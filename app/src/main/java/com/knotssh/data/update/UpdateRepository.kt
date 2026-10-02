package com.knotssh.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.knotssh.BuildConfig
import com.knotssh.data.diagnostics.DiagnosticsLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GhAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String = "",
    val assets: List<GhAsset> = emptyList()
)

data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long
)

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: DiagnosticsLog
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Builds published from source carry no comparable version, so they never self-update. */
    val isReleaseBuild: Boolean = SEMVER.matches(BuildConfig.VERSION_NAME)

    val currentVersion: String = BuildConfig.VERSION_NAME

    suspend fun fetchLatest(): Result<AppRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(URL(LATEST_RELEASE_API))
            val release = json.decodeFromString<GhRelease>(body)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: error("release ${release.tagName} has no APK asset")
            require(apk.browserDownloadUrl.startsWith("https://github.com/")) {
                "unexpected asset host"
            }
            AppRelease(
                version = release.tagName.removePrefix("v"),
                notes = release.body,
                pageUrl = release.htmlUrl,
                apkUrl = apk.browserDownloadUrl,
                apkName = apk.name,
                apkSize = apk.size
            )
        }.onFailure { log.warn(TAG, "update check failed: ${it.javaClass.simpleName}") }
    }

    fun isNewer(candidate: String): Boolean {
        if (!isReleaseBuild) return false
        val new = parse(candidate) ?: return false
        val now = parse(currentVersion) ?: return false
        return compareValuesBy(new, now, { it.first }, { it.second }, { it.third }) > 0
    }

    /**
     * Downloads into the app cache. Returns the file ready to hand to the package installer;
     * a mismatched signing key is rejected by Android itself, which is what makes this safe.
     */
    suspend fun download(
        release: AppRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, release.apkName)

            open(URL(release.apkUrl)).use { connection ->
                val total = if (connection.contentLengthLong > 0) {
                    connection.contentLengthLong
                } else {
                    release.apkSize
                }
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
            }
            target
        }.onFailure { log.warn(TAG, "update download failed: ${it.javaClass.simpleName}") }
    }

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

    private fun get(url: URL): String = open(url).use { it.inputStream.bufferedReader().readText() }

    private fun open(url: URL): HttpURLConnection {
        require(url.protocol == "https") { "refusing a non-HTTPS update URL" }
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "KnotSSH/${BuildConfig.VERSION_NAME}")
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            error("HTTP ${connection.responseCode}")
        }
        return connection
    }

    private fun parse(value: String): Triple<Int, Int, Int>? =
        SEMVER_PREFIX.find(value)?.destructured?.let { (a, b, c) ->
            Triple(a.toInt(), b.toInt(), c.toInt())
        }

    private companion object {
        const val TAG = "update"
        const val UPDATE_DIR = "updates"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val TIMEOUT_MS = 15_000
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/Jacopo1891/KnotSSH/releases/latest"
        val SEMVER = Regex("""^\d+\.\d+\.\d+$""")
        val SEMVER_PREFIX = Regex("""^(\d+)\.(\d+)\.(\d+)""")
    }
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try {
        block(this)
    } finally {
        disconnect()
    }
