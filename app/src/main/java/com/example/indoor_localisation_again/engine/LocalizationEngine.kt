package com.example.indoor_localisation_again.engine

import com.example.indoor_localisation_again.data.FingerprintRepository
import com.example.indoor_localisation_again.model.AccessPointReading
import com.example.indoor_localisation_again.model.Fingerprint
import com.example.indoor_localisation_again.model.Position
import com.example.indoor_localisation_again.strategy.EuclideanStrategy
import com.example.indoor_localisation_again.strategy.PositioningStrategy
import com.example.indoor_localisation_again.strategy.WKNNStrategy
import com.example.indoor_localisation_again.strategy.CosineSimilarityStrategy
import kotlinx.coroutines.flow.first

class LocalizationEngine(
    private val repository: FingerprintRepository
) {
    private var positioningStrategy: PositioningStrategy = EuclideanStrategy()

    data class Prediction(
        val fingerprintId: Long,
        val locationName: String,
        val score: Double,
        val matchedCount: Int
    )

    suspend fun saveFingerprint(
        locationName: String,
        readings: List<AccessPointReading>,
        xMeters: Double? = null,
        yMeters: Double? = null
    ) {
        if (locationName.isBlank() || readings.isEmpty()) return
        val fingerprint = Fingerprint(
            locationName = locationName.trim(),
            timestamp = System.currentTimeMillis(),
            readings = readings,
            xMeters = xMeters,
            yMeters = yMeters
        )
        repository.saveFingerprint(fingerprint)
    }

    suspend fun predict(readings: List<AccessPointReading>): Prediction? {
        val fingerprints = repository.fingerprints.first()
        if (fingerprints.isEmpty() || readings.isEmpty()) return null

        val currentByBssid = readings.associateBy { it.bssid }
        var best: Prediction? = null

        fingerprints.forEach { fingerprint ->
            var totalDiff = 0.0
            var matches = 0
            fingerprint.readings.forEach { stored ->
                val current = currentByBssid[stored.bssid]
                if (current != null) {
                    matches++
                    totalDiff += kotlin.math.abs(current.rssi - stored.rssi)
                } else {
                    totalDiff += MISSING_AP_PENALTY
                }
            }
            if (matches == 0) return@forEach
            val normalized = totalDiff / matches
            if (best == null || normalized < best!!.score) {
                best = Prediction(
                    fingerprintId = fingerprint.id,
                    locationName = fingerprint.locationName,
                    score = normalized,
                    matchedCount = matches
                )
            }
        }
        return best
    }

    suspend fun updatePosition(wifiReadings: List<AccessPointReading>): Position? {
        val database = repository.fingerprints.first()
        if (wifiReadings.isEmpty() || database.isEmpty()) return null
        return positioningStrategy.calculatePosition(wifiReadings, database)
    }

    fun setAlgorithm(algorithmName: String) {
        positioningStrategy = when (algorithmName.uppercase()) {
            "EUCLIDEAN" -> EuclideanStrategy()
            "WKNN" -> WKNNStrategy()  // Uses default k=3
            "COSINE" -> CosineSimilarityStrategy()
            else -> EuclideanStrategy()  // Default fallback
        }
    }

    companion object {
        private const val MISSING_AP_PENALTY = 18.0
    }
}
