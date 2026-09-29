package com.knotssh.data.repository

import android.util.Base64
import com.knotssh.data.local.db.KnownHostDao
import com.knotssh.data.local.db.KnownHostEntity
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.model.KnownHost
import com.knotssh.domain.repository.KnownHostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnownHostRepositoryImpl @Inject constructor(
    private val dao: KnownHostDao
) : KnownHostRepository {

    override fun getAll(): Flow<List<KnownHost>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun verify(
        host: String,
        port: Int,
        keyType: String,
        keyBlob: ByteArray
    ): HostKeyVerdict {
        val candidate = KnownHost(
            host = host,
            port = port,
            keyType = keyType,
            keyBlob = Base64.encodeToString(keyBlob, Base64.NO_WRAP),
            fingerprintSha256 = fingerprintOf(keyBlob),
            addedAtMs = System.currentTimeMillis()
        )
        val stored = dao.find(host, port, keyType)?.toDomain()
            ?: return HostKeyVerdict.Unknown(candidate)

        // Compare the raw key, not the fingerprint, to avoid relying on digest collision resistance alone.
        return if (constantTimeEquals(stored.keyBlob, candidate.keyBlob)) {
            HostKeyVerdict.Trusted
        } else {
            HostKeyVerdict.Changed(stored, candidate)
        }
    }

    override suspend fun trust(host: String, port: Int, keyType: String, keyBlob: ByteArray) {
        val existing = dao.find(host, port, keyType)
        dao.upsert(
            KnownHostEntity(
                id = existing?.id ?: 0,
                host = host,
                port = port,
                keyType = keyType,
                keyBlob = Base64.encodeToString(keyBlob, Base64.NO_WRAP),
                fingerprintSha256 = fingerprintOf(keyBlob),
                addedAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun forget(id: Long) = dao.deleteById(id)

    override suspend fun forgetHost(host: String, port: Int) = dao.deleteForHost(host, port)

    override suspend fun forgetAll() = dao.deleteAll()

    private fun KnownHostEntity.toDomain() = KnownHost(
        id = id,
        host = host,
        port = port,
        keyType = keyType,
        keyBlob = keyBlob,
        fingerprintSha256 = fingerprintSha256,
        addedAtMs = addedAtMs
    )

    companion object {
        /** OpenSSH-style `SHA256:<base64>` fingerprint. */
        fun fingerprintOf(keyBlob: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
            return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.US_ASCII), b.toByteArray(Charsets.US_ASCII))
    }
}
