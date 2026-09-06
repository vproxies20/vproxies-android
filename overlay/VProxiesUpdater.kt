package io.nekohasekai.sfa.vproxies

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class VProxiesUpdate(
    val version: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
)

internal class VProxiesUpdater(private val context: Context) {
    companion object {
        private const val LATEST_RELEASE =
            "https://api.github.com/repos/vproxies20/vproxies-android/releases/latest"

        fun isNewer(candidate: String, current: String): Boolean {
            val left = candidate.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val right = current.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            return (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { index ->
                val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
                difference.takeIf { it != 0 }
            }?.let { it > 0 } ?: false
        }
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun currentVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("").ifBlank { "0.0.0" }

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun checkLatest(): VProxiesUpdate {
        val root = JSONObject(readText(LATEST_RELEASE))
        val version = root.optString("tag_name").removePrefix("v")
        if (version.isBlank()) error("The latest GitHub release has no version tag.")
        val abiToken = preferredAssetToken()
        val assets = root.optJSONArray("assets") ?: error("The latest GitHub release has no APK assets.")
        val candidates = (0 until assets.length()).mapNotNull(assets::optJSONObject)
        val asset = candidates.firstOrNull { item ->
            val name = item.optString("name")
            name.endsWith("-$abiToken.apk", true)
        } ?: error("No $abiToken APK is available for this device.")
        val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
        if (!digest.matches(Regex("[0-9a-f]{64}"))) {
            error("The release APK is missing its SHA-256 digest.")
        }
        return VProxiesUpdate(
            version = version,
            notes = root.optString("body").trim().take(2_000),
            assetName = asset.optString("name"),
            downloadUrl = asset.optString("browser_download_url"),
            size = asset.optLong("size"),
            sha256 = digest,
        ).also {
            if (!it.downloadUrl.startsWith("https://github.com/") &&
                !it.downloadUrl.startsWith("https://objects.githubusercontent.com/")) {
                error("GitHub returned an unexpected APK download URL.")
            }
        }
    }

    fun download(update: VProxiesUpdate, onProgress: (Int) -> Unit): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val partial = File(directory, "VProxies-${update.version}-${preferredAssetToken()}.apk.part")
        val complete = File(directory, "VProxies-${update.version}-${preferredAssetToken()}.apk")
        val connection = open(URL(update.downloadUrl))
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "VProxies-Android/${currentVersion()}")
            val status = connection.responseCode
            if (status !in 200..299) error("GitHub APK download failed with HTTP $status.")
            val expectedSize = update.size.takeIf { it > 0 } ?: connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        if (expectedSize > 0) onProgress(((received * 100) / expectedSize).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (update.size > 0 && received != update.size) error("The downloaded APK size does not match GitHub.")
            val actual = digest.digest().joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            if (!actual.equals(update.sha256, true)) error("The downloaded APK failed SHA-256 verification.")
            if (!partial.renameTo(complete)) error("Unable to finalize the downloaded APK.")
            onProgress(100)
            return complete
        } catch (error: Throwable) {
            partial.delete()
            complete.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun readText(url: String): String {
        val connection = open(URL(url))
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "VProxies-Android/${currentVersion()}")
            val status = connection.responseCode
            if (status == 404) error("No VProxies release has been published yet.")
            if (status !in 200..299) error("GitHub update check failed with HTTP $status.")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: URL): HttpURLConnection {
        val physicalNetwork = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        }
        return (physicalNetwork?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
    }

    private fun preferredAssetToken(): String {
        val abis = Build.SUPPORTED_ABIS.map(String::lowercase)
        return when {
            "arm64-v8a" in abis -> "ARM64"
            "armeabi-v7a" in abis -> "ARM32"
            "x86_64" in abis -> "x86_64"
            "x86" in abis -> "x86"
            else -> error("This device architecture is not supported: ${abis.joinToString()}.")
        }
    }
}
