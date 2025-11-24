package com.example.indoor_localisation_again.model

data class AccessPointReading(
    val bssid: String,
    val ssid: String?,
    val rssi: Int,
    val frequency: Int,
    val ageMs: Long
)
