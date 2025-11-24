package com.example.indoor_localisation_again.model

data class Fingerprint(
    val id: Long = 0,
    val locationName: String,
    val timestamp: Long,
    val readings: List<AccessPointReading>
)
