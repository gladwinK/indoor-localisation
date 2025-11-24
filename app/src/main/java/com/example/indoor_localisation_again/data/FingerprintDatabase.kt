package com.example.indoor_localisation_again.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FingerprintEntity::class], version = 1, exportSchema = false)
@TypeConverters(RoomAccessPointConverters::class)
abstract class FingerprintDatabase : RoomDatabase() {
    abstract fun fingerprintDao(): FingerprintDao

    companion object {
        @Volatile
        private var instance: FingerprintDatabase? = null

        fun getInstance(context: Context): FingerprintDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FingerprintDatabase::class.java,
                    "fingerprints.db"
                ).build().also { instance = it }
            }
    }
}

class RoomAccessPointConverters {
    @androidx.room.TypeConverter
    fun toJson(readings: List<com.example.indoor_localisation_again.model.AccessPointReading>): String =
        AccessPointConverters.toJson(readings)

    @androidx.room.TypeConverter
    fun fromJson(raw: String): List<com.example.indoor_localisation_again.model.AccessPointReading> =
        AccessPointConverters.fromJson(raw)
}
