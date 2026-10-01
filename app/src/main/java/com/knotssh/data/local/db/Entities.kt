package com.knotssh.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.PortForwardRule
import com.knotssh.domain.model.SshKeyType

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val hostname: String,
    val port: Int = 22,
    val credentialId: Long,
    val lastConnectedMs: Long? = null,
    val isFavorite: Boolean = false,
    val keepAliveSeconds: Int = 30,
    val connectTimeoutSeconds: Int = 30,
    val portForwardRules: List<PortForwardRule> = emptyList(),
    /** `null` keeps the server in the "no folder" section. */
    val folderId: Long? = null,
    val sortOrder: Int = 0
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isExpanded: Boolean = true
)

@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val username: String,
    val authType: AuthType,
    val encryptedSecret: String = "",
    val encryptedPassphrase: String? = null,
    val publicKey: String? = null,
    val keyType: SshKeyType? = null,
    val askEachTime: Boolean = false
)

@Entity(tableName = "quick_commands")
data class QuickCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val command: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "custom_keys")
data class CustomKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val sequence: String,
    val sortOrder: Int = 0
)

/** Local known_hosts store backing trust-on-first-use host key verification. */@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port", "keyType"], unique = true)]
)
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val keyType: String,
    /** Base64 of the raw host public key blob. */
    val keyBlob: String,
    val fingerprintSha256: String,
    val addedAtMs: Long
)
