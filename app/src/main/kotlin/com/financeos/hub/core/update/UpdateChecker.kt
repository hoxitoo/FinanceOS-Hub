package com.financeos.hub.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.financeos.hub.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-update via GitHub Releases.
 *
 * The app is otherwise fully offline; this is the only component that touches the network.
 * It queries the public GitHub Releases API for the latest published release, compares its
 * tag against [BuildConfig.VERSION_NAME], and — if newer — downloads the attached `.apk`
 * and hands it to the system package installer.
 *
 * No external HTTP library: plain [HttpURLConnection] + [org.json]. No user data leaves the
 * device — the only outbound requests are the read-only release lookup and the APK download.
 *
 * Updates install cleanly only because every CI-built debug APK is signed with the same
 * repo-committed debug keystore (see app/build.gradle.kts `signingConfigs.shared`); otherwise
 * Android would reject the install with a signature mismatch.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Release(
        val versionName: String,   // normalised, e.g. "0.1.0.42"
        val tagName    : String,   // raw tag, e.g. "v0.1.0.42"
        val apkUrl     : String,
        val notes      : String,
        val sizeBytes  : Long,
    )

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class Available(val release: Release) : CheckResult
        data class Error(val message: String) : CheckResult
    }

    /** Currently installed version, suffix-stripped (e.g. "0.1.0.42-debug" → "0.1.0.42"). */
    val currentVersion: String get() = normalize(BuildConfig.VERSION_NAME)

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
            val (code, body) = httpGetWithRetry(url)
            when {
                code == 404                 -> return@runCatching CheckResult.Error("Релизы пока не опубликованы")
                code == 403 || code == 429  ->
                    return@runCatching CheckResult.Error("Слишком много запросов к GitHub — попробуйте через несколько минут")
                code in 500..599            ->
                    return@runCatching CheckResult.Error("GitHub временно недоступен ($code) — попробуйте позже")
                code !in 200..299 || body == null ->
                    return@runCatching CheckResult.Error("Сервер вернул ошибку ($code)")
            }
            val obj = JSONObject(body!!)
            val tag = obj.optString("tag_name").trim()
            if (tag.isEmpty()) return@runCatching CheckResult.Error("Релизы не найдены")

            val assets = obj.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: return@runCatching CheckResult.Error("В релизе нет APK-файла")

            val release = Release(
                versionName = normalize(tag),
                tagName     = tag,
                apkUrl      = apk.optString("browser_download_url"),
                notes       = obj.optString("body").trim(),
                sizeBytes   = apk.optLong("size"),
            )
            if (release.apkUrl.isBlank()) return@runCatching CheckResult.Error("Ссылка на APK недоступна")

            if (isNewer(release.versionName, currentVersion)) CheckResult.Available(release)
            else CheckResult.UpToDate
        }.getOrElse { e ->
            // Re-throw CancellationException so structured concurrency is not broken: if the
            // user navigates away mid-check, the IO thread is released instead of blocking for
            // up to 15 s on the network timeout.
            if (e is CancellationException) throw e
            // A raw IOException message is a stack-trace fragment ("Unable to resolve host …"),
            // which is noise in a settings row — say what the user can act on instead.
            if (e is IOException) CheckResult.Error("Нет связи с GitHub — проверьте интернет")
            else CheckResult.Error(e.message ?: "Ошибка проверки обновлений")
        }
    }

    /**
     * Downloads the release APK into the cache, reporting 0f..1f progress. Returns the file.
     *
     * Retried on the same transient conditions as [httpGetWithRetry] — a 37 MB transfer over a
     * mobile connection is far more likely to be cut short than the 2 KB release lookup. Each
     * attempt restarts from zero (the output stream truncates the file), so a partial download
     * can never be handed to the package installer.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // keep only the freshest download
            val out  = File(dir, "financeos-${release.tagName}.apk")
            // Reject non-HTTPS URLs before opening a connection to prevent MITM substitution
            // (an HTTP URL or an HTTPS→HTTP redirect would allow an attacker-controlled APK).
            if (!release.apkUrl.startsWith("https://", ignoreCase = true))
                error("Небезопасная ссылка на APK (требуется HTTPS)")

            var lastError: IOException? = null
            repeat(RETRY_BACKOFF_MS.size + 1) { attempt ->
                if (attempt > 0) {
                    onProgress(0f)                       // the bar restarts with the transfer
                    delay(RETRY_BACKOFF_MS[attempt - 1])
                }
                try {
                    downloadOnce(release, out, onProgress)
                    onProgress(1f)
                    return@withContext out
                } catch (e: IOException) {
                    // A non-transient HTTP status throws IllegalStateException below and is NOT
                    // caught here — retrying a 404 asset three times would only stall the user.
                    lastError = e
                }
            }
            out.delete()
            throw lastError ?: IOException("Загрузка не удалась")
        }

    private fun downloadOnce(release: Release, out: File, onProgress: (Float) -> Unit) {
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout         = 15_000
            readTimeout            = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code in TRANSIENT_CODES) throw IOException("Загрузка не удалась ($code)")
            if (code !in 200..299) error("Загрузка не удалась ($code)")
            val total = if (release.sizeBytes > 0) release.sizeBytes
                        else conn.contentLengthLong.coerceAtLeast(1L)
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read = input.read(buf)
                    while (read != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        read = input.read(buf)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Whether the OS will let us install packages (Android 8+ per-app "unknown sources"). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Launches the system installer for a downloaded APK. */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Deep-link to the per-app "Install unknown apps" toggle. */
    fun openUnknownSourcesSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ─── internals ────────────────────────────────────────────────────────────

    /**
     * [httpGet] with bounded retries, because a single transient blip used to surface as a hard
     * red error in Settings («Сервер вернул ошибку (504)») even though the very next request
     * succeeded. A 504/502/503 is a gateway timeout — GitHub's edge, or a carrier proxy on a
     * mobile network — and is almost always gone a second later.
     *
     * Only transient statuses and [IOException] are retried; a 404 (no releases) or a 200 is a
     * final answer and returns immediately, so the common path costs exactly one request.
     * The last observed response wins over a later network failure, so the user still sees the
     * specific status code rather than a generic "нет связи".
     */
    private suspend fun httpGetWithRetry(spec: String): Pair<Int, String?> {
        var lastResult: Pair<Int, String?>? = null
        var lastError : IOException?        = null
        repeat(RETRY_BACKOFF_MS.size + 1) { attempt ->
            if (attempt > 0) delay(RETRY_BACKOFF_MS[attempt - 1])
            try {
                val result = httpGet(spec)
                if (result.first !in TRANSIENT_CODES) return result
                lastResult = result
            } catch (e: IOException) {
                lastError = e
            }
        }
        return lastResult ?: throw (lastError ?: IOException("Сеть недоступна"))
    }

    private fun httpGet(spec: String): Pair<Int, String?> {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout    = 15_000
            requestMethod  = "GET"
            // GitHub rejects requests without a User-Agent; Accept pins the API version.
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            code to body
        } finally {
            conn.disconnect()
        }
    }

    /** Strip leading "v" and any pre-release suffix: "v0.1.0.42-debug" → "0.1.0.42". */
    private fun normalize(v: String): String =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').trim()

    /** Numeric, component-wise semver-ish compare; missing components count as 0. */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.split('.').map { seg -> seg.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private companion object {
        const val USER_AGENT = "FinanceOS-Hub-Updater"

        /**
         * Statuses worth a second look — gateway/edge failures that clear in about a second.
         * 429 is deliberately NOT here: GitHub's rate limit resets in minutes, so retrying would
         * spend three requests to reach the same answer and delay an accurate message.
         */
        val TRANSIENT_CODES = setOf(408, 500, 502, 503, 504)

        /**
         * Waits before retry N. Size also sets the attempt count (attempts = size + 1 = 3).
         * Kept short so a genuinely-down GitHub still fails inside a few seconds — the user is
         * staring at a settings row, not a background job.
         */
        val RETRY_BACKOFF_MS = longArrayOf(800L, 2_000L)
    }
}
