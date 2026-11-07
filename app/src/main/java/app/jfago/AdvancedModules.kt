package app.jfago

import android.content.Intent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun AdvancedModulesPage(
    api: Api,
    users: List<JSONObject>,
    invites: List<JSONObject>,
    profiles: List<String>,
    initialModule: String = "Users",
    requireBiometricRestore: Boolean = false,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var module by remember { mutableStateOf(initialModule) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val selectedUsers = remember { mutableStateListOf<String>() }
    var expiryDays by remember { mutableStateOf("30") }
    var label by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var jfaAdminAccess by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf(profiles.firstOrNull().orEmpty()) }
    var subject by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf("") }
    var templates by remember { mutableStateOf(emptyList<String>()) }
    var selectedTemplate by remember { mutableStateOf("") }
    var selectedInvite by remember { mutableStateOf(invites.firstOrNull()?.optString("code").orEmpty()) }
    var inviteLabel by remember { mutableStateOf("") }
    var inviteUserLabel by remember { mutableStateOf("") }
    var inviteRecipient by remember { mutableStateOf("") }
    var backups by remember { mutableStateOf(emptyList<JSONObject>()) }
    var config by remember { mutableStateOf<JSONObject?>(null) }
    var configSection by remember { mutableStateOf("") }
    var configKey by remember { mutableStateOf("") }
    var configValue by remember { mutableStateOf("") }
    var jellyseerrUsers by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedJellyseerrUser by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf("") }

    fun execute(labelText: String, action: () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            feedback = ""
            try {
                withContext(Dispatchers.IO) { action() }
                feedback = "$labelText completed."
                onRefresh()
            } catch (e: Exception) {
                feedback = e.message ?: "$labelText failed."
            } finally {
                busy = false
            }
        }
    }

    val context = LocalContext.current
    var pendingDownloadName by remember { mutableStateOf("") }
    val hostActivity = fragmentActivity(context) as? MainActivity
    fun handleBackupDownload(uri: android.net.Uri?) {
        if (uri != null) {
            val filename = pendingDownloadName
            execute("Backup downloaded") {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected destination.")
                output.use { api.downloadServerBackup(filename, it) }
            }
        }
    }
    fun handleBackupUpload(uri: android.net.Uri?) {
        if (uri != null) {
            execute("Restore upload submitted") {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not read the selected backup.")
                input.use { api.uploadServerBackup("Allerium-upload.bak", it) }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!busy) onClose() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Cyan) }
            Column(Modifier.weight(1f)) {
                Text("All Tools", color = Color.White, fontSize = 21.sp)
                Text("Extended JFA-GO administration", color = Dim, fontSize = 10.sp)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                // Keep the module selector on one line; narrow phones can scroll it horizontally.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Users", "Messages", "Invites", "Backups", "Settings", "Integrations").forEach { name ->
                        FilterChip(
                            selected = module == name,
                            onClick = { module = name },
                            label = { Text(name, fontSize = 9.sp, maxLines = 1) }
                        )
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)

                when (module) {
                    "Users" -> {
                        Text("Bulk user management", color = Color.White, fontSize = 13.sp)
                        Text("${selectedUsers.size} selected · ${users.size} loaded", color = Dim, fontSize = 10.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                selectedUsers.clear()
                                selectedUsers.addAll(users.map { it.optString("id") }.filter { it.isNotBlank() })
                            }) { Text("Select all", color = Cyan, fontSize = 10.sp) }
                            TextButton(onClick = { selectedUsers.clear() }) { Text("Clear", color = Dim, fontSize = 10.sp) }
                        }
                        users.take(200).forEach { user ->
                            val id = user.optString("id")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = id.isNotBlank() && selectedUsers.contains(id),
                                    onCheckedChange = { checked ->
                                        if (checked && id.isNotBlank()) selectedUsers.add(id)
                                        else selectedUsers.remove(id)
                                    },
                                    enabled = id.isNotBlank()
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(user.optString("name"), color = Color.White, fontSize = 11.sp)
                                    Text(if (user.optBoolean("disabled")) "Disabled" else if (user.optBoolean("admin")) "Administrator" else "Active",
                                        color = Dim, fontSize = 9.sp)
                                }
                            }
                        }
                        Divider(color = Line)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                                execute("Enable users") { api.bulkSetEnabled(selectedUsers.toList(), true) }
                            }) { Text("Enable", fontSize = 10.sp) }
                            OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                                execute("Disable users") { api.bulkSetEnabled(selectedUsers.toList(), false) }
                            }) { Text("Disable", fontSize = 10.sp) }
                            OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = { confirmDelete = true }) {
                                Text("Delete", color = Color(0xFFFF8585), fontSize = 10.sp)
                            }
                        }
                        Field(expiryDays, { expiryDays = it.filter(Char::isDigit) }, "Extend expiry by days")
                        OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty() && (expiryDays.toIntOrNull() ?: 0) > 0,
                            onClick = { execute("Expiry updated") { api.bulkSetExpiry(selectedUsers.toList(), expiryDays.toInt()) } }) {
                            Text("Apply expiry", fontSize = 10.sp)
                        }
                        OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                            execute("Expiry removed") { selectedUsers.toList().forEach { api.removeExpiry(it) } }
                        }) { Text("Remove expiry", fontSize = 10.sp) }
                        Field(label, { label = it }, "Set label for selected users")
                        OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty() && label.isNotBlank(), onClick = {
                            execute("User labels updated") { api.setUserLabels(selectedUsers.associateWith { label.trim() }) }
                        }) { Text("Apply label", fontSize = 10.sp) }

                        Field(emailAddress, { emailAddress = it }, "Set email for selected users (blank clears)")
                        OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                            val ids = selectedUsers.toList()
                            val address = emailAddress.trim()
                            execute("User email addresses updated") { api.setUserEmails(ids.associateWith { address }) }
                        }) { Text("Apply email", fontSize = 10.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = jfaAdminAccess, onCheckedChange = { jfaAdminAccess = it })
                            Text("Grant selected users access to JFA-GO admin", color = Dim, fontSize = 10.sp)
                        }
                        OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                            val ids = selectedUsers.toList()
                            val allowed = jfaAdminAccess
                            execute("JFA-GO admin access updated") { api.setJfaAdminAccess(ids.associateWith { allowed }) }
                        }) { Text("Apply admin access", fontSize = 10.sp) }
                        if (profiles.isNotEmpty()) {
                            Chips(profiles, selectedProfile) { selectedProfile = it }
                            OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                                execute("Profile applied") { api.applyProfileToUsers(selectedUsers.toList(), selectedProfile) }
                            }) { Text("Apply profile to selected", fontSize = 10.sp) }
                            OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                                execute("Referrals enabled") { api.enableReferrals(selectedUsers.toList(), selectedProfile) }
                            }) { Text("Enable referrals from profile", fontSize = 10.sp) }
                        }
                        OutlinedButton(enabled = !busy && selectedUsers.isNotEmpty(), onClick = {
                            execute("Referrals disabled") { api.disableReferrals(selectedUsers.toList()) }
                        }) { Text("Disable referrals", fontSize = 10.sp) }
                        Text("Bulk actions use JFA-GO's native multi-user endpoints. Deletion is permanent.", color = Dim, fontSize = 9.sp)
                    }
                    "Messages" -> {
                        Text("Announcement center", color = Color.White, fontSize = 13.sp)
                        Text("Send a Markdown announcement to the selected users. JFA-GO must have message delivery configured.", color = Dim, fontSize = 10.sp)
                        Text("${selectedUsers.size} recipients selected in the Users tab.", color = Cyan, fontSize = 10.sp)
                        Field(subject, { subject = it }, "Email subject")
                        Field(announcement, { announcement = it }, "Message (Markdown supported)")
                        Field(templateName, { templateName = it }, "Template name")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = !busy && subject.isNotBlank() && announcement.isNotBlank() && selectedUsers.isNotEmpty(),
                                onClick = { execute("Announcement sent") { api.announce(selectedUsers.toList(), subject, announcement) } }) {
                                Text("Send announcement", fontSize = 10.sp)
                            }
                            OutlinedButton(enabled = !busy && templateName.isNotBlank() && subject.isNotBlank() && announcement.isNotBlank(),
                                onClick = { execute("Template saved") { api.saveAnnouncementTemplate(templateName.trim(), subject, announcement) } }) {
                                Text("Save template", fontSize = 10.sp)
                            }
                        }
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    templates = withContext(Dispatchers.IO) { api.announcementTemplates() }
                                    feedback = if (templates.isEmpty()) "No saved templates." else "Templates loaded."
                                } catch (e: Exception) { feedback = e.message ?: "Could not load templates." }
                                finally { busy = false }
                            }
                        }) { Text("Load saved templates", fontSize = 10.sp) }
                        if (templates.isNotEmpty()) {
                            Chips(templates, selectedTemplate) { selectedTemplate = it }
                            OutlinedButton(enabled = !busy && selectedTemplate.isNotBlank(), onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val item = withContext(Dispatchers.IO) { api.announcementTemplate(selectedTemplate) }
                                        subject = item.optString("subject")
                                        announcement = item.optString("message")
                                        templateName = item.optString("name")
                                        feedback = "Template loaded into the editor."
                                    } catch (e: Exception) { feedback = e.message ?: "Template load failed." }
                                    finally { busy = false }
                                }
                            }) { Text("Use selected template", fontSize = 10.sp) }
                        }
                    }
                    "Invites" -> {
                        Text("Invitation management", color = Color.White, fontSize = 13.sp)
                        if (invites.isEmpty()) Text("No invitations loaded.", color = Dim, fontSize = 10.sp)
                        else {
                            Chips(invites.map { it.optString("code") }, selectedInvite) { selectedInvite = it }
                            val inv = invites.firstOrNull { it.optString("code") == selectedInvite }
                            if (inv != null) {
                                Text("Expires: " + inv.optLong("valid_till").takeIf { it > 0 }?.let {
                                    DateFormat.getDateTimeInstance().format(Date(if (it > 1000000000000L) it else it * 1000L))
                                }.orEmpty(), color = Dim, fontSize = 9.sp)
                                Text("Remaining uses: ${inv.optInt("remaining_uses", 1)}", color = Dim, fontSize = 9.sp)
                                Field(inviteLabel, { inviteLabel = it }, "Invite label")
                                Field(inviteUserLabel, { inviteUserLabel = it }, "Label applied to new users")
                                Field(inviteRecipient, { inviteRecipient = it }, "Send to email / Discord username")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(enabled = !busy && selectedInvite.isNotBlank(), onClick = {
                                        execute("Invite updated") { api.editInvite(selectedInvite, inviteLabel, inviteUserLabel, selectedProfile) }
                                    }) { Text("Save changes", fontSize = 10.sp) }
                                    OutlinedButton(enabled = !busy && selectedInvite.isNotBlank() && inviteRecipient.isNotBlank(), onClick = {
                                        execute("Invite sent") { api.sendInvite(selectedInvite, inviteRecipient.trim()) }
                                    }) { Text("Send invite", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                    "Backups" -> {
                        Text("JFA-GO database backups", color = Color.White, fontSize = 13.sp)
                        Text("These are server-side database backups, separate from Allerium's connection-profile JSON backup. Restoring a backup restarts JFA-GO.", color = Dim, fontSize = 10.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                execute("Database backup created") {
                                    api.createServerBackup()
                                    backups = api.serverBackups()
                                }
                            }) { Text("Create backup", fontSize = 10.sp) }
                            OutlinedButton(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    try { backups = withContext(Dispatchers.IO) { api.serverBackups() }; feedback = "Backup list refreshed." }
                                    catch (e: Exception) { feedback = e.message ?: "Could not list backups." }
                                    finally { busy = false }
                                }
                            }) { Text("Refresh list", fontSize = 10.sp) }
                            OutlinedButton(enabled = !busy, onClick = {
                                fun openUploadPicker() {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                                    hostActivity?.launchFilePicker(4202, intent, ::handleBackupUpload)
                                        ?: run { feedback = "Main activity is unavailable." }
                                }
                                if (requireBiometricRestore) {
                                    showBiometricPrompt(context, "Confirm backup restore",
                                        "Verify before uploading and restoring a server database",
                                        onSuccess = ::openUploadPicker,
                                        onPasswordFallback = { feedback = "Restore cancelled. Biometric verification is required." },
                                        onError = { feedback = "Restore cancelled: $it" },
                                        negativeButtonText = "Cancel")
                                } else openUploadPicker()
                            }) { Text("Upload & restore", fontSize = 10.sp) }
                        }
                        backups.forEach { backup ->
                            val name = backup.optString("name")
                            JCard {
                                Text(name, color = Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("Date: " + backup.optLong("date").takeIf { it > 0 }?.let {
                                    DateFormat.getDateTimeInstance().format(Date(it * 1000L))
                                }.orEmpty() + " · Size: " + backup.optString("size"), color = Dim, fontSize = 9.sp)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(enabled = !busy && name.isNotBlank(), onClick = {
                                        pendingDownloadName = name
                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, name)
                                        hostActivity?.launchFilePicker(4201, intent, ::handleBackupDownload)
                                            ?: run { feedback = "Main activity is unavailable." }
                                    }) { Text("Download", fontSize = 10.sp) }
                                    OutlinedButton(enabled = !busy && name.isNotBlank(), onClick = { restoreTarget = name }) {
                                        Text("Restore", color = Color(0xFFFFC36B), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        Text("Server configuration", color = Color.White, fontSize = 13.sp)
                        Text("JFA-GO accepts configuration updates by section/key. Use exact names from the server's config; restart-sensitive changes should be handled in JFA-GO itself.", color = Dim, fontSize = 10.sp)
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try { config = withContext(Dispatchers.IO) { api.config() }; feedback = "Configuration loaded." }
                                catch (e: Exception) { feedback = e.message ?: "Configuration load failed." }
                                finally { busy = false }
                            }
                        }) { Text("Load server configuration", fontSize = 10.sp) }
                        config?.let { cfg ->
                            Text("Configuration snapshot", color = Cyan, fontSize = 10.sp)
                            Text(cfg.toString(2).take(5000), color = Dim, fontSize = 8.sp)
                            Divider(color = Line)
                            Field(configSection, { configSection = it }, "Section (e.g. email)")
                            Field(configKey, { configKey = it }, "Setting key")
                            Field(configValue, { configValue = it }, "New value (string)")
                            OutlinedButton(enabled = !busy && configSection.isNotBlank() && configKey.isNotBlank(), onClick = {
                                execute("Configuration updated") { api.setConfig(configSection.trim(), configKey.trim(), configValue) }
                            }) { Text("Save setting", fontSize = 10.sp) }
                        }
                        Text("Configuration edits are applied to the server. Keep a current JFA-GO backup before changing settings.", color = Color(0xFFFFC36B), fontSize = 9.sp)
                    }
                    "Integrations" -> {
                        Text("Jellyseerr profile integration", color = Color.White, fontSize = 13.sp)
                        Text("JFA-GO can store a Jellyseerr user and notification template inside an existing user profile. This requires Jellyseerr to be enabled and configured on the server.", color = Dim, fontSize = 10.sp)
                        if (profiles.isNotEmpty()) Chips(profiles, selectedProfile) { selectedProfile = it }
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try { jellyseerrUsers = withContext(Dispatchers.IO) { api.jellyseerrUsers() }; feedback = "Jellyseerr users loaded." }
                                catch (e: Exception) { feedback = e.message ?: "Jellyseerr is unavailable or not enabled." }
                                finally { busy = false }
                            }
                        }) { Text("Load Jellyseerr users", fontSize = 10.sp) }
                        jellyseerrUsers.forEach { user ->
                            val id = user.optString("id")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedJellyseerrUser == id, onClick = { selectedJellyseerrUser = id })
                                Text(user.optString("name"), color = Color.White, fontSize = 10.sp)
                            }
                        }
                        OutlinedButton(enabled = !busy && selectedProfile.isNotBlank() && selectedJellyseerrUser.isNotBlank(), onClick = {
                            execute("Jellyseerr profile saved") { api.setJellyseerrProfile(selectedProfile, selectedJellyseerrUser) }
                        }) { Text("Save Jellyseerr template to profile", fontSize = 10.sp) }
                        OutlinedButton(enabled = !busy && selectedProfile.isNotBlank(), onClick = {
                            execute("Jellyseerr template removed") { api.removeJellyseerrProfile(selectedProfile) }
                        }) { Text("Remove Jellyseerr template", fontSize = 10.sp) }
                        Divider(color = Line)
                        Text("Webhooks", color = Color.White, fontSize = 12.sp)
                        Text("JFA-GO webhook destinations are configured under Settings > Webhooks. The API config snapshot above can be used to inspect available server settings; this client does not invent webhook routes.", color = Dim, fontSize = 9.sp)
                    }
                }
                if (feedback.isNotBlank()) {
                    Divider(color = Line)
                    Text(feedback, color = if (feedback.contains("failed", true) || feedback.contains("error", true)) Color(0xFFFF8585) else Cyan, fontSize = 10.sp)
                }
            }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardC,
            title = { Text("Delete selected users?", color = Color.White) },
            text = { Text("This permanently deletes ${selectedUsers.size} Jellyfin account(s). This cannot be undone.", color = Dim) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val idsToDelete = selectedUsers.toList()
                    confirmDelete = false
                    selectedUsers.clear()
                    execute("Selected users deleted") { api.bulkDeleteUsers(idsToDelete) }
                }) { Text("Delete permanently", color = Color(0xFFFF8585)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    if (restoreTarget.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { restoreTarget = "" },
            containerColor = CardC,
            title = { Text("Restore server database?", color = Color.White) },
            text = { Text("JFA-GO will restore '$restoreTarget' and restart its service. The admin session may disconnect temporarily. Continue only if you are sure this is the intended backup.", color = Dim) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val name = restoreTarget
                    restoreTarget = ""
                    fun submitRestore() {
                        execute("Restore request submitted") { api.restoreServerBackup(name) }
                    }
                    if (requireBiometricRestore) {
                        showBiometricPrompt(context, "Confirm database restore",
                            "Verify before restoring '$name' and restarting JFA-GO",
                            onSuccess = ::submitRestore,
                            onPasswordFallback = { feedback = "Restore cancelled. Biometric verification is required." },
                            onError = { feedback = "Restore cancelled: $it" },
                            negativeButtonText = "Cancel")
                    } else submitRestore()
                }) { Text("Restore and restart", color = Color(0xFFFFC36B)) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = "" }) { Text("Cancel") } }
        )
    }
}
