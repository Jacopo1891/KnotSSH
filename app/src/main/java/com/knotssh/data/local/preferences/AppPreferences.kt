package com.knotssh.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.knotssh.terminal.TerminalTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "knotssh_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TerminalFont { MONOSPACE, SANS_SERIF, SERIF }

/** How unknown or changed SSH host keys are handled. */
enum class HostKeyPolicy {
    /** Refuse any host that is not already in the local known-hosts store. */
    STRICT,

    /** Show the fingerprint and let the user decide. */
    PROMPT,

    /** Silently trust the first key seen, but still block on a mismatch. */
    TRUST_ON_FIRST_USE,

    /** Accept anything. Disables MITM protection entirely. */
    ACCEPT_ANY
}

enum class BellMode { OFF, VIBRATE, SOUND }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

data class TerminalSettings(
    val fontSize: Int = Defaults.FONT_SIZE,
    val font: TerminalFont = TerminalFont.MONOSPACE,
    val theme: TerminalTheme = TerminalTheme.DARK,
    val ansiColorsEnabled: Boolean = true,
    val clickableUrls: Boolean = true,
    val scrollbackLines: Int = Defaults.SCROLLBACK,
    val autoSizePty: Boolean = true,
    val fallbackColumns: Int = Defaults.COLUMNS,
    val fallbackRows: Int = Defaults.ROWS,
    val cursorBlink: Boolean = true,
    val showQuickCommandsBar: Boolean = true,
    val showAccessoryBar: Boolean = true,
    val hapticFeedback: Boolean = true,
    val bellMode: BellMode = BellMode.VIBRATE,
    val keepScreenOn: Boolean = true,
    val autoShowKeyboard: Boolean = true,
    val swipeScrollsFullScreenApps: Boolean = true
)

data class ConnectionSettings(
    val keepAliveSeconds: Int = Defaults.KEEP_ALIVE_SECONDS,
    val connectTimeoutSeconds: Int = Defaults.CONNECT_TIMEOUT_SECONDS,
    val autoReconnect: Boolean = true,
    val autoReconnectAttempts: Int = Defaults.RECONNECT_ATTEMPTS,
    val compression: Boolean = false,
    val wakeLock: Boolean = true,
    val wakeLockMinutes: Int = Defaults.WAKE_LOCK_MINUTES,
    val foregroundNotification: Boolean = true,
    val maxSessions: Int = Defaults.MAX_SESSIONS,
    val closeOnExit: Boolean = true,
    val closeOnExitSeconds: Int = Defaults.CLOSE_ON_EXIT_SECONDS
)

data class SecuritySettings(
    val allowScreenshot: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricGraceSeconds: Int = Defaults.BIOMETRIC_GRACE_SECONDS,
    val hostKeyPolicy: HostKeyPolicy = HostKeyPolicy.PROMPT,
    val maskSecretsInUi: Boolean = true,
    val verboseSshLogging: Boolean = false
)

