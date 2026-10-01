package com.knotssh.data.repository

import com.knotssh.data.local.db.ServerDao
import com.knotssh.data.local.db.ServerEntity
import com.knotssh.domain.model.Server
import com.knotssh.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val dao: ServerDao
) : ServerRepository {

    override fun getAllServers(): Flow<List<Server>> =
        dao.getAllServers().map { list -> list.map { it.toDomain() } }

    override fun getRecentServers(limit: Int): Flow<List<Server>> =
        dao.getRecentServers(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getServerById(id: Long): Server? =
        dao.getServerById(id)?.toDomain()

    override suspend fun insertServer(server: Server): Long =
        dao.insertServer(server.toEntity())

    override suspend fun updateServer(server: Server) =
        dao.updateServer(server.toEntity())

    override suspend fun deleteServer(id: Long) = dao.deleteServer(id)

    override suspend fun updateLastConnected(id: Long, timestamp: Long) =
        dao.updateLastConnected(id, timestamp)

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) =
        dao.toggleFavorite(id, isFavorite)

    override suspend fun setFolder(id: Long, folderId: Long?) = dao.setFolder(id, folderId)

    override suspend fun applyOrder(serverIds: List<Long>) {
        serverIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }

    private fun ServerEntity.toDomain() = Server(
        id = id, alias = alias, hostname = hostname, port = port,
        credentialId = credentialId, lastConnectedMs = lastConnectedMs,
        isFavorite = isFavorite, keepAliveSeconds = keepAliveSeconds,
        connectTimeoutSeconds = connectTimeoutSeconds,
        portForwardRules = portForwardRules,
        folderId = folderId, sortOrder = sortOrder
    )

    private fun Server.toEntity() = ServerEntity(
        id = id, alias = alias, hostname = hostname, port = port,
        credentialId = credentialId, lastConnectedMs = lastConnectedMs,
        isFavorite = isFavorite, keepAliveSeconds = keepAliveSeconds,
        connectTimeoutSeconds = connectTimeoutSeconds,
        portForwardRules = portForwardRules,
        folderId = folderId, sortOrder = sortOrder
    )
}
