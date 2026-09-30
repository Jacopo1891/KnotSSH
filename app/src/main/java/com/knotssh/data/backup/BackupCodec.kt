package com.knotssh.data.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Raised for every unusable file, so the UI can tell the user what to do about it. */
sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("Not a KnotSSH backup file")
    class UnsupportedVersion(val version: Int) : BackupException("Unsupported format version $version")
    class PasswordRequired : BackupException("The file is encrypted")
    class WrongPassword : BackupException("Wrong password or corrupted file")
    class Corrupted(message: String) : BackupException(message)
}

/** What can be learned from a file before knowing the password. */
data class BackupFileInfo(val encrypted: Boolean)

/**
 * Reads and writes the `.knotssh` container.
 *
 * Layout, all integers big-endian:
 * ```
 *  0   8  magic "KNOTSSH1"
 *  8   1  format version
 *  9   1  KDF id (0 = none, 1 = Argon2id)
 * 10   4  Argon2 memory cost, KiB
 * 14   4  Argon2 iterations
 * 18   1  Argon2 parallelism
 * 19  16  salt
 * 35  12  GCM nonce
 * 47   .  GZIP(JSON), AES-256-GCM encrypted when a KDF is in use
 * ```
 * The whole 47-byte header is fed to GCM as associated data: tampering with the KDF cost to make
 * the password cheap to brute-force invalidates the tag instead.
 */
object BackupCodec {

    private const val MAGIC = "KNOTSSH1"
    private const val FORMAT_VERSION = 1
    private const val KDF_NONE = 0
    private const val KDF_ARGON2ID = 1

    private const val HEADER_SIZE = 47
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_SIZE = 32
    private const val GCM_TAG_BITS = 128

    /** OWASP's low-memory Argon2id profile: affordable on a phone, hostile to GPU cracking. */
    private const val MEMORY_KIB = 47_104
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val random = SecureRandom()

    fun inspect(bytes: ByteArray): BackupFileInfo {
        if (bytes.size < HEADER_SIZE) throw BackupException.NotABackup()
        val magic = String(bytes, 0, MAGIC.length, Charsets.US_ASCII)
        if (magic != MAGIC) throw BackupException.NotABackup()
        val version = bytes[8].toInt() and 0xFF
        if (version != FORMAT_VERSION) throw BackupException.UnsupportedVersion(version)
        return BackupFileInfo(encrypted = (bytes[9].toInt() and 0xFF) != KDF_NONE)
    }

    fun encode(payload: BackupPayload, password: CharArray?): ByteArray {
        val body = gzip(json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8))
        val encrypt = password != null && password.isNotEmpty()

        val salt = ByteArray(SALT_SIZE).also { if (encrypt) random.nextBytes(it) }
        val nonce = ByteArray(NONCE_SIZE).also { if (encrypt) random.nextBytes(it) }
        val header = buildHeader(
            kdf = if (encrypt) KDF_ARGON2ID else KDF_NONE,
            memoryKib = if (encrypt) MEMORY_KIB else 0,
            iterations = if (encrypt) ITERATIONS else 0,
            parallelism = if (encrypt) PARALLELISM else 0,
            salt = salt,
            nonce = nonce
        )

        if (!encrypt) return header + body

        val key = deriveKey(password!!, salt, MEMORY_KIB, ITERATIONS, PARALLELISM)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(body)
        } finally {
            key.fill(0)
            body.fill(0)
        }
    }

    fun decode(bytes: ByteArray, password: CharArray?): BackupPayload {
        val info = inspect(bytes)
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val body = bytes.copyOfRange(HEADER_SIZE, bytes.size)

        val plain = if (!info.encrypted) {
            body
        } else {
            if (password == null || password.isEmpty()) throw BackupException.PasswordRequired()
            val buffer = ByteBuffer.wrap(header)
            val memoryKib = buffer.getInt(10)
            val iterations = buffer.getInt(14)
            val parallelism = header[18].toInt() and 0xFF
            val salt = bytes.copyOfRange(19, 19 + SALT_SIZE)
            val nonce = bytes.copyOfRange(35, 35 + NONCE_SIZE)

            val key = deriveKey(password, salt, memoryKib, iterations, parallelism)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
                cipher.updateAAD(header)
                cipher.doFinal(body)
            } catch (e: AEADBadTagException) {
                // GCM cannot tell a wrong key from a tampered file, and neither can we.
                throw BackupException.WrongPassword()
            } finally {
                key.fill(0)
            }
        }

        return try {
            json.decodeFromString(BackupPayload.serializer(), String(gunzip(plain), Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException.Corrupted(e.message ?: "Malformed payload")
        } finally {
            plain.fill(0)
        }
    }

    private fun buildHeader(
        kdf: Int,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        salt: ByteArray,
        nonce: ByteArray
    ): ByteArray = ByteBuffer.allocate(HEADER_SIZE).apply {
        put(MAGIC.toByteArray(Charsets.US_ASCII))
        put(FORMAT_VERSION.toByte())
        put(kdf.toByte())
        putInt(memoryKib)
        putInt(iterations)
        put(parallelism.toByte())
        put(salt)
        put(nonce)
    }.array()

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {
        if (memoryKib <= 0 || iterations <= 0 || parallelism <= 0) {
            throw BackupException.Corrupted("Invalid KDF parameters")
        }
        val passwordBytes = password.joinToString("").toByteArray(Charsets.UTF_8)
        return try {
            Argon2Kt().hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = salt,
                tCostInIterations = iterations,
                mCostInKibibyte = memoryKib,
                parallelism = parallelism,
                hashLengthInBytes = KEY_SIZE
            ).rawHashAsByteArray()
        } finally {
            passwordBytes.fill(0)
        }
    }

    /** Compressing before encrypting is the only order that helps: ciphertext does not compress. */
    private fun gzip(data: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(data) }
    }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
