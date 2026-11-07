package app.jfago

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

object UpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/AlleriaLabs/Allerium/releases/latest"
    private const val PREFS = "allerium_release_updates"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Allerium-Android")
        val release = try {
            val code = connection.responseCode
            if (code == 404) return@withContext null
            if (code >= 400) throw IllegalStateException("GitHub returned HTTP $code.")
            org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }

        val tag = release.optString("tag_name").trim()
        if (tag.isBlank() || release.optBoolean("draft") || release.optBoolean("prerelease")) {
            return@withContext null
        }
        val current = currentVersion(context)
        val version = tag.removePrefix("v").removePrefix("V")
        if (!isNewer(version, current)) return@withContext null

        val dismissed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("dismissed_tag", null)
        if (dismissed == tag) return@withContext null

        val assets = release.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                    .takeIf { it.startsWith("https://") }
                if (apkUrl != null) break
            }
        }
        val url = apkUrl ?: return@withContext null
        UpdateInfo(tag, version, url, release.optString("body").trim())
    }

    fun dismissUpdate(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("dismissed_tag", tag).apply()
    }

    suspend fun downloadAndInstall(context: Context, update: UpdateInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return "Allow Allerium to install updates, then reopen the app and tap Update again."
        }

        return withContext(Dispatchers.IO) {
            val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Allerium-Android")
            val file = File(context.cacheDir, "Allerium-update.apk")
            try {
                val code = connection.responseCode
                if (code >= 400) throw IllegalStateException("GitHub download failed (HTTP $code).")
                val body = connection.inputStream
                body.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                if (file.length() < 1_000_000L) {
                    file.delete()
                    throw IllegalStateException("The downloaded file does not look like a valid APK.")
                }
                if (!hasMatchingPackageAndSigner(context, file)) {
                    file.delete()
                    throw IllegalStateException(
                        "This update is not signed with the same certificate as the installed Allerium app. " +
                            "Your current installation has not been changed."
                    )
                }
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                withContext(Dispatchers.Main) {
                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(install)
                }
            } finally {
                connection.disconnect()
            }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun hasMatchingPackageAndSigner(context: Context, apk: File): Boolean = runCatching {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val installed = manager.getPackageInfo(context.packageName, flags)
        val candidate = manager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (candidate.packageName != context.packageName) return false

        fun signatures(info: PackageInfo): List<ByteArray> {
            val values = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.toList().orEmpty()
            } else {
                info.signatures?.toList().orEmpty()
            }
            return values.map { it.toByteArray() }
        }
        val installedHashes = signatures(installed).map { sha256(it) }.toSet()
        val candidateHashes = signatures(candidate).map { sha256(it) }.toSet()
        installedHashes.isNotEmpty() && installedHashes == candidateHashes
    }.getOrDefault(false)

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun currentVersion(context: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(0)
            ).versionName ?: "1.0"
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }
    }.getOrDefault("1.0")

    private fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(value: String) = Regex("\\d+").findAll(value)
            .mapNotNull { it.value.toIntOrNull() }.toList()
        val a = parts(candidate)
        val b = parts(installed)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}

@Composable
fun UpdateAvailableDialog(update: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Allerium update available") },
        text = {
            Column {
                Text("Version ${update.versionName} is ready to install.")
                if (update.releaseNotes.isNotBlank()) {
                    Text(
                        update.releaseNotes.take(900),
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp))
                }
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 14.dp))
                    Text("Downloading update…", modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                error = null
                working = true
                scope.launch {
                    error = runCatching {
                        UpdateChecker.downloadAndInstall(context, update)
                    }.exceptionOrNull()?.message
                    working = false
                }
            }) { Text(if (working) "Downloading…" else "Update") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text("Later") }
        }
    )
}


@Composable
fun AutomaticUpdatePrompt(enabled: Boolean) {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkedForLaunch by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (enabled && !checkedForLaunch) {
            delay(1500)
            update = runCatching { UpdateChecker.checkForUpdate(context) }.getOrNull()
            checkedForLaunch = true
        }
    }

    update?.let { available ->
        UpdateAvailableDialog(
            update = available,
            onDismiss = {
                UpdateChecker.dismissUpdate(context, available.tag)
                update = null
            }
        )
    }
}
