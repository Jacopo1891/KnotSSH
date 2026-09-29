package com.knotssh.data.repository

import com.knotssh.data.local.db.CredentialDao
import com.knotssh.data.local.db.CredentialEntity
import com.knotssh.data.local.security.CryptoManager
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.domain.repository.CredentialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val dao: CredentialDao,
    private val crypto: CryptoManager
) : CredentialRepository {

    override fun getAllCredentials(): Flow<List<Credential>> =
        dao.getAllCredentials().map { list -> list.map { it.toDomain() } }

    override suspend fun getCredentialById(id: Long): Credential? =
        dao.getCredentialById(id)?.toDomain()

    override suspend fun deleteCredential(id: Long) = dao.deleteCredential(id)

    override suspend fun save(
        credential: Credential,
        plainSecret: String,
        plainPassphrase: String?
    ): Long = withContext(Dispatchers.IO) {
        // A prompt-every-time credential must leave no secret behind, whatever the caller passed.
        val storeSecret = !(credential.askEachTime && credential.authType == AuthType.PASSWORD)
        val storePassphrase = !credential.askEachTime
        val encrypted = credential.copy(
            encryptedSecret = if (storeSecret) crypto.encrypt(plainSecret) else "",
            encryptedPassphrase = plainPassphrase
                ?.takeIf { storePassphrase && it.isNotEmpty() }
                ?.let { crypto.encrypt(it) }
        )
        if (credential.id > 0) {
            dao.updateCredential(encrypted.toEntity())
            credential.id
        } else {
            dao.insertCredential(encrypted.toEntity())
        }
    }

    override suspend fun decryptSecret(credential: Credential): String =
        withContext(Dispatchers.IO) {
            if (credential.encryptedSecret.isEmpty()) "" else crypto.decrypt(credential.encryptedSecret)
        }

    override suspend fun decryptPassphrase(credential: Credential): String? =
        withContext(Dispatchers.IO) {
            credential.encryptedPassphrase?.takeIf { it.isNotEmpty() }?.let { crypto.decrypt(it) }
        }

    private fun CredentialEntity.toDomain() = Credential(
        id = id, alias = alias, username = username, authType = authType,
        encryptedSecret = encryptedSecret, encryptedPassphrase = encryptedPassphrase,
        publicKey = publicKey, keyType = keyType, askEachTime = askEachTime
    )

    private fun Credential.toEntity() = CredentialEntity(
        id = id, alias = alias, username = username, authType = authType,
        encryptedSecret = encryptedSecret, encryptedPassphrase = encryptedPassphrase,
        publicKey = publicKey, keyType = keyType, askEachTime = askEachTime
    )
}