object Defaults {
    const val FONT_SIZE = 13
    const val FONT_SIZE_MIN = 8
    const val FONT_SIZE_MAX = 28
    const val SCROLLBACK = 5_000
    const val SCROLLBACK_MIN = 500
    const val SCROLLBACK_MAX = 50_000
    const val COLUMNS = 80
    const val ROWS = 24
    const val KEEP_ALIVE_SECONDS = 30
    const val CONNECT_TIMEOUT_SECONDS = 30
    const val RECONNECT_ATTEMPTS = 3
    const val WAKE_LOCK_MINUTES = 30
    const val BIOMETRIC_GRACE_SECONDS = 60
    const val MAX_SESSIONS = 5
    const val MAX_SESSIONS_LIMIT = 10
    const val CLOSE_ON_EXIT_SECONDS = 3
    const val CLOSE_ON_EXIT_SECONDS_MAX = 15
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        val TERMINAL_FONT_SIZE = intPreferencesKey("terminal_font_size")
        val TERMINAL_FONT = stringPreferencesKey("terminal_font")
        val TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val ANSI_COLORS = booleanPreferencesKey("ansi_colors")
        val CLICKABLE_URLS = booleanPreferencesKey("clickable_urls")
        val SCROLLBACK_LINES = intPreferencesKey("scrollback_lines")
        val AUTO_SIZE_PTY = booleanPreferencesKey("auto_size_pty")
        val FALLBACK_COLUMNS = intPreferencesKey("fallback_columns")
        val FALLBACK_ROWS = intPreferencesKey("fallback_rows")
        val CURSOR_BLINK = booleanPreferencesKey("cursor_blink")
        val SHOW_QUICK_COMMANDS = booleanPreferencesKey("show_quick_commands")
        val SHOW_ACCESSORY_BAR = booleanPreferencesKey("show_accessory_bar")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val BELL_MODE = stringPreferencesKey("bell_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_SHOW_KEYBOARD = booleanPreferencesKey("auto_show_keyboard")
        val SWIPE_SCROLLS_FULLSCREEN = booleanPreferencesKey("swipe_scrolls_fullscreen")

        val KEEP_ALIVE_SECONDS = intPreferencesKey("keep_alive_seconds")
        val CONNECT_TIMEOUT_SECONDS = intPreferencesKey("connect_timeout_seconds")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val AUTO_RECONNECT_ATTEMPTS = intPreferencesKey("auto_reconnect_attempts")
        val COMPRESSION = booleanPreferencesKey("compression")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock")
        val WAKE_LOCK_MINUTES = intPreferencesKey("wake_lock_minutes")
        val FOREGROUND_NOTIFICATION = booleanPreferencesKey("foreground_notification")
        val MAX_SESSIONS = intPreferencesKey("max_sessions")
        val CLOSE_ON_EXIT = booleanPreferencesKey("close_on_exit")
        val CLOSE_ON_EXIT_SECONDS = intPreferencesKey("close_on_exit_seconds")

        val ALLOW_SCREENSHOT = booleanPreferencesKey("allow_screenshot")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BIOMETRIC_GRACE_SECONDS = intPreferencesKey("biometric_grace_seconds")
        val HOST_KEY_POLICY = stringPreferencesKey("host_key_policy")
        val MASK_SECRETS = booleanPreferencesKey("mask_secrets_in_ui")
        val VERBOSE_SSH_LOGGING = booleanPreferencesKey("verbose_ssh_logging")

        val GOOGLE_DRIVE_SYNC = booleanPreferencesKey("google_drive_sync")
    }

