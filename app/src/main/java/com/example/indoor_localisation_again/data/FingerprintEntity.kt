package com.example.indoor_localisation_again.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.indoor_localisation_again.model.Fingerprint

@Entity(tableName = "fingerprints")
data class FingerprintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationName: String,
    val timestamp: Long,
    val readingsJson: String
)

fun FingerprintEntity.toModel(): Fingerprint =
    Fingerprint(
        id = id,
        locationName = locationName,
        timestamp = timestamp,
        readings = AccessPointConverters.fromJson(readingsJson)
    )

fun Fingerprint.toEntity(): FingerprintEntity =
    FingerprintEntity(
        id = id,
        locationName = locationName,
        timestamp = timestamp,
        readingsJson = AccessPointConverters.toJson(readings)
    )
