package app.jfago

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder

class Api(server: String) {
    val base = server.trim().trimEnd('/')
    var token = ""

    private fun call(method: String, path: String, body: JSONObject? = null, basic: String? = null): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 30000
        if (basic != null) c.setRequestProperty("Authorization", "Basic $basic")
        else c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code >= 400) throw Exception("HTTP $code ${text.take(120)}")
        return text
    }

    fun login(user: String, pass: String) {
        val b = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
        token = JSONObject(call("GET", "/token/login", basic = b)).getString("token")
    }

    private fun list(text: String, key: String): JSONArray {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return JSONArray()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        if (!trimmed.startsWith("{")) {
            val preview = trimmed.replace(Regex("\\s+"), " ").take(120)
            val kind = if (trimmed.startsWith("<!DOCTYPE", true) || trimmed.startsWith("<html", true))
                "HTML page" else "plain-text response"
            throw IllegalStateException("Expected JSON from $key endpoint, but the server returned a $kind. The API route may be unavailable or incorrect. Response: $preview")
        }
        val root = try { JSONObject(trimmed) } catch (e: Exception) {
            throw IllegalStateException("The $key endpoint returned malformed JSON: ${e.message ?: "parse error"}")
        }
        return root.optJSONArray(key)
            ?: root.optJSONArray("activity")
            ?: root.optJSONArray("items")
            ?: JSONArray()
    }

    fun users(): List<JSONObject> {
        val a = list(call("GET", "/users"), "users")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun invites(): List<JSONObject> {
        val a = list(call("GET", "/invites"), "invites")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun adminPasswordReset(id: String): JSONObject {
        return JSONObject(call("POST", "/users/password-reset", JSONObject().put("users", JSONArray().put(id))))
    }

    fun setEnabled(id: String, enabled: Boolean) {
        call("POST", "/users/enable", JSONObject().put("users", JSONArray().put(id)).put("enabled", enabled).put("notify", false))
    }

    fun deleteUser(id: String) {
        call("DELETE", "/users", JSONObject().put("users", JSONArray().put(id)).put("notify", false))
    }

    fun createInvite(days: Int, uses: Int, sendTo: String = "", profile: String = "") {
        val o = JSONObject().put("days", days).put("hours", 0).put("minutes", 0)
            .put("multiple-uses", uses > 1).put("no-limit", false).put("remaining-uses", uses)
        if (sendTo.isNotBlank()) o.put("send-to", sendTo.trim())
        if (profile.isNotBlank()) o.put("profile", profile)
        call("POST", "/invites", o)
    }

    fun activity(): List<JSONObject> {
        // JFA-GO's /activity API is POST-only. GET /activity serves the admin HTML page.
        val request = JSONObject()
            .put("searchTerms", JSONArray())
            .put("queries", JSONArray())
            .put("sortByField", "time")
            .put("ascending", false)
            .put("limit", 100)
            .put("page", 0)
        val a = list(call("POST", "/activity", request), "activities")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun deleteActivity(id: String) { call("DELETE", "/activity/$id") }

    fun createUser(name: String, pass: String, email: String, profile: String = "") {
        val o = JSONObject().put("username", name).put("password", pass).put("email", email)
        if (profile.isNotBlank()) o.put("profile", profile)
        call("POST", "/user", o)
    }

    fun profiles(): List<String> {
        val p = JSONObject(call("GET", "/profiles")).optJSONObject("profiles") ?: return emptyList()
        return p.keys().asSequence().toList()
    }

    fun config(): JSONObject = JSONObject(call("GET", "/config"))

    fun setConfig(section: String, key: String, value: Any) {
        call("POST", "/config", JSONObject().put(section, JSONObject().put(key, value)))
    }

    fun setExpiry(id: String, days: Int) {
        call("POST", "/users/extend", JSONObject().put("users", JSONArray().put(id))
            .put("months", 0).put("days", days).put("hours", 0).put("minutes", 0).put("notify", false))
    }

    fun bulkSetEnabled(ids: List<String>, enabled: Boolean) {
        require(ids.isNotEmpty()) { "Select at least one user." }
        call("POST", "/users/enable", JSONObject().put("users", JSONArray(ids))
            .put("enabled", enabled).put("notify", false))
    }

    fun bulkDeleteUsers(ids: List<String>) {
        require(ids.isNotEmpty()) { "Select at least one user." }
        call("DELETE", "/users", JSONObject().put("users", JSONArray(ids)).put("notify", false))
    }

    fun bulkSetExpiry(ids: List<String>, days: Int) {
        require(ids.isNotEmpty()) { "Select at least one user." }
        require(days > 0) { "Expiry days must be greater than zero." }
        call("POST", "/users/extend", JSONObject().put("users", JSONArray(ids))
            .put("months", 0).put("days", days).put("hours", 0).put("minutes", 0).put("notify", false))
    }

    fun removeExpiry(id: String) {
        call("DELETE", "/users/$id/expiry")
    }

    fun setUserLabels(labels: Map<String, String>) {
        call("POST", "/users/labels", JSONObject(labels))
    }

    fun announce(ids: List<String>, subject: String, message: String) {
        require(ids.isNotEmpty()) { "Select at least one recipient." }
        require(subject.isNotBlank() && message.isNotBlank()) { "Subject and message are required." }
        call("POST", "/users/announce", JSONObject().put("users", JSONArray(ids))
            .put("subject", subject).put("message", message))
    }

    fun announcementTemplates(): List<String> {
        val o = JSONObject(call("GET", "/users/announce"))
        val a = o.optJSONArray("announcements") ?: JSONArray()
        return (0 until a.length()).map { a.optString(it) }
    }

    fun saveAnnouncementTemplate(name: String, subject: String, message: String) {
        call("POST", "/users/announce/template",
            JSONObject().put("name", name).put("subject", subject).put("message", message))
    }

    fun sendInvite(code: String, recipient: String) {
        call("POST", "/invites/send", JSONObject().put("invite", code).put("send-to", recipient))
    }

    fun editInvite(code: String, label: String, userLabel: String, profile: String) {
        val body = JSONObject().put("code", code).put("label", label)
            .put("user_label", userLabel).put("profile", profile)
        call("PATCH", "/invites/edit", body)
    }

    fun serverBackups(): List<JSONObject> {
        val root = JSONObject(call("GET", "/backups"))
        val a = root.optJSONArray("backups") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun createServerBackup(): JSONObject = JSONObject(call("POST", "/backups"))

    fun restoreServerBackup(filename: String) {
        val safe = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        call("POST", "/backups/restore/$safe")
    }

    fun downloadServerBackup(filename: String, output: OutputStream) {
        val safe = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        val c = URL(base + "/backups/$safe").openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer $token")
        val code = c.responseCode
        if (code >= 400) {
            val error = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("Backup download failed: HTTP $code ${error.take(180)}")
        }
        c.inputStream.use { it.copyTo(output) }
        c.disconnect()
    }

    fun uploadServerBackup(filename: String, input: InputStream) {
        val boundary = "----AlleriumBackup${System.currentTimeMillis()}"
        val c = URL(base + "/backups/restore").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        c.outputStream.use { out ->
            out.write("--$boundary\\r\\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\\"backups-file\\"; filename=\\"$filename\\"\\r\\n".toByteArray())
            out.write("Content-Type: application/octet-stream\\r\\n\\r\\n".toByteArray())
            input.copyTo(out)
            out.write("\\r\\n--$boundary--\\r\\n".toByteArray())
        }
        try {
            val code = c.responseCode
            if (code >= 400) {
                val error = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Backup restore upload failed: HTTP $code ${error.take(180)}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Backup was uploaded, but JFA-GO may have restarted before confirming the response. Reconnect and check the server before retrying.", e)
        } finally {
            c.disconnect()
        }
    }

    fun jellyseerrUsers(): List<JSONObject> {
        val root = JSONObject(call("GET", "/jellyseerr/users"))
        val a = root.optJSONArray("users") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    fun setJellyseerrProfile(profile: String, jellyseerrUserId: String) {
        val p = URLEncoder.encode(profile, "UTF-8").replace("+", "%20")
        call("POST", "/profiles/jellyseerr/$p/$jellyseerrUserId")
    }

    fun removeJellyseerrProfile(profile: String) {
        val p = URLEncoder.encode(profile, "UTF-8").replace("+", "%20")
        call("DELETE", "/profiles/jellyseerr/$p")
    }

    fun applyProfileToUsers(ids: List<String>, profile: String) {
        require(ids.isNotEmpty()) { "Select at least one user." }
        call("POST", "/users/settings", JSONObject()
            .put("from", "profile").put("profile", profile).put("apply_to", JSONArray(ids))
            .put("configuration", true).put("homescreen", true).put("ombi", true).put("jellyseerr", true))
    }

    fun enableReferrals(ids: List<String>, profile: String, useExpiry: Boolean = false) {
        require(ids.isNotEmpty()) { "Select at least one user." }
        val p = URLEncoder.encode(profile, "UTF-8").replace("+", "%20")
        call("POST", "/users/referral/profile/$p/${if (useExpiry) "with-expiry" else "none"}",
            JSONObject().put("users", JSONArray(ids)))
    }

    fun disableReferrals(ids: List<String>) {
        require(ids.isNotEmpty()) { "Select at least one user." }
        call("DELETE", "/users/referral", JSONObject().put("users", JSONArray(ids)))
    }

    fun announcementTemplate(name: String): JSONObject {
        val n = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return JSONObject(call("GET", "/users/announce/template/$n"))
    }

    fun rawProfile(profile: String): JSONObject {
        val p = URLEncoder.encode(profile, "UTF-8").replace("+", "%20")
        return JSONObject(call("GET", "/profiles/raw/$p"))
    }

    fun updateRawProfile(profile: String, body: JSONObject) {
        val p = URLEncoder.encode(profile, "UTF-8").replace("+", "%20")
        call("PUT", "/profiles/raw/$p", body)
    }

    fun deleteInvite(code: String) {
        call("DELETE", "/invites", JSONObject().put("code", code))
    }
    fun diagnostics(): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()
        fun check(label: String, action: () -> String) {
            val started = System.nanoTime()
            try {
                val detail = action()
                checks += DiagnosticCheck(label, true, (System.nanoTime() - started) / 1_000_000L, detail)
            } catch (e: Exception) {
                checks += DiagnosticCheck(label, false, (System.nanoTime() - started) / 1_000_000L,
                    e.message?.take(180) ?: "Request failed")
            }
        }
        check("Configuration API") {
            config()
            "Authenticated configuration endpoint responded"
        }
        check("User directory") {
            val count = users().size
            count.toString() + " user" + (if (count == 1) "" else "s") + " returned"
        }
        check("Invitations API") {
            val count = invites().size
            count.toString() + " invitation" + (if (count == 1) "" else "s") + " returned"
        }
        check("Activity feed") {
            val count = activity().size
            count.toString() + " activity entr" + (if (count == 1) "y" else "ies") + " returned"
        }
        return checks
    }
}

data class DiagnosticCheck(
    val label: String,
    val passed: Boolean,
    val durationMs: Long,
    val detail: String
)