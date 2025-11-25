package com.example.indoor_localisation_again.strategy

import com.example.indoor_localisation_again.model.Fingerprint
import com.example.indoor_localisation_again.model.AccessPointReading
import com.example.indoor_localisation_again.model.Position
import kotlin.math.*

// Global Constant for Noise Floor (Weakest possible signal)
const val NO_SIGNAL = -100.0

class EuclideanStrategy : PositioningStrategy {
    override fun calculatePosition(currentScan: List<AccessPointReading>, database: List<Fingerprint>): Position? {
        if (currentScan.isEmpty() || database.isEmpty()) return null

        val scanByBssid = currentScan.associateBy { it.bssid }
        var bestMatch: Fingerprint? = null
        var bestDistance = Double.MAX_VALUE

        for (fingerprint in database) {
            var totalDiffSquared = 0.0

            // Get a set of ALL unique BSSIDs involved in this comparison
            val allBssids = (scanByBssid.keys + fingerprint.readings.map { it.bssid }).toSet()

            for (bssid in allBssids) {
                val scanRssi = scanByBssid[bssid]?.rssi?.toDouble() ?: NO_SIGNAL
                val dbRssi = fingerprint.readings.find { it.bssid == bssid }?.rssi?.toDouble() ?: NO_SIGNAL

                val diff = scanRssi - dbRssi
                totalDiffSquared += diff * diff
            }

            val distance = sqrt(totalDiffSquared)

            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = fingerprint
            }
        }

        return if (bestMatch != null && bestMatch.xMeters != null && bestMatch.yMeters != null) {
            // Confidence calculation: normalize. Distance of 0 = 100% confidence. Distance of 100 = 0%.
            val confidence = max(0.0, 100.0 - bestDistance)
            Position(
                x = bestMatch.xMeters!!,
                y = bestMatch.yMeters!!,
                locationName = bestMatch.locationName,
                confidence = confidence
            )
        } else {
            null
        }
    }
}

class WKNNStrategy(private val k: Int = 3) : PositioningStrategy {
    override fun calculatePosition(currentScan: List<AccessPointReading>, database: List<Fingerprint>): Position? {
        if (currentScan.isEmpty() || database.isEmpty()) return null

        val scanByBssid = currentScan.associateBy { it.bssid }
        val fingerprintDistances = mutableListOf<Pair<Fingerprint, Double>>()

        for (fingerprint in database) {
            var totalDiffSquared = 0.0

            // Use Euclidean distance for neighbor selection (Standard for WKNN)
            // It punishes large outliers more than Manhattan
            val allBssids = (scanByBssid.keys + fingerprint.readings.map { it.bssid }).toSet()

            for (bssid in allBssids) {
                val scanRssi = scanByBssid[bssid]?.rssi?.toDouble() ?: NO_SIGNAL
                val dbRssi = fingerprint.readings.find { it.bssid == bssid }?.rssi?.toDouble() ?: NO_SIGNAL

                val diff = scanRssi - dbRssi
                totalDiffSquared += diff * diff
            }

            // CRITICAL FIX: Do NOT divide by matchedCount.
            // The accumulated error is the correct metric.
            val distance = sqrt(totalDiffSquared)
            fingerprintDistances.add(Pair(fingerprint, distance))
        }

        // Sort by distance (ASC) and take top k
        val topK = fingerprintDistances
            .sortedBy { it.second }
            .take(k)
            .filter { it.first.xMeters != null && it.first.yMeters != null }

        if (topK.isEmpty()) return null

        // Calculate weighted average
        var totalWeight = 0.0
        var weightedSumX = 0.0
        var weightedSumY = 0.0

        for ((fingerprint, distance) in topK) {
            // Weight = Inverse distance. Add small epsilon to avoid divide by zero.
            val weight = 1.0 / (distance + 0.1)

            totalWeight += weight
            weightedSumX += fingerprint.xMeters!! * weight
            weightedSumY += fingerprint.yMeters!! * weight
        }

        if (totalWeight == 0.0) return null

        return Position(
            x = weightedSumX / totalWeight,
            y = weightedSumY / totalWeight,
            locationName = topK.first().first.locationName, // Label of the closest match
            confidence = max(0.0, 100.0 - topK.first().second)
        )
    }
}

class CosineSimilarityStrategy : PositioningStrategy {
    override fun calculatePosition(currentScan: List<AccessPointReading>, database: List<Fingerprint>): Position? {
        if (currentScan.isEmpty() || database.isEmpty()) return null

        val scanByBssid = currentScan.associateBy { it.bssid }

        var bestMatch: Fingerprint? = null
        var bestSimilarity = -1.0 // Cosine range is -1 to 1

        for (fingerprint in database) {
            if (fingerprint.xMeters == null || fingerprint.yMeters == null) continue

            val fingerprintByBssid = fingerprint.readings.associateBy { it.bssid }
            val allBssids = (scanByBssid.keys + fingerprintByBssid.keys).toSet()

            var dotProduct = 0.0
            var normA = 0.0
            var normB = 0.0

            for (bssid in allBssids) {
                // CRITICAL FIX: Shift RSSI to Positive Range
                // -30 becomes 70. -90 becomes 10. Missing becomes 0.
                val rawScan = scanByBssid[bssid]?.rssi?.toDouble() ?: NO_SIGNAL
                val rawDb   = fingerprintByBssid[bssid]?.rssi?.toDouble() ?: NO_SIGNAL

                // Shift logic: Value = RSSI - NO_SIGNAL (e.g. -30 - (-100) = 70)
                // If it's NO_SIGNAL, it becomes 0.
                val valA = max(0.0, rawScan - NO_SIGNAL)
                val valB = max(0.0, rawDb - NO_SIGNAL)

                dotProduct += valA * valB
                normA += valA * valA
                normB += valB * valB
            }

            val similarity = if (normA > 0 && normB > 0) {
                dotProduct / (sqrt(normA) * sqrt(normB))
            } else 0.0

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = fingerprint
            }
        }

        return if (bestMatch != null && bestMatch.xMeters != null && bestMatch.yMeters != null) {
            Position(
                x = bestMatch.xMeters!!,
                y = bestMatch.yMeters!!,
                locationName = bestMatch.locationName,
                confidence = bestSimilarity * 100.0 // 0 to 1 scale -> 0 to 100%
            )
        } else {
            null
        }
    }
}