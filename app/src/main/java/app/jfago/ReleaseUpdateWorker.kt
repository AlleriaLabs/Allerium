package app.jfago

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class ReleaseUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val connection = URL("https://api.github.com/repos/AlleriaLabs/Allerium/releases/latest").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Allerium-Android")
        val release = try {
            val code = connection.responseCode
            if (code == 404) return Result.success()
            if (code >= 400) return Result.retry()
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }

        val tag = release.optString("tag_name").trim()
        val url = release.optString("html_url").ifBlank { "https://github.com/AlleriaLabs/Allerium/releases/latest" }
        if (tag.isBlank() || release.optBoolean("draft") || release.optBoolean("prerelease")) return Result.success()
        val info = if (Build.VERSION.SDK_INT >= 33) {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        }
        val current = info.versionName.orEmpty()
        if (compareVersions(tag, current) <= 0) return Result.success()
        val prefs = applicationContext.getSharedPreferences("allerium_release_updates", Context.MODE_PRIVATE)
        if (prefs.getString("last_notified_tag", "") == tag) return Result.success()
        showNotification(tag, url)
        prefs.edit().putString("last_notified_tag", tag).apply()
        Result.success()
    } catch (_: Exception) { Result.retry() }

    private fun showNotification(tag: String, url: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Allerium updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications when a new Allerium version is published on GitHub"
            }
        )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pending = PendingIntent.getActivity(applicationContext, 7302, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(applicationContext, CHANNEL_ID)
            else @Suppress("DEPRECATION") Notification.Builder(applicationContext)
        val notification = builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Allerium update available")
            .setContentText("Version $tag is ready. Tap to view release notes and download the APK.")
            .setStyle(Notification.BigTextStyle().bigText("Allerium $tag is available on GitHub. Tap to view the release notes and download the latest APK."))
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun compareVersions(first: String, second: String): Int {
        fun parts(value: String) = Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()
        val a = parts(first); val b = parts(second)
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    companion object {
        private const val CHANNEL_ID = "allerium_release_updates"
        private const val NOTIFICATION_ID = 2101
        private const val WORK_NAME = "allerium_github_release_check"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReleaseUpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
