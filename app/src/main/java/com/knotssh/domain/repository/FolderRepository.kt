package com.knotssh.domain.repository

import com.knotssh.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAll(): Flow<List<Folder>>
    suspend fun getAllOnce(): List<Folder>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setExpanded(id: Long, expanded: Boolean)

    /** Deletes the folder; its servers fall back to the "no folder" section. */
    suspend fun delete(id: Long)

    suspend fun applyOrder(folderIds: List<Long>)
}
