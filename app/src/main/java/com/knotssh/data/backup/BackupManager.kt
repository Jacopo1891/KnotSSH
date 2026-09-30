package com.knotssh.data.backup

import android.util.Base64
import com.knotssh.BuildConfig
import com.knotssh.data.local.preferences.AppPreferences
import com.knotssh.domain.model.AuthType
import com.knotssh.domain.model.Credential
import com.knotssh.domain.model.CustomKey
import com.knotssh.domain.model.ForwardType
import com.knotssh.domain.model.HostKeyVerdict
import com.knotssh.domain.model.PortForwardRule
import com.knotssh.domain.model.QuickCommand
import com.knotssh.domain.model.Server
import com.knotssh.domain.model.SshKeyType
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.CustomKeyRepository
import com.knotssh.domain.repository.KnownHostRepository
import com.knotssh.domain.repository.QuickCommandRepository
import com.knotssh.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and applies `.knotssh` backups.
 *
 * Merge rule, uniform across sections: an entry that is absent is added, one that is present and
 * byte-for-byte identical is skipped, and one that clashes by name but differs is added under
 * `<name>_Importato`. Nothing the user already has is ever overwritten.
 */
@Singleton
class BackupManager @Inject constructor(
    private val serverRepository: ServerRepository,
    private val credentialRepository: CredentialRepository,
    private val knownHostRepository: KnownHostRepository,
    private val quickCommandRepository: QuickCommandRepository,
    private val customKeyRepository: CustomKeyRepository,
    private val appPreferences: AppPreferences
) {

    suspend fun export(includeSettings: Boolean): BackupPayload = withContext(Dispatchers.IO) {
        val credentials = credentialRepository.getAllCredentials().first()
        val aliasById = credentials.associate { it.id to it.alias }

        BackupPayload(
            exportedAtMs = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            credentials = credentials.map { it.toBackup() },
            servers = serverRepository.getAllServers().first().map { server ->
                BackupServer(
                    alias = server.alias,
                    hostname = server.hostname,
                    port = server.port,
                    credentialAlias = aliasById[server.credentialId].orEmpty(),
                    isFavorite = server.isFavorite,
                    keepAliveSeconds = server.keepAliveSeconds,
                    connectTimeoutSeconds = server.connectTimeoutSeconds,
                    portForwardRules = server.portForwardRules.map {
                        BackupPortForward(it.type.name, it.localPort, it.remoteHost, it.remotePort)
                    }
                )
            },
            knownHosts = knownHostRepository.getAll().first().map {
                BackupKnownHost(it.host, it.port, it.keyType, it.keyBlob)
            },
            quickCommands = quickCommandRepository.getAllCommands().first().map {
                BackupQuickCommand(it.label, it.command, it.sortOrder)
            },
            customKeys = customKeyRepository.getAll().first().map {
                BackupCustomKey(it.label, it.sequence, it.sortOrder)
            },
            settings = if (includeSettings) currentSettings() else null
        )
    }

    fun summarize(payload: BackupPayload) = BackupSummary(
        credentials = payload.credentials.size,
        servers = payload.servers.size,
        knownHosts = payload.knownHosts.size,
        quickCommands = payload.quickCommands.size,
        customKeys = payload.customKeys.size,
        hasSettings = payload.settings != null
    )

    suspend fun import(
        payload: BackupPayload,
        restoreSettings: Boolean
    ): ImportOutcome = withContext(Dispatchers.IO) {
        var renamed = 0

        // Credentials first: servers are resolved against the ids they get here.
        val existingCredentials = credentialRepository.getAllCredentials().first()
        val credentialIdByAlias = mutableMapOf<String, Long>()
        var credentialsAdded = 0
        var credentialsSkipped = 0

        for (incoming in payload.credentials) {
            val existing = existingCredentials.firstOrNull { it.alias == incoming.alias }
            if (existing != null && existing.matches(incoming)) {
                credentialIdByAlias[incoming.alias] = existing.id
                credentialsSkipped++
                continue
            }
            val alias = if (existing == null) {
                incoming.alias
            } else {
                renamed++
                freeAlias(incoming.alias, existingCredentials.map { it.alias } + credentialIdByAlias.keys)
            }
            val id = credentialRepository.save(
                credential = Credential(
                    alias = alias,
                    username = incoming.username,
                    authType = incoming.authType.toEnumOr(AuthType.PASSWORD),
                    publicKey = incoming.publicKey,
                    keyType = incoming.keyType?.toEnumOrNull<SshKeyType>(),
                    askEachTime = incoming.askEachTime
                ),
                plainSecret = incoming.secret,
                plainPassphrase = incoming.passphrase
            )
            credentialIdByAlias[incoming.alias] = id
            credentialsAdded++
        }

        val existingServers = serverRepository.getAllServers().first()
        val takenServerAliases = existingServers.map { it.alias }.toMutableList()
        var serversAdded = 0
        var serversSkipped = 0

        for (incoming in payload.servers) {
            val credentialId = credentialIdByAlias[incoming.credentialAlias] ?: 0L
            val existing = existingServers.firstOrNull { it.alias == incoming.alias }
            if (existing != null && existing.matches(incoming, credentialId)) {
                serversSkipped++
                continue
            }
            val alias = if (existing == null) {
                incoming.alias
            } else {
                renamed++
                freeAlias(incoming.alias, takenServerAliases)
            }
            takenServerAliases += alias
            serverRepository.insertServer(
                Server(
                    alias = alias,
                    hostname = incoming.hostname,
                    port = incoming.port,
                    credentialId = credentialId,
                    isFavorite = incoming.isFavorite,
                    keepAliveSeconds = incoming.keepAliveSeconds,
                    connectTimeoutSeconds = incoming.connectTimeoutSeconds,
                    portForwardRules = incoming.portForwardRules.map {
                        PortForwardRule(
                            type = it.type.toEnumOr(ForwardType.LOCAL),
                            localPort = it.localPort,
                            remoteHost = it.remoteHost,
                            remotePort = it.remotePort
                        )
                    }
                )
            )
            serversAdded++
        }

        var hostsAdded = 0
        var hostsSkipped = 0
        var hostConflicts = 0
        for (incoming in payload.knownHosts) {
            val blob = runCatching { Base64.decode(incoming.keyBlob, Base64.NO_WRAP) }.getOrNull()
                ?: continue
            when (knownHostRepository.verify(incoming.host, incoming.port, incoming.keyType, blob)) {
                is HostKeyVerdict.Trusted -> hostsSkipped++
                is HostKeyVerdict.Unknown -> {
                    knownHostRepository.trust(incoming.host, incoming.port, incoming.keyType, blob)
                    hostsAdded++
                }
                // A backup must never be able to replace a trusted key: that is the one thing
                // standing between the user and a man-in-the-middle.
                is HostKeyVerdict.Changed -> hostConflicts++
            }
        }

        val existingCommands = quickCommandRepository.getAllCommands().first()
        val takenCommandLabels = existingCommands.map { it.label }.toMutableList()
        var commandsAdded = 0
        var commandsSkipped = 0
        for (incoming in payload.quickCommands) {
            val existing = existingCommands.firstOrNull { it.label == incoming.label }
            if (existing != null && existing.command == incoming.command) {
                commandsSkipped++
                continue
            }
            val label = if (existing == null) {
                incoming.label
            } else {
                renamed++
                freeAlias(incoming.label, takenCommandLabels)
            }
            takenCommandLabels += label
            quickCommandRepository.insertCommand(
                QuickCommand(label = label, command = incoming.command, sortOrder = incoming.sortOrder)
            )
            commandsAdded++
        }

        val existingKeys = customKeyRepository.getAll().first()
        val takenKeyLabels = existingKeys.map { it.label }.toMutableList()
        var keysAdded = 0
        var keysSkipped = 0
        for (incoming in payload.customKeys) {
            val existing = existingKeys.firstOrNull { it.label == incoming.label }
            if (existing != null && existing.sequence == incoming.sequence) {
                keysSkipped++
                continue
            }
            val label = if (existing == null) {
                incoming.label
            } else {
                renamed++
                freeAlias(incoming.label, takenKeyLabels)
            }
            takenKeyLabels += label
            customKeyRepository.save(
                CustomKey(label = label, sequence = incoming.sequence, sortOrder = incoming.sortOrder)
            )
            keysAdded++
        }

        val settingsRestored = restoreSettings && payload.settings != null
        if (settingsRestored) appPreferences.restoreFromBackup(payload.settings!!)

        ImportOutcome(
            added = BackupSummary(
                credentials = credentialsAdded,
                servers = serversAdded,
                knownHosts = hostsAdded,
                quickCommands = commandsAdded,
                customKeys = keysAdded,
                hasSettings = settingsRestored
            ),
            skipped = BackupSummary(
                credentials = credentialsSkipped,
                servers = serversSkipped,
                knownHosts = hostsSkipped,
                quickCommands = commandsSkipped,
                customKeys = keysSkipped,
                hasSettings = false
            ),
            renamed = renamed,
            conflictingHostKeys = hostConflicts,
            settingsRestored = settingsRestored
        )
    }

    private suspend fun Credential.toBackup() = BackupCredential(
        alias = alias,
        username = username,
        authType = authType.name,
        secret = credentialRepository.decryptSecret(this),
        passphrase = credentialRepository.decryptPassphrase(this),
        publicKey = publicKey,
        keyType = keyType?.name,
        askEachTime = askEachTime
    )

    private suspend fun Credential.matches(other: BackupCredential): Boolean =
        username == other.username &&
            authType.name == other.authType &&
            askEachTime == other.askEachTime &&
            keyType?.name == other.keyType &&
            credentialRepository.decryptSecret(this) == other.secret &&
            credentialRepository.decryptPassphrase(this) == other.passphrase

    private fun Server.matches(other: BackupServer, resolvedCredentialId: Long): Boolean =
        hostname == other.hostname &&
            port == other.port &&
            credentialId == resolvedCredentialId &&
            keepAliveSeconds == other.keepAliveSeconds &&
            connectTimeoutSeconds == other.connectTimeoutSeconds &&
            portForwardRules.size == other.portForwardRules.size &&
            portForwardRules.zip(other.portForwardRules).all { (mine, theirs) ->
                mine.type.name == theirs.type &&
                    mine.localPort == theirs.localPort &&
                    mine.remoteHost == theirs.remoteHost &&
                    mine.remotePort == theirs.remotePort
            }

    private fun freeAlias(base: String, taken: Collection<String>): String {
        val candidate = "$base$IMPORTED_SUFFIX"
        if (candidate !in taken) return candidate
        var n = 2
        while ("$candidate$n" in taken) n++
        return "$candidate$n"
    }

    private suspend fun currentSettings(): BackupSettings {
        val appearance = appPreferences.appearance.first()
        val terminal = appPreferences.terminal.first()
        val connection = appPreferences.connection.first()
        val security = appPreferences.security.first()
        return BackupSettings(
            themeMode = appearance.themeMode.name,
            dynamicColor = appearance.dynamicColor,
            terminalFontSize = terminal.fontSize,
            terminalFont = terminal.font.name,
            terminalTheme = terminal.theme.name,
            ansiColors = terminal.ansiColorsEnabled,
            clickableUrls = terminal.clickableUrls,
            scrollbackLines = terminal.scrollbackLines,
            autoSizePty = terminal.autoSizePty,
            fallbackColumns = terminal.fallbackColumns,
            fallbackRows = terminal.fallbackRows,
            cursorBlink = terminal.cursorBlink,
            showQuickCommandsBar = terminal.showQuickCommandsBar,
            showAccessoryBar = terminal.showAccessoryBar,
            hapticFeedback = terminal.hapticFeedback,
            bellMode = terminal.bellMode.name,
            keepScreenOn = terminal.keepScreenOn,
            autoShowKeyboard = terminal.autoShowKeyboard,
            swipeScrollsFullScreenApps = terminal.swipeScrollsFullScreenApps,
            keepAliveSeconds = connection.keepAliveSeconds,
            connectTimeoutSeconds = connection.connectTimeoutSeconds,
            autoReconnect = connection.autoReconnect,
            autoReconnectAttempts = connection.autoReconnectAttempts,
            compression = connection.compression,
            wakeLock = connection.wakeLock,
            wakeLockMinutes = connection.wakeLockMinutes,
            foregroundNotification = connection.foregroundNotification,
            maxSessions = connection.maxSessions,
            closeOnExit = connection.closeOnExit,
            closeOnExitSeconds = connection.closeOnExitSeconds,
            allowScreenshot = security.allowScreenshot,
            biometricEnabled = security.biometricEnabled,
            biometricGraceSeconds = security.biometricGraceSeconds,
            hostKeyPolicy = security.hostKeyPolicy.name,
            maskSecretsInUi = security.maskSecretsInUi,
            verboseSshLogging = security.verboseSshLogging
        )
    }

    private companion object {
        const val IMPORTED_SUFFIX = "_Importato"
    }
}

private inline fun <reified E : Enum<E>> String.toEnumOr(fallback: E): E =
    runCatching { enumValueOf<E>(this) }.getOrDefault(fallback)

private inline fun <reified E : Enum<E>> String.toEnumOrNull(): E? =
    runCatching { enumValueOf<E>(this) }.getOrNull()
