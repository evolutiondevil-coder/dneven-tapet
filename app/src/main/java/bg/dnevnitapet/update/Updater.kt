package bg.dnevnitapet.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import bg.dnevnitapet.BuildConfig
import bg.dnevnitapet.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import bg.dnevnitapet.i18n.tr

/** A published version on GitHub with its installable file. */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
    val notes: String,
)

object Versions {
    private val pattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")

    /** "1.3.0" / "v1.3.0" → [1, 3, 0]; null for test builds ("1.0.31-dev", "0.0.0-local"). */
    fun parse(name: String): List<Int>? =
        pattern.matchEntire(name.trim())?.groupValues?.drop(1)?.map { it.toInt() }

    /** True when [candidate] is a later release than [installed]. Test builds never ask for updates. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val c = parse(candidate) ?: return false
        val i = parse(installed) ?: return false
        for (k in 0 until 3) if (c[k] != i[k]) return c[k] > i[k]
        return false
    }
}

object ReleaseParser {
    @Serializable
    private data class GhAsset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0,
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("html_url") val page: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** The latest release from GitHub's answer, or null when it has no installable file. */
    fun parse(text: String): ReleaseInfo? {
        val r = json.decodeFromString(GhRelease.serializer(), text)
        if (r.draft || r.prerelease) return null
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.url.startsWith("https://") } ?: return null
        val version = Versions.parse(r.tag)?.joinToString(".") ?: return null
        return ReleaseInfo(r.tag, version, apk.url, apk.size, r.page, r.body.orEmpty())
    }
}

/**
 * "Нова версия": looks on GitHub for a newer release of the app, downloads its APK and hands it to
 * the phone's installer. The same signing key means it installs over the old version and keeps
 * all settings, the history and the favourites.
 */
class Updater(private val context: Context, private val http: Http) {
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val dir = File(context.cacheDir, "update")

    private val _available = MutableStateFlow<ReleaseInfo?>(null)

    /** A newer version than the installed one, once a check found it. */
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()

    /**
     * Asks GitHub for the latest release (at most every [minGapMs]; otherwise the last answer is used).
     * @return the newer release, or null when the app is up to date.
     */
    suspend fun check(minGapMs: Long = 30 * 60_000L): ReleaseInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = prefs.getString(KEY_JSON, null)
        val text = if (cached != null && now - prefs.getLong(KEY_AT, 0) < minGapMs) {
            cached
        } else {
            try {
                http.getText(LATEST_URL, mapOf("Accept" to "application/vnd.github+json")).also {
                    prefs.edit().putString(KEY_JSON, it).putLong(KEY_AT, now).apply()
                }
            } catch (e: Exception) {
                // Offline: the last answer still says whether a newer version is waiting.
                cached ?: throw e
            }
        }
        val latest = runCatching { ReleaseParser.parse(text) }.getOrNull()
        val newer = latest?.takeIf { Versions.isNewer(it.tag, BuildConfig.VERSION_NAME) }
        _available.value = newer
        if (newer == null) dir.listFiles()?.forEach { it.delete() }
        newer
    }

    /** Downloads the release's APK (kept for a retry) and checks that it is really this app. */
    suspend fun download(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val file = File(dir, "DnevenTapet-${release.version}.apk")
        if (!(file.exists() && release.apkSize > 0 && file.length() == release.apkSize && isThisApp(file))) {
            file.delete()
            dir.listFiles()?.forEach { it.delete() }
            http.downloadImage(release.apkUrl, file, maxBytes = 100L * 1024 * 1024)
        }
        if (!isThisApp(file)) {
            file.delete()
            throw IOException(tr("Изтегленият файл не е Дневен тапет"))
        }
        file
    }

    /** The APK is an installable package of this same app. */
    fun isThisApp(file: File): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName == context.packageName
    }.getOrDefault(false)

    /** Android 8+ asks once whether this app may install updates. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** The system screen "Инсталиране на неизвестни приложения" for this app. */
    fun openInstallPermission(): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /** Hands the downloaded APK to the phone's installer ("Да се актуализира ли приложението?"). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/evolutiondevil-coder/dneven-tapet/releases/latest"
        private const val KEY_JSON = "latest_json"
        private const val KEY_AT = "latest_at"
    }
}
