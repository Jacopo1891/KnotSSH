package com.knotssh.data.backup

import kotlinx.serialization.Serializable

/**
 * What a `.knotssh` file carries once decrypted.
 *
 * Deliberately made of primitives and enum *names* rather than of the app's own types: the file
 * format must survive refactors of the domain model, and an unknown enum value must degrade to a
 * default instead of failing the whole import.
 */
@Serializable
data class BackupPayload(
    val exportedAtMs: Long,
    val appVersion: String,
    val credentials: List<BackupCredential> = emptyList(),
    val servers: List<BackupServer> = emptyList(),
    val knownHosts: List<BackupKnownHost> = emptyList(),
    val quickCommands: List<BackupQuickCommand> = emptyList(),
    val customKeys: List<BackupCustomKey> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
    val settings: BackupSettings? = null
)

@Serializable
data class BackupFolder(
    val name: String,
    val sortOrder: Int = 0
)

/** Secrets travel in clear *inside* the encrypted envelope; the file itself protects them. */
@Serializable
data class BackupCredential(
    val alias: String,
    val username: String,
    val authType: String,
    val secret: String = "",
    val passphrase: String? = null,
    val publicKey: String? = null,
    val keyType: String? = null,
    val askEachTime: Boolean = false
)

@Serializable
data class BackupServer(
    val alias: String,
    val hostname: String,
    val port: Int,
    /** Credentials are referenced by alias: database ids are meaningless on another device. */
    val credentialAlias: String,
    val isFavorite: Boolean = false,
    val keepAliveSeconds: Int = 30,
    val connectTimeoutSeconds: Int = 30,
    val portForwardRules: List<BackupPortForward> = emptyList(),
    /** Folders are referenced by name, for the same reason credentials are referenced by alias. */
    val folderName: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class BackupPortForward(
    val type: String,
    val localPort: Int,
    val remoteHost: String = "",
    val remotePort: Int = 0
)

@Serializable
data class BackupKnownHost(
    val host: String,
    val port: Int,
    val keyType: String,
    /** Base64 of the raw key blob, exactly as the local store keeps it. */
    val keyBlob: String
)

@Serializable
data class BackupQuickCommand(
    val label: String,
    val command: String,
    val sortOrder: Int = 0
)

@Serializable
data class BackupCustomKey(
    val label: String,
    val sequence: String,
    val sortOrder: Int = 0
)

@Serializable
data class BackupSettings(
    val themeMode: String,
    val dynamicColor: Boolean,
    val terminalFontSize: Int,
    val terminalFont: String,
    val terminalTheme: String,
    val ansiColors: Boolean,
    val clickableUrls: Boolean,
    val scrollbackLines: Int,
    val autoSizePty: Boolean,
    val fallbackColumns: Int,
    val fallbackRows: Int,
    val cursorBlink: Boolean,
    val showQuickCommandsBar: Boolean,
    val showAccessoryBar: Boolean,
    val hapticFeedback: Boolean,
    val bellMode: String,
    val keepScreenOn: Boolean,
    val autoShowKeyboard: Boolean,
    val swipeScrollsFullScreenApps: Boolean,
    val keepAliveSeconds: Int,
    val connectTimeoutSeconds: Int,
    val autoReconnect: Boolean,
    val autoReconnectAttempts: Int,
    val compression: Boolean,
    val wakeLock: Boolean,
    val wakeLockMinutes: Int,
    val foregroundNotification: Boolean,
    val maxSessions: Int,
    val closeOnExit: Boolean,
    val closeOnExitSeconds: Int,
    val allowScreenshot: Boolean,
    val biometricEnabled: Boolean,
    val biometricGraceSeconds: Int,
    val hostKeyPolicy: String,
    val maskSecretsInUi: Boolean,
    val verboseSshLogging: Boolean
)

/** Per-section tallies shown to the user before and after an import. */
data class BackupSummary(
    val credentials: Int,
    val servers: Int,
    val knownHosts: Int,
    val quickCommands: Int,
    val customKeys: Int,
    val hasSettings: Boolean
)

/** What actually happened, section by section, once the merge ran. */
data class ImportOutcome(
    val added: BackupSummary,
    val skipped: BackupSummary,
    val renamed: Int,
    /** Host keys present locally with a *different* key: never overwritten silently. */
    val conflictingHostKeys: Int,
    val settingsRestored: Boolean
)
