package com.example.indoor_localisation_again.engine

import com.example.indoor_localisation_again.data.FingerprintRepository
import com.example.indoor_localisation_again.model.AccessPointReading
import com.example.indoor_localisation_again.model.Fingerprint
import kotlinx.coroutines.flow.first
import kotlin.math.abs

class LocalizationEngine(
    private val repository: FingerprintRepository
)
{
    data class Prediction(
        val locationName: String,
        val score: Double,
        val matchedCount: Int
    )

    suspend fun saveFingerprint(locationName: String, readings: List<AccessPointReading>) {
        if (locationName.isBlank() || readings.isEmpty()) return
        val fingerprint = Fingerprint(
            locationName = locationName.trim(),
            timestamp = System.currentTimeMillis(),
            readings = readings
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
                    totalDiff += abs(current.rssi - stored.rssi)
                } else {
                    totalDiff += MISSING_AP_PENALTY
                }
            }
            if (matches == 0) return@forEach
            val normalized = totalDiff / matches
            if (best == null || normalized < best!!.score) {
                best = Prediction(
                    locationName = fingerprint.locationName,
                    score = normalized,
                    matchedCount = matches
                )
            }
        }
        return best
    }

    companion object {
        private const val MISSING_AP_PENALTY = 18.0
    }
}
