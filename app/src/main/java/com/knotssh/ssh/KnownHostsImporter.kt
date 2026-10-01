package com.knotssh.ssh

import java.util.Base64

/** A host key read from an OpenSSH `known_hosts` / `ssh-keyscan` line. */
data class ParsedHostKey(
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBlob: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ParsedHostKey &&
            host == other.host && port == other.port && keyType == other.keyType &&
            keyBlob.contentEquals(other.keyBlob))

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + port
        result = 31 * result + keyType.hashCode()
        result = 31 * result + keyBlob.contentHashCode()
        return result
    }
}

data class KnownHostsImport(val keys: List<ParsedHostKey>, val rejectedLines: Int)

/**
 * Parses pasted `known_hosts` entries so a host can be trusted up front, which is the only way
 * to reach a new server under the strict host key policy.
 *
 * Only entries that an exact `host:port` lookup can later match are accepted: hashed patterns,
 * wildcards and `@cert-authority` / `@revoked` markers are rejected rather than stored as keys
 * that would never be used.
 */
object KnownHostsImporter {

    fun parse(text: String): KnownHostsImport {
        val keys = mutableListOf<ParsedHostKey>()
        var rejected = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parsed = parseLine(line)
            if (parsed.isEmpty()) rejected++ else keys += parsed
        }
        return KnownHostsImport(keys.distinct(), rejected)
    }

    private fun parseLine(line: String): List<ParsedHostKey> {
        val fields = line.split(WHITESPACE)
        if (fields.size < 3) return emptyList()
        val patterns = fields[0]
        if (patterns.startsWith("@") || patterns.startsWith("|")) return emptyList()

        val declaredType = fields[1]
        val blob = runCatching { Base64.getDecoder().decode(fields[2]) }.getOrNull()
            ?: return emptyList()
        // The type inside the blob is what JSch presents at connect time; a mismatch would store
        // an entry that can never be matched.
        if (HostKeyGate.parseKeyType(blob) != declaredType) return emptyList()

        return patterns.split(',').mapNotNull { pattern ->
            splitHostPort(pattern)?.let { (host, port) ->
                ParsedHostKey(host, port, declaredType, blob)
            }
        }
    }

    private fun splitHostPort(pattern: String): Pair<String, Int>? {
        if (pattern.isEmpty() || pattern.any { it == '*' || it == '?' || it == '!' }) return null
        if (!pattern.startsWith("[")) return pattern to DEFAULT_SSH_PORT
        val separator = pattern.indexOf("]:")
        if (separator <= 1) return null
        val port = pattern.substring(separator + 2).toIntOrNull()?.takeIf { it in 1..65535 }
            ?: return null
        return pattern.substring(1, separator) to port
    }

    private val WHITESPACE = Regex("\\s+")
    private const val DEFAULT_SSH_PORT = 22
}
