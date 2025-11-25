package com.example.indoor_localisation_again.strategy

import com.example.indoor_localisation_again.model.Fingerprint
import com.example.indoor_localisation_again.model.AccessPointReading
import com.example.indoor_localisation_again.model.Position

interface PositioningStrategy {
    fun calculatePosition(currentScan: List<AccessPointReading>, database: List<Fingerprint>): Position?
}