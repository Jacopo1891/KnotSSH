package com.knotssh.domain.repository

import com.knotssh.domain.model.QuickCommand
import kotlinx.coroutines.flow.Flow

interface QuickCommandRepository {
    fun getAllCommands(): Flow<List<QuickCommand>>
    suspend fun insertCommand(command: QuickCommand): Long
    suspend fun updateCommand(command: QuickCommand)
    suspend fun deleteCommand(id: Long)
}
