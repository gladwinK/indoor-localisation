package com.example.indoor_localisation_again.model

data class Position(
    val x: Double,
    val y: Double,
    val locationName: String,
    val confidence: Double
)