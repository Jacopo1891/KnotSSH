package com.knotssh.ssh

import com.knotssh.data.local.preferences.HostKeyPolicy
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.repository.KnownHostRepository
import kotlinx.coroutines.runBlocking
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo

/**
 * Bridges JSch host key checking to the app's known-hosts store.
 *
 * [check] runs on the JSch connect thread *before* authentication, which is the only point at
 * which a man-in-the-middle can still be rejected without having leaked the credentials.
 */
class HostKeyGate(
    private val host: String,
    private val port: Int,
    private val policy: HostKeyPolicy,
    private val knownHosts: KnownHostRepository,
    private val onPrompt: suspend (HostKeyVerdict) -> Boolean
) : HostKeyRepository {

    /** Set when [check] refuses a key, so the caller can report a precise reason. */
    @Volatile
    var rejection: HostKeyRejection? = null
        private set

    override fun check(host: String, key: ByteArray): Int {
        val keyType = parseKeyType(key)
        if (policy == HostKeyPolicy.ACCEPT_ANY) return HostKeyRepository.OK

        val verdict = runBlocking { knownHosts.verify(this@HostKeyGate.host, port, keyType, key) }
        return when (verdict) {
            is HostKeyVerdict.Trusted -> HostKeyRepository.OK

            is HostKeyVerdict.Unknown -> when (policy) {
                HostKeyPolicy.TRUST_ON_FIRST_USE -> {
                    runBlocking { knownHosts.trust(this@HostKeyGate.host, port, keyType, key) }
                    HostKeyRepository.OK
                }
                HostKeyPolicy.PROMPT -> askUser(verdict, keyType, key)
                else -> {
                    rejection = HostKeyRejection.UnknownHost(verdict.candidate.fingerprintSha256)
                    HostKeyRepository.NOT_INCLUDED
                }
            }

            is HostKeyVerdict.Changed -> when (policy) {
                HostKeyPolicy.PROMPT -> askUser(verdict, keyType, key)
                else -> {
                    rejection = HostKeyRejection.KeyChanged(
                        expected = verdict.stored.fingerprintSha256,
                        actual = verdict.candidate.fingerprintSha256
                    )
                    HostKeyRepository.CHANGED
                }
            }
        }
    }

    private fun askUser(verdict: HostKeyVerdict, keyType: String, key: ByteArray): Int {
        val accepted = runBlocking { onPrompt(verdict) }
        if (accepted) {
            runBlocking { knownHosts.trust(host, port, keyType, key) }
            return HostKeyRepository.OK
        }
        rejection = HostKeyRejection.UserDeclined
        return if (verdict is HostKeyVerdict.Changed) HostKeyRepository.CHANGED
        else HostKeyRepository.NOT_INCLUDED
    }

    // The app owns persistence, so JSch's own mutation hooks are intentionally inert.
    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "knotssh-known-hosts"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    companion object {
        /** SSH public key blobs are `uint32 len | type | ...`. */
        fun parseKeyType(blob: ByteArray): String {
            if (blob.size < 4) return "unknown"
            val length = ((blob[0].toInt() and 0xFF) shl 24) or
                    ((blob[1].toInt() and 0xFF) shl 16) or
                    ((blob[2].toInt() and 0xFF) shl 8) or
                    (blob[3].toInt() and 0xFF)
            if (length !in 1..64 || blob.size < 4 + length) return "unknown"
            return String(blob, 4, length, Charsets.US_ASCII)
        }
    }
}

sealed interface HostKeyRejection {
    data class UnknownHost(val fingerprint: String) : HostKeyRejection
    data class KeyChanged(val expected: String, val actual: String) : HostKeyRejection
    data object UserDeclined : HostKeyRejection
}
