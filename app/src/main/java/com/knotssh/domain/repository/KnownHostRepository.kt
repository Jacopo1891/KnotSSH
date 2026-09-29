package com.knotssh.domain.repository

import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.model.KnownHost
import kotlinx.coroutines.flow.Flow

interface KnownHostRepository {
    fun getAll(): Flow<List<KnownHost>>

    /** Compares a presented key against the store without mutating it. */
    suspend fun verify(host: String, port: Int, keyType: String, keyBlob: ByteArray): HostKeyVerdict

    suspend fun trust(host: String, port: Int, keyType: String, keyBlob: ByteArray)

    suspend fun forget(id: Long)

    suspend fun forgetHost(host: String, port: Int)

    suspend fun forgetAll()
}
