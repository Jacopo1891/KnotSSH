package com.knotssh.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the container itself. The encrypted path needs Argon2's native library and therefore
 * belongs to an instrumented test; everything that can be checked on the JVM is checked here.
 */
class BackupCodecTest {

    private fun payload() = BackupPayload(
        exportedAtMs = 1_700_000_000_000,
        appVersion = "1.0.0",
        credentials = listOf(
            BackupCredential(
                alias = "prod",
                username = "root",
                authType = "PASSWORD",
                secret = "hunter2",
                askEachTime = false
            )
        ),
        servers = listOf(
            BackupServer(
                alias = "web",
                hostname = "example.com",
                port = 2222,
                credentialAlias = "prod",
                portForwardRules = listOf(BackupPortForward("LOCAL", 8080, "127.0.0.1", 80))
            )
        ),
        knownHosts = listOf(BackupKnownHost("example.com", 2222, "ssh-ed25519", "QUJD"))
    )

    @Test
    fun `round trips without a password`() {
        val original = payload()
        val decoded = BackupCodec.decode(BackupCodec.encode(original, null), null)

        assertEquals(original.appVersion, decoded.appVersion)
        assertEquals(original.credentials, decoded.credentials)
        assertEquals(original.servers, decoded.servers)
        assertEquals(original.knownHosts, decoded.knownHosts)
    }

    @Test
    fun `an empty password is treated as no password`() {
        val bytes = BackupCodec.encode(payload(), CharArray(0))
        assertFalse(BackupCodec.inspect(bytes).encrypted)
    }

    @Test
    fun `rejects a file that is not a backup`() {
        assertThrows(BackupException.NotABackup::class.java) {
            BackupCodec.inspect(ByteArray(128) { it.toByte() })
        }
    }

    @Test
    fun `rejects a file shorter than the header`() {
        assertThrows(BackupException.NotABackup::class.java) {
            BackupCodec.inspect("KNOTSSH1".toByteArray())
        }
    }

    @Test
    fun `rejects a newer format version`() {
        val bytes = BackupCodec.encode(payload(), null)
        bytes[8] = 99

        val thrown = assertThrows(BackupException.UnsupportedVersion::class.java) {
            BackupCodec.inspect(bytes)
        }
        assertEquals(99, thrown.version)
    }

    @Test
    fun `rejects a truncated body`() {
        val bytes = BackupCodec.encode(payload(), null)
        assertThrows(BackupException.Corrupted::class.java) {
            BackupCodec.decode(bytes.copyOfRange(0, bytes.size - 20), null)
        }
    }
}