    /** DataStore surfaces read errors through the flow; fall back to defaults instead of crashing collectors. */
    private val prefs: Flow<Preferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val appearance: Flow<AppearanceSettings> = prefs.map { p ->
        AppearanceSettings(
            themeMode = p[Keys.THEME_MODE].toEnum(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true
        )
    }.distinctUntilChanged()

    val terminal: Flow<TerminalSettings> = prefs.map { p ->
        TerminalSettings(
            fontSize = (p[Keys.TERMINAL_FONT_SIZE] ?: Defaults.FONT_SIZE)
                .coerceIn(Defaults.FONT_SIZE_MIN, Defaults.FONT_SIZE_MAX),
            font = p[Keys.TERMINAL_FONT].toEnum(TerminalFont.MONOSPACE),
            theme = p[Keys.TERMINAL_THEME].toEnum(TerminalTheme.DARK),
            ansiColorsEnabled = p[Keys.ANSI_COLORS] ?: true,
            clickableUrls = p[Keys.CLICKABLE_URLS] ?: true,
            scrollbackLines = (p[Keys.SCROLLBACK_LINES] ?: Defaults.SCROLLBACK)
                .coerceIn(Defaults.SCROLLBACK_MIN, Defaults.SCROLLBACK_MAX),
            autoSizePty = p[Keys.AUTO_SIZE_PTY] ?: true,
            fallbackColumns = (p[Keys.FALLBACK_COLUMNS] ?: Defaults.COLUMNS).coerceIn(20, 500),
            fallbackRows = (p[Keys.FALLBACK_ROWS] ?: Defaults.ROWS).coerceIn(5, 200),
            cursorBlink = p[Keys.CURSOR_BLINK] ?: true,
            showQuickCommandsBar = p[Keys.SHOW_QUICK_COMMANDS] ?: true,
            showAccessoryBar = p[Keys.SHOW_ACCESSORY_BAR] ?: true,
            hapticFeedback = p[Keys.HAPTIC_FEEDBACK] ?: true,
            bellMode = p[Keys.BELL_MODE].toEnum(BellMode.VIBRATE),
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            autoShowKeyboard = p[Keys.AUTO_SHOW_KEYBOARD] ?: true,
            swipeScrollsFullScreenApps = p[Keys.SWIPE_SCROLLS_FULLSCREEN] ?: true
        )
    }.distinctUntilChanged()

    val connection: Flow<ConnectionSettings> = prefs.map { p ->
        ConnectionSettings(
            keepAliveSeconds = (p[Keys.KEEP_ALIVE_SECONDS] ?: Defaults.KEEP_ALIVE_SECONDS).coerceIn(0, 600),
            connectTimeoutSeconds = (p[Keys.CONNECT_TIMEOUT_SECONDS] ?: Defaults.CONNECT_TIMEOUT_SECONDS).coerceIn(5, 120),
            autoReconnect = p[Keys.AUTO_RECONNECT] ?: true,
            autoReconnectAttempts = (p[Keys.AUTO_RECONNECT_ATTEMPTS] ?: Defaults.RECONNECT_ATTEMPTS).coerceIn(1, 10),
            compression = p[Keys.COMPRESSION] ?: false,
            wakeLock = p[Keys.WAKE_LOCK] ?: true,
            wakeLockMinutes = (p[Keys.WAKE_LOCK_MINUTES] ?: Defaults.WAKE_LOCK_MINUTES).coerceIn(5, 240),
            foregroundNotification = p[Keys.FOREGROUND_NOTIFICATION] ?: true,
            maxSessions = (p[Keys.MAX_SESSIONS] ?: Defaults.MAX_SESSIONS)
                .coerceIn(1, Defaults.MAX_SESSIONS_LIMIT),
            closeOnExit = p[Keys.CLOSE_ON_EXIT] ?: true,
            closeOnExitSeconds = (p[Keys.CLOSE_ON_EXIT_SECONDS] ?: Defaults.CLOSE_ON_EXIT_SECONDS)
                .coerceIn(1, Defaults.CLOSE_ON_EXIT_SECONDS_MAX)
        )
    }.distinctUntilChanged()

    val security: Flow<SecuritySettings> = prefs.map { p ->
        SecuritySettings(
            allowScreenshot = p[Keys.ALLOW_SCREENSHOT] ?: false,
            biometricEnabled = p[Keys.BIOMETRIC_ENABLED] ?: false,
            biometricGraceSeconds = (p[Keys.BIOMETRIC_GRACE_SECONDS] ?: Defaults.BIOMETRIC_GRACE_SECONDS).coerceIn(0, 3600),
            hostKeyPolicy = p[Keys.HOST_KEY_POLICY].toEnum(HostKeyPolicy.PROMPT),
            maskSecretsInUi = p[Keys.MASK_SECRETS] ?: true,
            verboseSshLogging = p[Keys.VERBOSE_SSH_LOGGING] ?: false
        )
    }.distinctUntilChanged()

    val googleDriveSync: Flow<Boolean> = prefs
        .map { it[Keys.GOOGLE_DRIVE_SYNC] ?: false }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) = put(Keys.THEME_MODE, value.name)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC_COLOR, value)

    suspend fun setTerminalFontSize(value: Int) =
        put(Keys.TERMINAL_FONT_SIZE, value.coerceIn(Defaults.FONT_SIZE_MIN, Defaults.FONT_SIZE_MAX))

    suspend fun setTerminalFont(value: TerminalFont) = put(Keys.TERMINAL_FONT, value.name)
    suspend fun setTerminalTheme(value: TerminalTheme) = put(Keys.TERMINAL_THEME, value.name)
    suspend fun setAnsiColorsEnabled(value: Boolean) = put(Keys.ANSI_COLORS, value)
    suspend fun setClickableUrls(value: Boolean) = put(Keys.CLICKABLE_URLS, value)

    suspend fun setScrollbackLines(value: Int) =
        put(Keys.SCROLLBACK_LINES, value.coerceIn(Defaults.SCROLLBACK_MIN, Defaults.SCROLLBACK_MAX))

    suspend fun setAutoSizePty(value: Boolean) = put(Keys.AUTO_SIZE_PTY, value)
    suspend fun setFallbackColumns(value: Int) = put(Keys.FALLBACK_COLUMNS, value.coerceIn(20, 500))
    suspend fun setFallbackRows(value: Int) = put(Keys.FALLBACK_ROWS, value.coerceIn(5, 200))
    suspend fun setCursorBlink(value: Boolean) = put(Keys.CURSOR_BLINK, value)
    suspend fun setShowQuickCommandsBar(value: Boolean) = put(Keys.SHOW_QUICK_COMMANDS, value)
    suspend fun setShowAccessoryBar(value: Boolean) = put(Keys.SHOW_ACCESSORY_BAR, value)
    suspend fun setHapticFeedback(value: Boolean) = put(Keys.HAPTIC_FEEDBACK, value)
    suspend fun setBellMode(value: BellMode) = put(Keys.BELL_MODE, value.name)
    suspend fun setKeepScreenOn(value: Boolean) = put(Keys.KEEP_SCREEN_ON, value)
    suspend fun setAutoShowKeyboard(value: Boolean) = put(Keys.AUTO_SHOW_KEYBOARD, value)
    suspend fun setSwipeScrollsFullScreenApps(value: Boolean) =
        put(Keys.SWIPE_SCROLLS_FULLSCREEN, value)

    suspend fun setKeepAliveSeconds(value: Int) = put(Keys.KEEP_ALIVE_SECONDS, value.coerceIn(0, 600))
    suspend fun setConnectTimeoutSeconds(value: Int) = put(Keys.CONNECT_TIMEOUT_SECONDS, value.coerceIn(5, 120))
    suspend fun setAutoReconnect(value: Boolean) = put(Keys.AUTO_RECONNECT, value)
    suspend fun setAutoReconnectAttempts(value: Int) = put(Keys.AUTO_RECONNECT_ATTEMPTS, value.coerceIn(1, 10))
    suspend fun setCompression(value: Boolean) = put(Keys.COMPRESSION, value)
    suspend fun setWakeLock(value: Boolean) = put(Keys.WAKE_LOCK, value)
    suspend fun setWakeLockMinutes(value: Int) = put(Keys.WAKE_LOCK_MINUTES, value.coerceIn(5, 240))
    suspend fun setForegroundNotification(value: Boolean) = put(Keys.FOREGROUND_NOTIFICATION, value)

    suspend fun setMaxSessions(value: Int) =
        put(Keys.MAX_SESSIONS, value.coerceIn(1, Defaults.MAX_SESSIONS_LIMIT))

    suspend fun setCloseOnExit(value: Boolean) = put(Keys.CLOSE_ON_EXIT, value)
    suspend fun setCloseOnExitSeconds(value: Int) =
        put(Keys.CLOSE_ON_EXIT_SECONDS, value.coerceIn(1, Defaults.CLOSE_ON_EXIT_SECONDS_MAX))

    suspend fun setAllowScreenshot(value: Boolean) = put(Keys.ALLOW_SCREENSHOT, value)
    suspend fun setBiometricEnabled(value: Boolean) = put(Keys.BIOMETRIC_ENABLED, value)
    suspend fun setBiometricGraceSeconds(value: Int) = put(Keys.BIOMETRIC_GRACE_SECONDS, value.coerceIn(0, 3600))
    suspend fun setHostKeyPolicy(value: HostKeyPolicy) = put(Keys.HOST_KEY_POLICY, value.name)
    suspend fun setMaskSecretsInUi(value: Boolean) = put(Keys.MASK_SECRETS, value)
    suspend fun setVerboseSshLogging(value: Boolean) = put(Keys.VERBOSE_SSH_LOGGING, value)

    suspend fun setGoogleDriveSync(value: Boolean) = put(Keys.GOOGLE_DRIVE_SYNC, value)

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(fallback: E): E =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback
