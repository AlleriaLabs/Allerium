package app.jfago

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private data class AuditEntry(val time: Long, val method: String, val path: String, val status: Int, val success: Boolean)

@Composable
fun OperationsCenterDialog(
    api: Api,
    server: Srv,
    users: List<JSONObject>,
    invites: List<JSONObject>,
    activities: List<JSONObject>,
    onClose: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("allerium_operations", Context.MODE_PRIVATE) }
    val backupPrefs = remember(context) { context.getSharedPreferences("allerium_backup_automation", Context.MODE_PRIVATE) }
    val credentials = remember(context) {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "allerium_credentials", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    var section by remember { mutableStateOf("Analytics") }
    var checks by remember { mutableStateOf<List<DiagnosticCheck>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var audit by remember { mutableStateOf(emptyList<AuditEntry>()) }
    var schedule by remember { mutableStateOf(backupPrefs.getString("schedule_${server.url}", "Off") ?: "Off") }
    var retention by remember { mutableStateOf(backupPrefs.getInt("retention_${server.url}", 7).toString()) }
    var biometricEnabled by remember { mutableStateOf(context.getSharedPreferences("allerium_biometric", Context.MODE_PRIVATE).getBoolean(server.url, false)) }
    val workName = "allerium_backup_${server.url.hashCode()}"

    fun loadAudit() {
        val raw = prefs.getString("audit_${server.url}", "[]") ?: "[]"
        val arr = JSONArray(raw)
        audit = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { AuditEntry(it.optLong("time"), it.optString("method"),
                it.optString("path"), it.optInt("status"), it.optBoolean("success")) }
        }.sortedByDescending { it.time }
    }

    DisposableEffect(api, server.url) {
        api.auditSink = { method, path, status, success ->
            synchronized(prefs) {
                val arr = JSONArray(prefs.getString("audit_${server.url}", "[]") ?: "[]")
                arr.put(JSONObject().put("time", System.currentTimeMillis()).put("method", method)
                    .put("path", path).put("status", status).put("success", success))
                while (arr.length() > 500) arr.remove(0)
                prefs.edit().putString("audit_${server.url}", arr.toString()).apply()
            }
        }
        onDispose { api.auditSink = null }
    }
    LaunchedEffect(server.url) { loadAudit() }

    val now = System.currentTimeMillis()
    val active = users.count { !it.optBoolean("disabled") }
    val disabled = users.size - active
    val expired = users.count {
        val v = it.optLong("expiry")
        v > 0 && (if (v > 1_000_000_000_000L) v else v * 1000L) < now
    }
    val expiring = users.count {
        val v = it.optLong("expiry")
        if (v <= 0) false else {
            val ms = if (v > 1_000_000_000_000L) v else v * 1000L
            ms >= now && ms <= now + 30L * 86400000L
        }
    }
    val recentActivity = activities.count {
        val v = it.optLong("time")
        val ms = if (v > 1_000_000_000_000L) v else v * 1000L
        ms > now - 7L * 86400000L && ms <= now
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = CardC,
        title = { Text("Operations center", color = Color.White) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Analytics", "Audit", "Health", "Backups", "Security").forEach { item ->
                        FilterChip(selected = section == item, onClick = { section = item },
                            label = { Text(item, fontSize = 9.sp) })
                    }
                }
                when (section) {
                    "Analytics" -> {
                        Text("Account overview", color = Cyan, fontSize = 12.sp)
                        listOf("Total users" to users.size, "Active accounts" to active, "Disabled accounts" to disabled,
                            "Expired accounts" to expired, "Expiring in 30 days" to expiring,
                            "Invitations" to invites.size, "Activity · 7 days" to recentActivity).forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, color = Dim, fontSize = 11.sp)
                                Text(value.toString(), color = Color.White, fontSize = 12.sp)
                            }
                        }
                        Divider(color = Line)
                        Text("Account status", color = Dim, fontSize = 10.sp)
                        Bar(if (users.isEmpty()) 0f else active.toFloat() / users.size)
                        Text("Active ${active} · Disabled ${disabled}", color = Dim, fontSize = 9.sp)
                        Text("Metrics are calculated from the latest data loaded from this JFA-GO server.", color = Dim, fontSize = 9.sp)
                    }
                    "Audit" -> {
                        Text("Local administrator API audit", color = Cyan, fontSize = 12.sp)
                        Text("Records Allerium API actions on this device, including endpoint, method, response code and time. Keeps the latest 500 events per server.", color = Dim, fontSize = 9.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${audit.size} recorded events", color = Color.White, fontSize = 10.sp)
                            TextButton(onClick = ::loadAudit) { Text("Refresh", color = Cyan) }
                        }
                        audit.take(80).forEach { item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.method} ${item.path}", color = Color.White, fontSize = 9.sp)
                                    Text(if (item.success) "HTTP ${item.status}" else "Failed", color = if (item.success) Green else Color(0xFFFF8585), fontSize = 9.sp)
                                }
                                Text(DateFormat.getDateTimeInstance().format(Date(item.time)), color = Dim, fontSize = 8.sp)
                            }
                            Divider(color = Line)
                        }
                        OutlinedButton(enabled = audit.isNotEmpty(), onClick = {
                            val csv = buildString {
                                append("time,method,path,status,success\n")
                                audit.forEach { e ->
                                    val row = listOf(e.time.toString(), e.method, e.path, e.status.toString(), e.success.toString())
                                    append(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\n")
                                }
                            }
                            shareCsv(context, "Allerium audit trail.csv", csv)
                        }) { Text("Export audit CSV", fontSize = 10.sp) }
                    }
                    "Health" -> {
                        Text("JFA-GO server health", color = Cyan, fontSize = 12.sp)
                        Text("Server: ${server.url}", color = Dim, fontSize = 9.sp)
                        Text("Connection security: ${if (server.url.startsWith("https://", true)) "HTTPS" else "HTTP · unencrypted transport"}",
                            color = if (server.url.startsWith("https://", true)) Green else Color(0xFFFFC36B), fontSize = 10.sp)
                        checks.forEach { check ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(check.label, color = Color.White, fontSize = 10.sp)
                                    Text(check.detail, color = Dim, fontSize = 9.sp)
                                }
                                Text("${check.durationMs} ms", color = Cyan, fontSize = 9.sp)
                            }
                        }
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try { checks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.diagnostics() } }
                                catch (e: Exception) { feedback = e.message ?: "Health check failed." }
                                finally { busy = false }
                            }
                        }) { Text(if (checks.isEmpty()) "Run health checks" else "Run again", fontSize = 10.sp) }
                        Text("Checks authenticated API availability and response time. Device CPU, memory, disk, and historical uptime are not exposed by the JFA-GO API used here.", color = Dim, fontSize = 9.sp)
                    }
                    "Backups" -> {
                        Text("Scheduled database backups", color = Cyan, fontSize = 12.sp)
                        Text("Runs in the background using Android WorkManager. The encrypted saved server password is used; it is never copied into the work request.", color = Dim, fontSize = 9.sp)
                        Text("Schedule: ${schedule}", color = Color.White, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                try {
                                    require(credentials.getString(server.url, null) != null) { "Sign in and save this server password before enabling automation." }
                                    val interval = if (schedule == "Weekly") 168L else 24L
                                    val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(interval, TimeUnit.HOURS)
                                        .setInputData(workDataOf("url" to server.url, "user" to server.user)).build()
                                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(workName,
                                        ExistingPeriodicWorkPolicy.UPDATE, request)
                                    schedule = if (schedule == "Weekly") "Weekly" else "Daily"
                                    backupPrefs.edit().putString("schedule_${server.url}", schedule).apply()
                                    feedback = "Scheduled backup enabled."
                                } catch (e: Exception) { feedback = e.message ?: "Could not schedule backups." }
                            }) { Text("Enable daily", fontSize = 9.sp) }
                            OutlinedButton(enabled = !busy, onClick = {
                                try {
                                    require(credentials.getString(server.url, null) != null) { "Sign in and save this server password before enabling automation." }
                                    val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(168, TimeUnit.HOURS)
                                        .setInputData(workDataOf("url" to server.url, "user" to server.user)).build()
                                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(workName,
                                        ExistingPeriodicWorkPolicy.UPDATE, request)
                                    schedule = "Weekly"
                                    backupPrefs.edit().putString("schedule_${server.url}", schedule).apply()
                                    feedback = "Weekly backup enabled."
                                } catch (e: Exception) { feedback = e.message ?: "Could not schedule backups." }
                            }) { Text("Enable weekly", fontSize = 9.sp) }
                            OutlinedButton(enabled = !busy, onClick = {
                                WorkManager.getInstance(context).cancelUniqueWork(workName)
                                schedule = "Off"
                                backupPrefs.edit().putString("schedule_${server.url}", "Off").apply()
                                feedback = "Scheduled backups disabled."
                            }) { Text("Disable", fontSize = 9.sp) }
                        }
                        val last = backupPrefs.getLong("last_success_${server.url}", 0L)
                        val result = backupPrefs.getString("last_result_${server.url}", "No backup has run yet")
                        Text("Last successful backup: ${if (last > 0) DateFormat.getDateTimeInstance().format(Date(last)) else "Not recorded"}", color = Dim, fontSize = 9.sp)
                        Text("Latest result: ${result}", color = Dim, fontSize = 9.sp)
                        OutlinedButton(enabled = !busy, onClick = {
                            val request = OneTimeWorkRequestBuilder<ScheduledBackupWorker>()
                                .setInputData(workDataOf("url" to server.url, "user" to server.user)).build()
                            WorkManager.getInstance(context).enqueue(request)
                            feedback = "Backup job queued. Check the result here after it runs."
                        }) { Text("Run backup now", fontSize = 10.sp) }
                        Text("Android may defer periodic work to protect battery. Retention cleanup is not enabled because the connected JFA-GO backup API does not expose a verified delete endpoint.", color = Dim, fontSize = 9.sp)
                    }
                    "Security" -> {
                        Text("Session and device security", color = Cyan, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Server transport", color = Dim, fontSize = 10.sp)
                            Text(if (server.url.startsWith("https://", true)) "HTTPS" else "HTTP", color = if (server.url.startsWith("https://", true)) Green else Color(0xFFFFC36B), fontSize = 10.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Biometric sign-in", color = Dim, fontSize = 10.sp)
                            Text(if (biometricEnabled) "Enabled" else "Not enabled", color = if (biometricEnabled) Green else Dim, fontSize = 10.sp)
                        }
                        Text("Passwords are stored with Android EncryptedSharedPreferences. The active bearer token remains in memory for this session.", color = Dim, fontSize = 9.sp)
                        Text("For security, use HTTPS for remote servers and keep Android screen lock and biometrics enabled.", color = Dim, fontSize = 9.sp)
                        OutlinedButton(onClick = {
                            api.token = ""
                            onLogout()
                        }) { Text("End current session", color = Color(0xFFFF8585), fontSize = 10.sp) }
                        Text("JFA-GO session revocation and remote device-session listing are not exposed by the API integration, so this action clears the active Allerium session only.", color = Dim, fontSize = 9.sp)
                    }
                }
                if (feedback.isNotBlank()) Text(feedback, color = Cyan, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Done", color = Red) } }
    )
}
