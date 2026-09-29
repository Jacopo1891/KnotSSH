package com.knotssh.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val id: Long = 0,
    val alias: String,
    val username: String,
    val authType: AuthType,
    // Stored as base64-encoded encrypted bytes in DB
    val encryptedSecret: String = "",
    // Optional passphrase protecting an encrypted private key, also encrypted at rest
    val encryptedPassphrase: String? = null,
    // For SSH keys: the public key portion (not encrypted)
    val publicKey: String? = null,
    // Key type for SSH key auth
    val keyType: SshKeyType? = null
)

@Serializable
enum class AuthType {
    PASSWORD,
    SSH_KEY
}

@Serializable
enum class SshKeyType {
    RSA,
    ECDSA,
    ED25519
}
