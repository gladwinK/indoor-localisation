package com.example.indoor_localisation_again.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FingerprintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FingerprintEntity): Long

    @Query("SELECT * FROM fingerprints ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FingerprintEntity>>

    @Query("SELECT * FROM fingerprints WHERE id = :id")
    suspend fun getById(id: Long): FingerprintEntity?

    @Query("DELETE FROM fingerprints WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM fingerprints")
    suspend fun clear()
}
