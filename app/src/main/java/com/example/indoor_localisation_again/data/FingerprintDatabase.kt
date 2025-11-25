package com.example.indoor_localisation_again.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FingerprintEntity::class], version = 2, exportSchema = false)
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE fingerprints ADD COLUMN xMeters REAL")
                database.execSQL("ALTER TABLE fingerprints ADD COLUMN yMeters REAL")
            }
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
