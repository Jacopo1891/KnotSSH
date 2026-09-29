package com.knotssh.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Server(
    val id: Long = 0,
    val alias: String,
    val hostname: String,
    val port: Int = 22,
    val credentialId: Long,
    val lastConnectedMs: Long? = null,
    val isFavorite: Boolean = false,
    // Short enough to keep NAT mappings on mobile networks alive.
    val keepAliveSeconds: Int = 30,
    val connectTimeoutSeconds: Int = 30,
    val portForwardRules: List<PortForwardRule> = emptyList()
)

@Serializable
data class PortForwardRule(
    val id: Long = 0,
    val type: ForwardType,
    val localPort: Int,
    val remoteHost: String = "",
    val remotePort: Int = 0
)

@Serializable
enum class ForwardType {
    LOCAL,    // -L: local port → remote host:port
    REMOTE,   // -R: remote port → local host:port
    DYNAMIC   // -D: SOCKS5 proxy on local port
}
