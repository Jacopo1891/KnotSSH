package com.knotssh.data.repository

import com.knotssh.data.local.db.CustomKeyDao
import com.knotssh.data.local.db.CustomKeyEntity
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.repository.CustomKeyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomKeyRepositoryImpl @Inject constructor(
    private val dao: CustomKeyDao
) : CustomKeyRepository {

    override fun getAll(): Flow<List<CustomKey>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(key: CustomKey): Long = dao.upsert(key.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) dao.upsertAll(CustomKey.DEFAULTS.map { it.toEntity() })
    }

    override suspend fun restoreDefaults() {
        dao.upsertAll(CustomKey.DEFAULTS.map { it.toEntity() })
    }

    private fun CustomKeyEntity.toDomain() =
        CustomKey(id = id, label = label, sequence = sequence, sortOrder = sortOrder)

    private fun CustomKey.toEntity() =
        CustomKeyEntity(id = id, label = label, sequence = sequence, sortOrder = sortOrder)
}
