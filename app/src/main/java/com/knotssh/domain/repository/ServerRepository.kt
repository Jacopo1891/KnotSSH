package com.knotssh.domain.repository

import com.knotssh.domain.model.Server
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun getAllServers(): Flow<List<Server>>
    fun getRecentServers(limit: Int = 20): Flow<List<Server>>
    suspend fun getServerById(id: Long): Server?
    suspend fun insertServer(server: Server): Long
    suspend fun updateServer(server: Server)
    suspend fun deleteServer(id: Long)
    suspend fun updateLastConnected(id: Long, timestamp: Long)
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    suspend fun setFolder(id: Long, folderId: Long?)
    suspend fun applyOrder(serverIds: List<Long>)
}
