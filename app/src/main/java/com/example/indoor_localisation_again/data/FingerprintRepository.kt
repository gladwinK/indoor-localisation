package com.example.indoor_localisation_again.data

import android.content.Context
import com.example.indoor_localisation_again.model.Fingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FingerprintRepository(
    private val dao: FingerprintDao
) {
    val fingerprints: Flow<List<Fingerprint>> =
        dao.getAll().map { list -> list.map { it.toModel() } }

    suspend fun saveFingerprint(fingerprint: Fingerprint) {
        dao.insert(fingerprint.toEntity())
    }

    suspend fun clear() {
        dao.clear()
    }

    companion object {
        fun create(context: Context): FingerprintRepository =
            FingerprintRepository(FingerprintDatabase.getInstance(context).fingerprintDao())
    }
}
