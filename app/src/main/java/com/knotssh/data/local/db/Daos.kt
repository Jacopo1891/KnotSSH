package com.knotssh.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY isFavorite DESC, CASE WHEN lastConnectedMs IS NULL THEN 1 ELSE 0 END, lastConnectedMs DESC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY isFavorite DESC, CASE WHEN lastConnectedMs IS NULL THEN 1 ELSE 0 END, lastConnectedMs DESC LIMIT :limit")
    fun getRecentServers(limit: Int = 20): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: Long): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServer(id: Long)

    @Query("UPDATE servers SET lastConnectedMs = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)

    @Query("UPDATE servers SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE servers SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE servers SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachFromFolder(folderId: Long)

    @Query("UPDATE servers SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM servers " +
            "WHERE alias = :alias COLLATE NOCASE AND id != :excludingId)"
    )
    suspend fun aliasExists(alias: String, excludingId: Long): Boolean
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET isExpanded = :expanded WHERE id = :id")
    suspend fun setExpanded(id: Long, expanded: Boolean)

    @Query("UPDATE folders SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT MAX(sortOrder) FROM folders")
    suspend fun maxSortOrder(): Int?
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY alias ASC")
    fun getAllCredentials(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getCredentialById(id: Long): CredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: CredentialEntity): Long

    @Update
    suspend fun updateCredential(credential: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteCredential(id: Long)
}

@Dao
interface QuickCommandDao {
    @Query("SELECT * FROM quick_commands ORDER BY sortOrder ASC, id ASC")
    fun getAllCommands(): Flow<List<QuickCommandEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: QuickCommandEntity): Long

    @Update
    suspend fun updateCommand(command: QuickCommandEntity)

    @Query("DELETE FROM quick_commands WHERE id = :id")
    suspend fun deleteCommand(id: Long)
}

@Dao
interface KnownHostDao {    @Query("SELECT * FROM known_hosts ORDER BY host ASC, port ASC")
    fun getAll(): Flow<List<KnownHostEntity>>

    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port AND keyType = :keyType LIMIT 1")
    suspend fun find(host: String, port: Int, keyType: String): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KnownHostEntity): Long

    @Query("DELETE FROM known_hosts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun deleteForHost(host: String, port: Int)

    @Query("DELETE FROM known_hosts")
    suspend fun deleteAll()
}

@Dao
interface CustomKeyDao {
    @Query("SELECT * FROM custom_keys ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<CustomKeyEntity>>

    @Query("SELECT COUNT(*) FROM custom_keys")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: CustomKeyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(keys: List<CustomKeyEntity>)

    @Query("DELETE FROM custom_keys WHERE id = :id")
    suspend fun deleteById(id: Long)
}
