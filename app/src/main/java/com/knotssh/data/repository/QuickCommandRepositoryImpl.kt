package com.knotssh.data.repository

import com.knotssh.data.local.db.QuickCommandDao
import com.knotssh.data.local.db.QuickCommandEntity
import com.knotssh.domain.model.QuickCommand
import com.knotssh.domain.repository.QuickCommandRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickCommandRepositoryImpl @Inject constructor(
    private val dao: QuickCommandDao
) : QuickCommandRepository {

    override fun getAllCommands(): Flow<List<QuickCommand>> =
        dao.getAllCommands().map { list -> list.map { it.toDomain() } }

    override suspend fun insertCommand(command: QuickCommand): Long =
        dao.insertCommand(command.toEntity())

    override suspend fun updateCommand(command: QuickCommand) =
        dao.updateCommand(command.toEntity())

    override suspend fun deleteCommand(id: Long) = dao.deleteCommand(id)

    private fun QuickCommandEntity.toDomain() =
        QuickCommand(id = id, label = label, command = command, sortOrder = sortOrder)

    private fun QuickCommand.toEntity() =
        QuickCommandEntity(id = id, label = label, command = command, sortOrder = sortOrder)
}
