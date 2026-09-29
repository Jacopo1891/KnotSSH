package com.knotssh.domain.repository

import com.knotssh.domain.model.CustomKey
import kotlinx.coroutines.flow.Flow

interface CustomKeyRepository {
    fun getAll(): Flow<List<CustomKey>>
    suspend fun save(key: CustomKey): Long
    suspend fun delete(id: Long)

    /** Populates the defaults the first time the bar is used. */
    suspend fun seedDefaultsIfEmpty()

    suspend fun restoreDefaults()
}
