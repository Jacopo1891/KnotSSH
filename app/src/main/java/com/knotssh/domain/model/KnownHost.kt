package com.knotssh.domain.model

data class KnownHost(
    val id: Long = 0,
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBlob: String,
    val fingerprintSha256: String,
    val addedAtMs: Long
)

/** Outcome of comparing a presented host key against the local known-hosts store. */
sealed interface HostKeyVerdict {
    data object Trusted : HostKeyVerdict

    data class Unknown(val candidate: KnownHost) : HostKeyVerdict

    data class Changed(val stored: KnownHost, val candidate: KnownHost) : HostKeyVerdict
}
