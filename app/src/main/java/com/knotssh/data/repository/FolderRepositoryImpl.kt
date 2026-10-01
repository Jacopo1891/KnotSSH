package com.knotssh.data.repository

import com.knotssh.data.local.db.FolderDao
import com.knotssh.data.local.db.FolderEntity
import com.knotssh.data.local.db.ServerDao
import com.knotssh.domain.model.Folder
import com.knotssh.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val dao: FolderDao,
    private val serverDao: ServerDao
) : FolderRepository {

    override fun getAll(): Flow<List<Folder>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllOnce(): List<Folder> = dao.getAllOnce().map { it.toDomain() }

    override suspend fun create(name: String): Long =
        dao.insert(FolderEntity(name = name.trim(), sortOrder = (dao.maxSortOrder() ?: -1) + 1))

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    override suspend fun setExpanded(id: Long, expanded: Boolean) = dao.setExpanded(id, expanded)

    override suspend fun delete(id: Long) {
        // Detached first: a server pointing at a missing folder would vanish from every section.
        serverDao.detachFromFolder(id)
        dao.delete(id)
    }

    override suspend fun applyOrder(folderIds: List<Long>) {
        folderIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }

    private fun FolderEntity.toDomain() = Folder(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isExpanded = isExpanded
    )
}
