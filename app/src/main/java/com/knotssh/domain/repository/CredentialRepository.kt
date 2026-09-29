package com.knotssh.domain.repository

import com.knotssh.domain.model.Credential
import kotlinx.coroutines.flow.Flow

interface CredentialRepository {
    fun getAllCredentials(): Flow<List<Credential>>
    suspend fun getCredentialById(id: Long): Credential?
    suspend fun deleteCredential(id: Long)

    /** Encrypts the supplied plaintext and inserts or updates the row. Returns the row id. */
    suspend fun save(credential: Credential, plainSecret: String, plainPassphrase: String?): Long

    /** Decrypts and returns the plaintext secret (password or private key PEM). */
    suspend fun decryptSecret(credential: Credential): String

    /** Decrypts the private key passphrase, or null when the key is not passphrase-protected. */
    suspend fun decryptPassphrase(credential: Credential): String?
}
