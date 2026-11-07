package app.jfago

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduledBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val user = inputData.getString("user") ?: return@withContext Result.failure()
        val prefs = applicationContext.getSharedPreferences("allerium_backup_automation", Context.MODE_PRIVATE)
        fun recordHistory(result: String) {
            val key = "history_$url"
            val history = org.json.JSONArray(prefs.getString(key, "[]") ?: "[]")
            history.put(org.json.JSONObject().put("time", System.currentTimeMillis()).put("result", result))
            while (history.length() > 20) history.remove(0)
            prefs.edit().putString(key, history.toString()).apply()
        }
        try {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val credentials = EncryptedSharedPreferences.create(
                applicationContext, "allerium_credentials", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val password = credentials.getString(url, null)
                ?: throw IllegalStateException("No saved encrypted password is available for this server.")
            val api = Api(url)
            api.login(user, password)
            api.createServerBackup()
            prefs.edit().putLong("last_success_$url", System.currentTimeMillis())
                .putString("last_result_$url", "success").apply()
            recordHistory("Success")
            Result.success()
        } catch (e: Exception) {
            val detail = e.message ?: "Backup failed"
            prefs.edit().putLong("last_attempt_$url", System.currentTimeMillis())
                .putString("last_result_$url", detail).apply()
            recordHistory("Failed: $detail")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
