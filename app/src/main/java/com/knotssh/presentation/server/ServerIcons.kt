package com.knotssh.presentation.server

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.knotssh.R

enum class ServerIconGroup(@StringRes val label: Int) {
    GENERIC(R.string.server_icon_group_generic),
    LINUX(R.string.server_icon_group_linux),
    SYSTEMS(R.string.server_icon_group_systems),
    SELF_HOSTED(R.string.server_icon_group_selfhosted),
    SERVICES(R.string.server_icon_group_services),
    CLOUD(R.string.server_icon_group_cloud)
}

/**
 * One entry of the icon picker. [key] is what gets stored, so it must never change once shipped.
 * Brand logos come from Simple Icons (CC0); they only identify the software running on a server.
 */
class ServerIcon private constructor(
    val key: String,
    val group: ServerIconGroup,
    val name: String? = null,
    @StringRes val nameRes: Int = 0,
    @DrawableRes val drawable: Int = 0,
    val vector: ImageVector? = null,
    /** Wordmark logos are much wider than tall, so they are drawn larger to stay legible. */
    val wide: Boolean = false
) {
    companion object {
        private fun brand(
            key: String,
            group: ServerIconGroup,
            name: String,
            @DrawableRes res: Int,
            wide: Boolean = false
        ) = ServerIcon(key, group, name = name, drawable = res, wide = wide)

        private fun generic(key: String, @StringRes name: Int, vector: ImageVector) =
            ServerIcon(key, ServerIconGroup.GENERIC, nameRes = name, vector = vector)

        /** What a server without a chosen icon shows. */
        val DEFAULT = generic("server", R.string.server_icon_server, Icons.Default.Dns)

        val ALL: List<ServerIcon> = listOf(
            DEFAULT,
            generic("storage", R.string.server_icon_storage, Icons.Default.Storage),
            generic("computer", R.string.server_icon_computer, Icons.Default.Computer),
            generic("router", R.string.server_icon_router, Icons.Default.Router),
            generic("cloud", R.string.server_icon_cloud_generic, Icons.Default.Cloud),
            generic("terminal", R.string.server_icon_terminal, Icons.Default.Terminal),
            generic("vm", R.string.server_icon_vm, Icons.Default.ViewInAr),

            brand("ubuntu", ServerIconGroup.LINUX, "Ubuntu", R.drawable.ic_srv_ubuntu),
            brand("debian", ServerIconGroup.LINUX, "Debian", R.drawable.ic_srv_debian),
            brand("fedora", ServerIconGroup.LINUX, "Fedora", R.drawable.ic_srv_fedora),
            brand("archlinux", ServerIconGroup.LINUX, "Arch Linux", R.drawable.ic_srv_archlinux),
            brand("centos", ServerIconGroup.LINUX, "CentOS", R.drawable.ic_srv_centos),
            brand("redhat", ServerIconGroup.LINUX, "Red Hat", R.drawable.ic_srv_redhat),
            brand("opensuse", ServerIconGroup.LINUX, "openSUSE", R.drawable.ic_srv_opensuse),
            brand("linuxmint", ServerIconGroup.LINUX, "Linux Mint", R.drawable.ic_srv_linuxmint),
            brand("alpinelinux", ServerIconGroup.LINUX, "Alpine Linux", R.drawable.ic_srv_alpinelinux),
            brand("rockylinux", ServerIconGroup.LINUX, "Rocky Linux", R.drawable.ic_srv_rockylinux),
            brand("almalinux", ServerIconGroup.LINUX, "AlmaLinux", R.drawable.ic_srv_almalinux),
            brand("kalilinux", ServerIconGroup.LINUX, "Kali Linux", R.drawable.ic_srv_kalilinux),
            brand("nixos", ServerIconGroup.LINUX, "NixOS", R.drawable.ic_srv_nixos),
            brand("linux", ServerIconGroup.LINUX, "Linux", R.drawable.ic_srv_linux),

            brand("raspberrypi", ServerIconGroup.SYSTEMS, "Raspberry Pi", R.drawable.ic_srv_raspberrypi),
            brand("freebsd", ServerIconGroup.SYSTEMS, "FreeBSD", R.drawable.ic_srv_freebsd),
            brand("openbsd", ServerIconGroup.SYSTEMS, "OpenBSD", R.drawable.ic_srv_openbsd),
            brand("apple", ServerIconGroup.SYSTEMS, "Apple", R.drawable.ic_srv_apple),
            brand("android", ServerIconGroup.SYSTEMS, "Android", R.drawable.ic_srv_android),

            brand("docker", ServerIconGroup.SELF_HOSTED, "Docker", R.drawable.ic_srv_docker),
            brand("kubernetes", ServerIconGroup.SELF_HOSTED, "Kubernetes", R.drawable.ic_srv_kubernetes),
            brand("proxmox", ServerIconGroup.SELF_HOSTED, "Proxmox", R.drawable.ic_srv_proxmox),
            brand("synology", ServerIconGroup.SELF_HOSTED, "Synology", R.drawable.ic_srv_synology, wide = true),
            brand("truenas", ServerIconGroup.SELF_HOSTED, "TrueNAS", R.drawable.ic_srv_truenas),
            brand("unraid", ServerIconGroup.SELF_HOSTED, "Unraid", R.drawable.ic_srv_unraid, wide = true),
            brand("homeassistant", ServerIconGroup.SELF_HOSTED, "Home Assistant", R.drawable.ic_srv_homeassistant),
            brand("nextcloud", ServerIconGroup.SELF_HOSTED, "Nextcloud", R.drawable.ic_srv_nextcloud),
            brand("fireflyiii", ServerIconGroup.SELF_HOSTED, "Firefly III", R.drawable.ic_srv_fireflyiii),
            brand("portainer", ServerIconGroup.SELF_HOSTED, "Portainer", R.drawable.ic_srv_portainer),
            brand("pihole", ServerIconGroup.SELF_HOSTED, "Pi-hole", R.drawable.ic_srv_pihole),
            brand("openwrt", ServerIconGroup.SELF_HOSTED, "OpenWrt", R.drawable.ic_srv_openwrt),
            brand("wireguard", ServerIconGroup.SELF_HOSTED, "WireGuard", R.drawable.ic_srv_wireguard),
            brand("plex", ServerIconGroup.SELF_HOSTED, "Plex", R.drawable.ic_srv_plex),
            brand("grafana", ServerIconGroup.SELF_HOSTED, "Grafana", R.drawable.ic_srv_grafana),

            brand("nginx", ServerIconGroup.SERVICES, "NGINX", R.drawable.ic_srv_nginx),
            brand("postgresql", ServerIconGroup.SERVICES, "PostgreSQL", R.drawable.ic_srv_postgresql),
            brand("mysql", ServerIconGroup.SERVICES, "MySQL", R.drawable.ic_srv_mysql, wide = true),
            brand("mariadb", ServerIconGroup.SERVICES, "MariaDB", R.drawable.ic_srv_mariadb),
            brand("mongodb", ServerIconGroup.SERVICES, "MongoDB", R.drawable.ic_srv_mongodb),
            brand("redis", ServerIconGroup.SERVICES, "Redis", R.drawable.ic_srv_redis),
            brand("gitlab", ServerIconGroup.SERVICES, "GitLab", R.drawable.ic_srv_gitlab),
            brand("gitea", ServerIconGroup.SERVICES, "Gitea", R.drawable.ic_srv_gitea),
            brand("forgejo", ServerIconGroup.SERVICES, "Forgejo", R.drawable.ic_srv_forgejo),

            brand("googlecloud", ServerIconGroup.CLOUD, "Google Cloud", R.drawable.ic_srv_googlecloud),
            brand("digitalocean", ServerIconGroup.CLOUD, "DigitalOcean", R.drawable.ic_srv_digitalocean),
            brand("hetzner", ServerIconGroup.CLOUD, "Hetzner", R.drawable.ic_srv_hetzner),
            brand("cloudflare", ServerIconGroup.CLOUD, "Cloudflare", R.drawable.ic_srv_cloudflare),
            brand("vultr", ServerIconGroup.CLOUD, "Vultr", R.drawable.ic_srv_vultr),
            brand("ovh", ServerIconGroup.CLOUD, "OVHcloud", R.drawable.ic_srv_ovh)
        )

        private val byKey = ALL.associateBy { it.key }

        /** Keys of icons dropped during development, mapped to the closest remaining one. */
        private val legacyKeys = mapOf("vmware" to "vm", "qnap" to "storage")

        /** An unknown key (e.g. from a newer backup) degrades to the default icon. */
        fun fromKey(key: String?): ServerIcon =
            key?.let { byKey[legacyKeys[it] ?: it] } ?: DEFAULT
    }
}

@Composable
fun ServerIconImage(
    icon: ServerIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    val iconModifier = modifier.size(if (icon.wide) size * 1.3f else size)
    if (icon.vector != null) {
        Icon(icon.vector, contentDescription, iconModifier, tint)
    } else {
        Icon(painterResource(icon.drawable), contentDescription, iconModifier, tint)
    }
}
