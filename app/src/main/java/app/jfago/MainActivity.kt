package app.jfago

import android.content.*
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.PrintWriter
import java.io.StringWriter
import java.text.DateFormat
import java.util.Date

// Allerium visual system: ALLERION-inspired surfaces and motion, with Allerium's cyan/purple identity.
val Bg = Color(0xFF080B16)
val CardC = Color(0xFF11182A)
val Line = Color(0xFF26334D)
val Red = Color(0xFF9B72FF)
val Cyan = Color(0xFF39D5F2)
val Dim = Color(0xFF9AA8C3)
val Green = Color(0xFF43D9A3)
val PillC = Color(0xFF1C2740)

class MainActivity : FragmentActivity() {
    private val filePickerCallbacks = mutableMapOf<Int, (android.net.Uri?) -> Unit>()

    fun launchFilePicker(requestCode: Int, intent: Intent, onResult: (android.net.Uri?) -> Unit) {
        require(requestCode in 1..0xFFFF) { "File picker request code must fit in 16 bits." }
        filePickerCallbacks[requestCode] = onResult
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            filePickerCallbacks.remove(requestCode)
            throw e
        }
    }

    @Deprecated("Uses a 16-bit request code for FragmentActivity compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val callback = filePickerCallbacks.remove(requestCode) ?: return
        callback(if (resultCode == RESULT_OK) data?.data else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashDiagnostics(applicationContext)
        super.onCreate(savedInstanceState)
        window.statusBarColor = Bg.toArgb(); window.navigationBarColor = Bg.toArgb()
        val cs = darkColorScheme(primary = Red, background = Bg, surface = Bg, onSurface = Color.White,
            surfaceVariant = CardC, onPrimary = Color.White, secondaryContainer = PillC)
        setContent { MaterialTheme(colorScheme = cs) { Surface(Modifier.fillMaxSize(), color = Bg) { Root() } } }
    }
}

data class Srv(val name: String, val url: String, val user: String)
private data class CenterAlert(
    val id: String,
    val title: String,
    val detail: String,
    val timestamp: Long,
    val category: String,
    val warning: Boolean = false
)

private fun installCrashDiagnostics(context: Context) {
    val app = context.applicationContext
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        try {
            val trace = StringWriter().also { writer ->
                error.printStackTrace(PrintWriter(writer))
            }.toString()
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(app.packageName, 0)
            }
            val version = packageInfo.versionName ?: "unknown"
            val report = buildString {
                appendLine("Allerium crash report")
                appendLine("Time: ${DateFormat.getDateTimeInstance().format(Date())}")
                appendLine("App: $version (${packageInfo.longVersionCode})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(trace)
            }
            app.getSharedPreferences("allerium_diagnostics", Context.MODE_PRIVATE)
                .edit().putString("last_crash", report).putLong("last_crash_at", System.currentTimeMillis()).commit()
        } catch (_: Exception) {
            // Never let diagnostic persistence interfere with the original crash handler.
        }
        previousHandler?.uncaughtException(thread, error)
    }
}

internal fun fragmentActivity(context: Context): FragmentActivity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

private fun showBiometricPrompt(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onPasswordFallback: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = fragmentActivity(context)
    if (activity == null) {
        onError("Biometric authentication is unavailable in this screen.")
        return
    }
    val capability = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (capability != BiometricManager.BIOMETRIC_SUCCESS) {
        onError(when (capability) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Enroll a supported fingerprint or face in Android settings first."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no supported strong biometric hardware."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is temporarily unavailable."
            else -> "This device does not support strong biometric sign-in."
        })
        return
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) onPasswordFallback()
                else onError(errString.toString())
            }
        })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Use password")
        .build()
    prompt.authenticate(info)
}

private fun credentialPrefs(ctx: Context): android.content.SharedPreferences {
    val masterKey = androidx.security.crypto.MasterKey.Builder(ctx)
        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
    return androidx.security.crypto.EncryptedSharedPreferences.create(
        ctx, "allerium_credentials", masterKey,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

@Composable fun JCard(m: Modifier = Modifier, c: @Composable ColumnScope.() -> Unit) =
    Column(m.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(CardC)
        .border(1.dp, Line.copy(alpha = 0.82f), RoundedCornerShape(22.dp))
        .animateContentSize(animationSpec = tween(260)).padding(16.dp), content = c)

@Composable fun AnimatedServerCard(m: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "serverCardGlow")
    val phase by transition.animateFloat(0f, 1f, animationSpec = infiniteRepeatable(tween(5200), repeatMode = RepeatMode.Reverse), label = "serverCardPhase")
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(CardC, CardC.copy(alpha = 0.96f), Color(0xFF14243A)),
        start = Offset(phase * 260f, 0f), end = Offset(620f, 520f))
    Column(m.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brush)
        .border(1.dp, Cyan.copy(alpha = 0.10f + phase * 0.06f), RoundedCornerShape(24.dp))
        .animateContentSize(animationSpec = tween(280)).padding(18.dp), content = content)
}

@Composable fun StatCard(label: String, value: Int, tint: Color, glyph: ImageVector, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "stat-$label")
    val phase by transition.animateFloat(0f, 1f, animationSpec = infiniteRepeatable(tween(3600), repeatMode = RepeatMode.Reverse), label = "statGlow-$label")
    val count by animateIntAsState(value, animationSpec = tween(700), label = "statCount-$label")
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(tint.copy(alpha = 0.15f + phase * 0.09f), CardC, tint.copy(alpha = 0.07f + phase * 0.06f)),
        start = Offset(phase * 240f, 0f), end = Offset(400f, 360f)
    )
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(18.dp))
            .background(brush)
            .border(1.dp, tint.copy(alpha = 0.16f + phase * 0.12f), RoundedCornerShape(18.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.16f + phase * 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Text("$count", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 8.sp, color = Dim, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable fun Bar(f: Float, m: Modifier = Modifier) =
    Box(m.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Line)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.03f, 1f)).fillMaxHeight()
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Red, Cyan))))
    }

@Composable fun Pill(t: String, dot: Color? = null) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(PillC).border(1.dp, Line, RoundedCornerShape(50))
        .padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(6.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(5.dp)) }
        Text(t, fontSize = 8.sp, color = Color(0xFFE4EAF8), fontWeight = FontWeight.Medium)
    }

@Composable fun RedBtn(t: String, enabled: Boolean = true, icon: ImageVector? = null, on: () -> Unit) =
    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
        .background(if (enabled) Red else Line)
        .clickable(enabled = enabled, onClick = on).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(9.dp)) }
        Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }

@Composable fun Field(v: String, on: (String) -> Unit, label: String, pw: Boolean = false) =
    OutlinedTextField(v, on, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(16.dp), visualTransformation = if (pw) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, focusedLabelColor = Red, unfocusedBorderColor = Line, cursorColor = Red))

@Composable fun Header(sub: String, actions: @Composable () -> Unit = {
    Box(Modifier.size(7.dp).clip(CircleShape).background(Cyan))
}) = Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.allerium_mark), null, Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("ALLERIUM", color = Color.White, fontSize = 15.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
            Text("ALLERIA LABS", color = Dim, fontSize = 8.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Medium)
        }
        actions()
    }
    Spacer(Modifier.height(8.dp))
    Text(sub, color = Dim, fontSize = 10.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = .75f)))
}

@Composable fun Chips(opts: List<String>, sel: String, on: (String) -> Unit) {
    if (opts.isEmpty()) return
    Text("Profile", color = Dim, fontSize = 10.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("") + opts).forEach { o -> Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel == o) Red else PillC)
            .clickable { on(o) }.padding(horizontal = 14.dp, vertical = 8.dp)) { Text(o.ifEmpty { "Default" }, fontSize = 10.sp) } }
    }
}

@Composable fun Root() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("jfa", Context.MODE_PRIVATE) }
    fun load(): List<Srv> { val a = JSONArray(prefs.getString("servers", "[]")); return (0 until a.length()).map {
        val o = a.getJSONObject(it); Srv(o.getString("name"), o.getString("url"), o.getString("user")) } }
    var servers by remember { mutableStateOf(load()) }
    fun save(l: List<Srv>) { servers = l; prefs.edit().putString("servers", JSONArray(l.map {
        JSONObject().put("name", it.name).put("url", it.url).put("user", it.user) }).toString()).apply() }
    var session by remember { mutableStateOf<Pair<Srv, Api>?>(null) }
    session?.let { (s, api) -> Home(s, api, servers, ::save) { session = null }; return }
    ServerList(servers, ::save) { s, a -> session = s to a }
}

@Composable fun ServerList(servers: List<Srv>, save: (List<Srv>) -> Unit, open: (Srv, Api) -> Unit) {
    var editing by remember { mutableStateOf<Srv?>(null) }
    var adding by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf<Srv?>(null) }
    val online = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val credentials = remember(context) { credentialPrefs(context) }
    val biometricPrefs = remember(context) { context.getSharedPreferences("allerium_biometric", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var authError by remember { mutableStateOf("") }
    var authenticating by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf("") }
    var backupMenuExpanded by remember { mutableStateOf(false) }
    fun handleExportBackup(uri: android.net.Uri?) {
        if (uri != null) {
            try {
                val backup = JSONObject()
                    .put("format", "allerium-server-backup")
                    .put("version", 1)
                    .put("createdAt", System.currentTimeMillis())
                    .put("servers", JSONArray(servers.map { server ->
                        JSONObject().put("name", server.name).put("url", server.url).put("user", server.user)
                    }))
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not open the selected backup destination.")
                stream.use { it.write(backup.toString(2).toByteArray(Charsets.UTF_8)) }
                backupMessage = "Backup saved. ${servers.size} server profile(s) exported. Passwords and biometric settings were not included."
            } catch (e: Exception) {
                backupMessage = "Backup failed: ${e.message ?: "Unable to write file"}"
            }
        }
    }
    fun handleImportBackup(uri: android.net.Uri?) {
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not read the selected backup file.")
                val raw = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val backup = JSONObject(raw)
                require(backup.optString("format") == "allerium-server-backup") { "This is not an Allerium server backup." }
                require(backup.optInt("version", -1) == 1) { "This backup version is not supported." }
                val saved = backup.optJSONArray("servers") ?: throw IllegalArgumentException("The backup has no server list.")
                val merged = servers.toMutableList()
                var added = 0
                for (i in 0 until saved.length()) {
                    val item = saved.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val url = item.optString("url").trim().trimEnd('/')
                    val user = item.optString("user").trim()
                    if (name.isBlank() || url.isBlank() || user.isBlank()) continue
                    if (merged.any { it.url.equals(url, ignoreCase = true) }) continue
                    merged.add(Srv(name, url, user))
                    added++
                }
                if (added > 0) save(merged)
                backupMessage = "Restore complete. Added $added server profile(s); existing profiles were kept. Sign in again on restored servers. Passwords and biometric settings must be set up on this device."
            } catch (e: Exception) {
                backupMessage = "Restore failed: ${e.message ?: "Invalid backup file"}"
            }
        }
    }
    fun launchServerBackupExport() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "Allerium-server-backup.json")
            (fragmentActivity(context) as? MainActivity)?.launchFilePicker(4101, intent, ::handleExportBackup)
                ?: throw IllegalStateException("Main activity is unavailable.")
        } catch (e: Exception) {
            backupMessage = "Could not open Android save picker: " + (e.message ?: e.javaClass.simpleName)
        }
    }
    fun launchServerBackupImport() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
            (fragmentActivity(context) as? MainActivity)?.launchFilePicker(4102, intent, ::handleImportBackup)
                ?: throw IllegalStateException("Main activity is unavailable.")
        } catch (e: Exception) {
            backupMessage = "Could not open Android file picker: " + (e.message ?: e.javaClass.simpleName)
        }
    }
    fun connectWithStoredPassword(s: Srv, password: String) {
        authenticating = true
        scope.launch {
            try {
                val api = Api(s.url)
                withContext(Dispatchers.IO) { api.login(s.user, password) }
                authError = ""
                open(s, api)
            } catch (e: Exception) {
                authError = e.message ?: "Saved sign-in failed. Please update the password."
                login = s
            } finally { authenticating = false }
        }
    }
    fun selectServer(s: Srv) {
        val stored = credentials.getString(s.url, "").orEmpty()
        if (stored.isBlank()) { login = s; return }
        if (biometricPrefs.getBoolean(s.url, false)) {
            showBiometricPrompt(context, "Unlock ${s.name}", "Verify to use your saved JFA-GO credentials",
                onSuccess = { connectWithStoredPassword(s, stored) },
                onPasswordFallback = { login = s },
                onError = { authError = it; login = s })
        } else {
            connectWithStoredPassword(s, stored)
        }
    }
    LaunchedEffect(servers) { servers.forEach { s -> launch(Dispatchers.IO) {
        online[s.url] = try { (URL(s.url).openConnection() as HttpURLConnection).run { connectTimeout = 4000; readTimeout = 4000; responseCode; true } } catch (e: Exception) { false } } } }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Header("Your secure JFA-GO workspace") {
                Box {
                    IconButton(onClick = { backupMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Backup options", tint = Dim)
                    }
                    DropdownMenu(
                        expanded = backupMenuExpanded,
                        onDismissRequest = { backupMenuExpanded = false },
                        modifier = Modifier.background(CardC)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create server backup", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; launchServerBackupExport() }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore server backup", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Restore, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; launchServerBackupImport() }
                        )
                    }
                }
            }
        }
        if (authenticating) item { Text("Connecting securely…", color = Cyan, fontSize = 11.sp) }
        if (authError.isNotBlank()) item { Text(authError, color = Color(0xFFFF8A8A), fontSize = 11.sp) }
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                Text("Welcome back", color = Dim, fontSize = 10.sp, letterSpacing = 1.sp)
                Text("Your servers", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Manage your JFA-GO workspace.", color = Dim, fontSize = 12.sp)
            }
        }
        if (servers.isEmpty()) item {
            JCard {
                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Dns, null, tint = Cyan, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No servers connected", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Add your JFA-GO server to get started.", color = Dim, fontSize = 10.sp)
                    }
                }
            }
        }
        items(servers, key = { it.url }) { s ->
            AnimatedVisibility(visible = true, enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 5 }) {
            AnimatedServerCard(Modifier.clickable(enabled = !authenticating) { selectServer(s) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                         .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Red, Cyan))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Dns, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(s.url, color = Dim, fontSize = 11.sp, maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        val o = online[s.url]
                        Pill(if (o == null) "Checking connection" else if (o) "Server online" else "Unavailable",
                            if (o == true) Green else if (o == false) Color(0xFFFF8A8A) else Cyan)
                    }
                    IconButton({ editing = s }) { Icon(Icons.Default.MoreVert, "Server options", tint = Dim) }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Open server workspace", color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, tint = Red, modifier = Modifier.size(18.dp))
                }
            }
            }
        }
    }
    Row(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)
        .clip(RoundedCornerShape(50)).background(Red)
        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
        .clickable { adding = true }.padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Add, contentDescription = "Add server", tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add server", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
    }
    if (backupMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { backupMessage = "" },
            containerColor = CardC,
            title = { Text("Server backup", color = Color.White) },
            text = { Text(backupMessage, color = Dim, fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = { backupMessage = "" }) { Text("Done", color = Red) } }
        )
    }
    if (adding || editing != null) {
        val old = editing
        var n by remember { mutableStateOf(old?.name ?: "") }; var u by remember { mutableStateOf(old?.url ?: "https://") }
        var us by remember { mutableStateOf(old?.user ?: "") }
        var pw by remember { mutableStateOf(if (old == null) "" else credentials.getString(old.url, "").orEmpty()) }
        var biometricEnabled by remember(old?.url) { mutableStateOf(old?.let { biometricPrefs.getBoolean(it.url, false) } ?: false) }
        var biometricNotice by remember { mutableStateOf("") }
        val close = { adding = false; editing = null }
        AlertDialog(close, containerColor = CardC, title = { Text(if (old == null) "Add server" else "Edit server") },
            text = { Column {
                Field(n, { n = it }, "Server name")
                Field(u, { u = it.trim() }, "Server URL")
                Field(us, { us = it }, "Username")
                Field(pw, { pw = it }, "Password", true)
                Text("Password is stored encrypted on this device and used for automatic sign-in.", color = Dim, fontSize = 10.sp)
                if (old != null && credentials.getString(old.url, "").orEmpty().isNotBlank()) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Biometric sign-in", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Require strong fingerprint or face verification before using the saved password.",
                                color = Dim, fontSize = 9.sp)
                        }
                        Switch(checked = biometricEnabled, onCheckedChange = { enable ->
                            if (!enable) {
                                biometricEnabled = false
                                biometricNotice = ""
                            } else {
                                showBiometricPrompt(context, "Enable biometric sign-in",
                                    "Verify your identity to protect this server's saved credentials",
                                    onSuccess = { biometricEnabled = true; biometricNotice = "Biometric sign-in enabled." },
                                    onPasswordFallback = { biometricNotice = "Use biometric verification to enable this feature." },
                                    onError = { biometricNotice = it })
                            }
                        })
                    }
                    if (biometricNotice.isNotBlank()) Text(biometricNotice, color = Cyan, fontSize = 10.sp)
                    Text("Only Android strong biometrics are accepted. Some face unlock systems may not qualify. You can always choose password sign-in.",
                        color = Dim, fontSize = 9.sp)
                }
            } },
            confirmButton = { TextButton(enabled = n.isNotBlank() && u.isNotBlank() && us.isNotBlank() && (pw.isNotBlank() || old != null),
                onClick = {
                    val normalized = u.trim().trimEnd('/')
                    val s = Srv(n.trim(), normalized, us.trim())
                    if (old != null && old.url != normalized) {
                        credentials.edit().remove(old.url).apply()
                        biometricPrefs.edit().remove(old.url).apply()
                    }
                    if (pw.isNotBlank()) credentials.edit().putString(normalized, pw).apply()
                    if (old != null) biometricPrefs.edit().putBoolean(normalized, biometricEnabled).apply()
                    save(if (old == null) servers + s else servers.map { if (it == old) s else it })
                    close()
                }) { Text("Save", color = Red) } },
            dismissButton = { Row { if (old != null) TextButton({ credentials.edit().remove(old.url).apply(); biometricPrefs.edit().remove(old.url).apply(); save(servers - old); close() }) { Text("Delete", color = Red) }; TextButton(close) { Text("Cancel") } } })
    }
    login?.let { s ->
        var pw by remember { mutableStateOf(credentials.getString(s.url, "").orEmpty()) }; var err by remember { mutableStateOf(authError) }; var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        AlertDialog({ login = null }, containerColor = CardC, title = { Text(s.name) },
            text = { Column { Field(pw, { pw = it }, "Password for ${s.user}", true); if (err.isNotEmpty()) Text(err, color = Red) } },
            confirmButton = { TextButton({
                busy = true; err = ""
                scope.launch { try {
                    val a = Api(s.url)
                    withContext(Dispatchers.IO) { a.login(s.user, pw) }
                    credentials.edit().putString(s.url, pw).apply()
                    authError = ""; login = null; open(s, a)
                } catch (e: Exception) { err = e.message ?: "Failed" }
                busy = false }
            }, enabled = !busy) { Text(if (busy) "…" else "Sign in", color = Red) } },
            dismissButton = { TextButton({ login = null }) { Text("Cancel") } })
    }
}

@Composable fun Home(s: Srv, api: Api, servers: List<Srv>, saveServers: (List<Srv>) -> Unit, back: () -> Unit) {
    BackHandler(onBack = back)
    var tab by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf(listOf<JSONObject>()) }
    var invites by remember { mutableStateOf(listOf<JSONObject>()) }
    var acts by remember { mutableStateOf(listOf<JSONObject>()) }
    var profs by remember { mutableStateOf(listOf<String>()) }
    var q by remember { mutableStateOf("") }
    var userStatus by remember { mutableStateOf("All users") }
    var userSort by remember { mutableStateOf("Name") }
    var activityQuery by remember { mutableStateOf("") }
    var activityWindow by remember { mutableStateOf("All time") }
    var activityType by remember { mutableStateOf("All types") }
    var activitySort by remember { mutableStateOf("Newest") }
    var expandedActivity by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var featureDialog by remember { mutableStateOf("") }
    var notificationFilter by remember { mutableStateOf("All") }
    val notificationPrefs = remember(ctx, s.url) {
        ctx.getSharedPreferences("allerium_notifications", Context.MODE_PRIVATE)
    }
    var readNotificationIds by remember(s.url) {
        mutableStateOf(notificationPrefs.getStringSet("read_${s.url}", emptySet())?.toSet() ?: emptySet())
    }
    fun markNotificationRead(id: String) {
        readNotificationIds = readNotificationIds + id
        notificationPrefs.edit().putStringSet("read_${s.url}", readNotificationIds).apply()
    }
    fun markAllNotificationsRead(ids: List<String>) {
        readNotificationIds = readNotificationIds + ids
        notificationPrefs.edit().putStringSet("read_${s.url}", readNotificationIds).apply()
    }
    var advancedModulesOpen by remember { mutableStateOf(false) }
    var diagnosticChecks by remember { mutableStateOf<List<DiagnosticCheck>>(emptyList()) }
    var diagnosticBusy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var backupMenuExpanded by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf("") }; var target by remember { mutableStateOf<JSONObject?>(null) }
    var resetLink by remember { mutableStateOf("") }
    var resetMessage by remember { mutableStateOf("") }
    var resetBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(api, s.url) {
        val auditStore = ctx.getSharedPreferences("allerium_operations", Context.MODE_PRIVATE)
        api.auditSink = { method, endpoint, status, success ->
            synchronized(auditStore) {
                val key = "audit_${s.url}"
                val entries = JSONArray(auditStore.getString(key, "[]") ?: "[]")
                entries.put(JSONObject().put("time", System.currentTimeMillis()).put("method", method)
                    .put("path", endpoint).put("status", status).put("success", success))
                while (entries.length() > 500) entries.remove(0)
                auditStore.edit().putString(key, entries.toString()).apply()
            }
        }
        onDispose { api.auditSink = null }
    }
    fun refresh() = scope.launch { try { withContext(Dispatchers.IO) { users = api.users(); invites = api.invites(); acts = try { api.activity() } catch (e: Exception) { acts }; profs = try { api.profiles() } catch (e: Exception) { profs } }; msg = "" } catch (e: Exception) { msg = e.message ?: "Error" } }
    fun act(b: () -> Unit) = scope.launch { try { withContext(Dispatchers.IO) { b() } } catch (e: Exception) { msg = e.message ?: "Error" }; refresh() }
    fun exportServerBackup(uri: android.net.Uri?) {
        if (uri == null) return
        try {
            val backup = JSONObject()
                .put("format", "allerium-server-backup")
                .put("version", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("servers", JSONArray(servers.map { server ->
                    JSONObject().put("name", server.name).put("url", server.url).put("user", server.user)
                }))
            val stream = ctx.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open the selected backup destination.")
            stream.use { it.write(backup.toString(2).toByteArray(Charsets.UTF_8)) }
            backupMessage = "Backup saved. ${servers.size} server profile(s) exported. Passwords and biometric settings were not included."
        } catch (e: Exception) {
            backupMessage = "Backup failed: ${e.message ?: "Unable to write file"}"
        }
    }
    fun importServerBackup(uri: android.net.Uri?) {
        if (uri == null) return
        try {
            val stream = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not read the selected backup file.")
            val backup = JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            require(backup.optString("format") == "allerium-server-backup") { "This is not an Allerium server backup." }
            require(backup.optInt("version", -1) == 1) { "This backup version is not supported." }
            val saved = backup.optJSONArray("servers") ?: throw IllegalArgumentException("The backup has no server list.")
            val merged = servers.toMutableList()
            var added = 0
            for (i in 0 until saved.length()) {
                val item = saved.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val url = item.optString("url").trim().trimEnd('/')
                val user = item.optString("user").trim()
                if (name.isBlank() || url.isBlank() || user.isBlank()) continue
                if (merged.any { it.url.equals(url, ignoreCase = true) }) continue
                merged.add(Srv(name, url, user))
                added++
            }
            if (added > 0) saveServers(merged)
            backupMessage = "Restore complete. Added $added server profile(s); existing profiles were kept. Sign in again on restored servers. Passwords and biometric settings must be set up on this device."
        } catch (e: Exception) {
            backupMessage = "Restore failed: ${e.message ?: "Invalid backup file"}"
        }
    }
    fun launchBackupExport() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "Allerium-server-backup.json")
            (fragmentActivity(ctx) as? MainActivity)?.launchFilePicker(4101, intent, ::exportServerBackup)
                ?: throw IllegalStateException("Main activity is unavailable.")
        } catch (e: Exception) {
            backupMessage = "Could not open Android save picker: " + (e.message ?: e.javaClass.simpleName)
        }
    }
    fun launchBackupImport() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            (fragmentActivity(ctx) as? MainActivity)?.launchFilePicker(4102, intent, ::importServerBackup)
                ?: throw IllegalStateException("Main activity is unavailable.")
        } catch (e: Exception) {
            backupMessage = "Could not open Android file picker: " + (e.message ?: e.javaClass.simpleName)
        }
    }
    LaunchedEffect(Unit) { refresh() }
    if (backupMessage.isNotBlank()) {
        AlertDialog(onDismissRequest = { backupMessage = "" }, containerColor = CardC,
            title = { Text("Server backup", color = Color.White) },
            text = { Text(backupMessage, color = Dim, fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = { backupMessage = "" }) { Text("Done", color = Red) } })
    }
    val off = users.count { it.optBoolean("disabled") }
    val shown = users.filter { u ->
        val queryMatch = q.isBlank() || u.optString("name").contains(q, true) || u.optString("email").contains(q, true)
        val statusMatch = when (userStatus) {
            "Active" -> !u.optBoolean("disabled")
            "Disabled" -> u.optBoolean("disabled")
            "Admins" -> u.optBoolean("admin")
            else -> true
        }
        queryMatch && statusMatch
    }.let { list -> when (userSort) {
        "Expiration" -> list.sortedBy { it.optLong("expiry") }
        else -> list.sortedBy { it.optString("name").lowercase() }
    } }
    val nowMillis = System.currentTimeMillis()
    val nowSeconds = nowMillis / 1000L
    val centerAlerts = buildList {
        users.forEach { user ->
            val expiry = user.optLong("expiry")
            if (expiry > 0L) {
                val expiryMillis = if (expiry > 1_000_000_000_000L) expiry else expiry * 1000L
                val daysLeft = (expiryMillis - nowMillis) / 86_400_000L
                if (daysLeft <= 30L) {
                    val expired = expiryMillis < nowMillis
                    val username = user.optString("name").ifBlank { "User account" }
                    add(CenterAlert(
                        id = "expiry:${user.optString("id")}:$expiry",
                        title = if (expired) "Account expired" else "Account expiring soon",
                        detail = if (expired) "$username has passed its expiration date."
                            else "$username expires in ${daysLeft.coerceAtLeast(0)} day(s).",
                        timestamp = expiryMillis,
                        category = "Accounts",
                        warning = true
                    ))
                }
            }
        }
        invites.forEach { invite ->
            val expiry = invite.optLong("valid_till")
            if (expiry > 0L) {
                val expiryMillis = if (expiry > 1_000_000_000_000L) expiry else expiry * 1000L
                val daysLeft = (expiryMillis - nowMillis) / 86_400_000L
                if (daysLeft <= 7L) {
                    val expired = expiryMillis < nowMillis
                    add(CenterAlert(
                        id = "invite:${invite.optString("code")}:$expiry",
                        title = if (expired) "Invitation expired" else "Invitation expiring soon",
                        detail = "Invitation ${invite.optString("code")} " +
                            if (expired) "has expired." else "expires in ${daysLeft.coerceAtLeast(0)} day(s).",
                        timestamp = expiryMillis,
                        category = "Accounts",
                        warning = true
                    ))
                }
            }
        }
        acts.forEach { event ->
            val rawTime = event.optLong("time")
            val eventMillis = if (rawTime > 1_000_000_000_000L) rawTime else rawTime * 1000L
            if (eventMillis > 0L && nowMillis - eventMillis <= 30L * 86_400_000L) {
                val type = event.optString("type").ifBlank { "Activity" }
                val detail = event.optString("message").ifBlank {
                    event.optString("description").ifBlank { event.optString("username") }
                }
                val stable = listOf(type, rawTime.toString(), event.optString("username"),
                    event.optString("value"), detail).joinToString("|")
                add(CenterAlert(
                    id = "activity:${stable.hashCode()}",
                    title = type.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    detail = detail.ifBlank { "A server activity event was recorded." },
                    timestamp = eventMillis,
                    category = "Activity"
                ))
            }
        }
    }.sortedByDescending { it.timestamp }
    val unreadNotificationCount = centerAlerts.count { it.id !in readNotificationIds }
    val activityTypes = acts.map { it.optString("type").ifBlank { "Activity" } }
        .distinct().sorted()
    val filteredActivities = acts.filter { item ->
        val type = item.optString("type").ifBlank { "Activity" }
        val searchText = type + " " + item.optString("username") + " " + item.optString("value") + " " + item.optString("message") + " " + item.optString("description")
        val rawTime = item.optLong("time")
        val eventTime = if (rawTime > 1000000000000L) rawTime / 1000L else rawTime
        val age = if (eventTime > 0L) nowSeconds - eventTime else Long.MAX_VALUE
        val timeMatch = when (activityWindow) {
            "24 hours" -> age in 0..86400L
            "7 days" -> age in 0..604800L
            "30 days" -> age in 0..2592000L
            else -> true
        }
        val typeMatch = activityType == "All types" || type == activityType
        timeMatch && typeMatch && (activityQuery.isBlank() || searchText.contains(activityQuery, true))
    }.let { list ->
        if (activitySort == "Oldest") list.sortedBy { it.optLong("time") }
        else list.sortedByDescending { it.optLong("time") }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header(s.name) {
                Box {
                    IconButton(onClick = { backupMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Server options", tint = Dim)
                    }
                    DropdownMenu(expanded = backupMenuExpanded, onDismissRequest = { backupMenuExpanded = false },
                        modifier = Modifier.background(CardC)) {
                        DropdownMenuItem(
                            text = { Text(if (unreadNotificationCount > 0) "Alerts · $unreadNotificationCount new" else "Alerts", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Notifications, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; featureDialog = "notifications" }
                        )
                        DropdownMenuItem(
                            text = { Text("Diagnostics", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; featureDialog = "diagnostics"; diagnosticChecks = emptyList() }
                        )
                        DropdownMenuItem(
                            text = { Text("Operations center", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Analytics, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; featureDialog = "operations" }
                        )
                        DropdownMenuItem(
                            text = { Text("Export workspace data", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; featureDialog = "export" }
                        )
                        DropdownMenuItem(
                            text = { Text("All tools", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Tune, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; advancedModulesOpen = true }
                        )
                        HorizontalDivider(color = Line)
                        DropdownMenuItem(
                            text = { Text("Create server backup", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Save, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; launchBackupExport() }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore server backup", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Restore, null, tint = Cyan) },
                            onClick = { backupMenuExpanded = false; launchBackupImport() }
                        )
                    }
                }
            }
        }
        if (tab < 3) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("TOTAL USERS", users.size, Red, Icons.Default.Groups, Modifier.weight(1f))
                StatCard("INVITATIONS", invites.size, Color(0xFFB8BBC0), Icons.Default.ConfirmationNumber, Modifier.weight(1f))
                StatCard("DISABLED", off, Color(0xFFFFB547), Icons.Default.Block, Modifier.weight(1f))
            }
        }
        if (tab < 3) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (tab == 0) "Directory" else if (tab == 1) "Invitations" else "Activity",
                        fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Manage your JFA-GO server", color = Dim, fontSize = 12.sp)
                }
                IconButton({ refresh() }, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PillC)) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Red)
                }
            }
        }
        if (tab == 3) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Control Center", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Insights, monitoring and security for ${s.name}", color = Dim, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("ACTIVE", users.count { !it.optBoolean("disabled") }, Green, Icons.Default.VerifiedUser, Modifier.weight(1f))
                    StatCard("EXPIRING", centerAlerts.count { it.category == "Accounts" && it.title.contains("soon") }, Color(0xFFFFB547), Icons.Default.Schedule, Modifier.weight(1f))
                    StatCard("ALERTS", unreadNotificationCount, Cyan, Icons.Default.Notifications, Modifier.weight(1f))
                }
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, null, tint = Cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Analytics & audit", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Account trends, API audit events and export.", color = Dim, fontSize = 10.sp)
                        }
                        IconButton(onClick = { featureDialog = "operations" }) { Icon(Icons.Default.ChevronRight, null, tint = Cyan) }
                    }
                    TextButton(onClick = { featureDialog = "operations" }) { Text("Open Control Center", color = Cyan) }
                }
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFFFB547), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Notification center", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("$unreadNotificationCount unread · account expiry and recent activity", color = Dim, fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = { featureDialog = "notifications" }) { Text("View notifications", color = Cyan) }
                }
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, tint = Cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Server diagnostics", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Authenticated endpoint checks and response times.", color = Dim, fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = { featureDialog = "diagnostics"; diagnosticChecks = emptyList() }) { Text("Run diagnostics", color = Cyan) }
                }
                JCard {
                    Text("Workspace data", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Export users and activity as CSV files.", color = Dim, fontSize = 10.sp)
                    TextButton(onClick = { featureDialog = "export" }) { Text("Export data", color = Cyan) }
                }
                RedBtn("Refresh server data", icon = Icons.Default.Refresh) { refresh() }
            }
        }
        if (tab == 4) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("All Tools", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Extended JFA-GO administration modules", color = Dim, fontSize = 11.sp)
                }
                listOf(
                    Triple("User management", "Bulk enable, disable, expiry, labels, email and referrals.", Icons.Default.Groups),
                    Triple("Messages", "Announcements and reusable message templates.", Icons.Default.MarkEmailRead),
                    Triple("Invitations", "Edit invite details, labels and recipients.", Icons.Default.ConfirmationNumber),
                    Triple("Backups", "Browse, download, upload and restore server backups.", Icons.Default.Backup),
                    Triple("Settings", "Inspect and manage supported JFA-GO configuration.", Icons.Default.Settings),
                    Triple("Integrations", "Manage available integration modules.", Icons.Default.Extension)
                ).forEach { item ->
                    JCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.third, null, tint = Cyan, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.first, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(item.second, color = Dim, fontSize = 10.sp)
                            }
                        }
                    }
                }
                RedBtn("Open all modules", icon = Icons.Default.Tune) { advancedModulesOpen = true }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = Red) }
        if (tab == 0) {
            item {
                Field(q, { q = it }, "Search users")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All users", "Active", "Disabled", "Admins").forEach { value ->
                        FilterChip(selected = userStatus == value, onClick = { userStatus = value }, label = { Text(value, fontSize = 9.sp) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort:", color = Dim, fontSize = 10.sp)
                    listOf("Name", "Expiration").forEach { value ->
                        TextButton({ userSort = value }) { Text(value, color = if (userSort == value) Cyan else Dim, fontSize = 10.sp) }
                    }
                }
            }
            items(shown) { u ->
                val dis = u.optBoolean("disabled"); val exp = u.optLong("expiry")
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Red, Color(0xFF8E080F)))),
                            contentAlignment = Alignment.Center) {
                            Text(u.optString("name").firstOrNull()?.uppercase() ?: "U", fontSize = 14.sp,
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.optString("name"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            val em = u.optString("email")
                            Text(em.ifEmpty { if (u.optBoolean("admin")) "Administrator" else "JFA-GO account" },
                                color = Dim, fontSize = 10.sp, maxLines = 1)
                        }
                        Pill(if (u.optBoolean("admin")) "ADMIN" else if (dis) "DISABLED" else "ACTIVE",
                            if (dis) Color(0xFFFF8A8A) else Green)
                    }
                    if (exp > 0) {
                        Spacer(Modifier.height(13.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Dim, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Expires " + DateFormat.getDateInstance().format(Date(if (exp > 1e12) exp else exp * 1000)),
                                color = Dim, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(!dis, { on -> act { api.setEnabled(u.getString("id"), on) } },
                            modifier = Modifier.scale(0.68f),
                            colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White, uncheckedTrackColor = Line))
                        Spacer(Modifier.weight(1f))
                        TextButton({ target = u; dlg = "reset-confirm"; resetLink = ""; resetMessage = "" }) {
                            Icon(Icons.Default.Lock, null, tint = Cyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Reset", color = Cyan, fontSize = 10.sp)
                        }
                        TextButton({ target = u; dlg = "expiry" }) {
                            Icon(Icons.Default.Schedule, null, tint = Dim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Expiry", color = Dim, fontSize = 10.sp)
                        }
                        IconButton({ target = u; dlg = "del" }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF8A8A)) }
                    }
                }
            }
        } else if (tab == 1) {
            item { RedBtn("Create invitation", icon = Icons.Default.AddLink) { dlg = "invite" } }
            items(invites) { i ->
                val code = i.optString("code")
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Red.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ConfirmationNumber, null, tint = Red)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(code, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Uses remaining · " + i.optString("remaining-uses").ifEmpty { "Unlimited" }, color = Dim, fontSize = 12.sp)
                        }
                        IconButton({ (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("invite", "${api.base}/invite/$code")) }) {
                            Icon(Icons.Default.ContentCopy, "Copy invite link", tint = Cyan)
                        }
                        IconButton({ act { api.deleteInvite(code) } }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF8A8A)) }
                    }
                }
            }
        }
        if (tab == 2) {
            item {
                Field(activityQuery, { activityQuery = it }, "Search events, users or details")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All time", "24 hours", "7 days", "30 days").forEach { value ->
                        FilterChip(selected = activityWindow == value, onClick = { activityWindow = value }, label = { Text(value, fontSize = 9.sp) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Event type", color = Dim, fontSize = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = activityType == "All types", onClick = { activityType = "All types" }, label = { Text("All", fontSize = 9.sp) })
                        }
                        items(activityTypes) { type ->
                            FilterChip(selected = activityType == type, onClick = { activityType = type }, label = { Text(type.replace('_', ' ').replace('-', ' '), fontSize = 9.sp) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${filteredActivities.size} matching events", color = Dim, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { activitySort = if (activitySort == "Newest") "Oldest" else "Newest" }) {
                        Icon(Icons.Default.Sort, null, tint = Cyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(activitySort, color = Cyan, fontSize = 10.sp)
                    }
                    TextButton(onClick = { activityQuery = ""; activityWindow = "All time"; activityType = "All types"; activitySort = "Newest" }) {
                        Text("Clear", color = Dim, fontSize = 10.sp)
                    }
                }
            }
            if (filteredActivities.isEmpty()) item {
                JCard {
                    Icon(Icons.Default.HistoryToggleOff, null, tint = Dim, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (acts.isEmpty()) "No activity has been recorded yet." else "No events match these filters.", color = Dim, fontSize = 12.sp)
                }
            }
            items(filteredActivities) { x ->
                val t = x.optLong("time")
                val rawType = x.optString("type").ifBlank { "Activity" }
                val eventType = rawType.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
                val who = x.optString("username").ifEmpty { x.optString("value") }
                val detail = x.optString("message").ifEmpty { x.optString("description") }
                val eventId = x.optString("id").ifBlank { "${rawType}_${t}_${who}" }
                val expanded = expandedActivity == eventId
                val glyph = when {
                    eventType.contains("creat", true) -> Icons.Default.PersonAdd
                    eventType.contains("delet", true) || eventType.contains("remov", true) -> Icons.Default.PersonRemove
                    eventType.contains("invit", true) -> Icons.Default.ConfirmationNumber
                    eventType.contains("disabl", true) -> Icons.Default.Block
                    eventType.contains("enabl", true) -> Icons.Default.CheckCircle
                    else -> Icons.Default.History
                }
                JCard(Modifier.clickable { expandedActivity = if (expanded) "" else eventId }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Red.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(glyph, null, tint = Red, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(eventType, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            if (who.isNotBlank()) Text(who, color = Dim, fontSize = 12.sp)
                            if (detail.isNotBlank()) Text(detail, color = Dim, fontSize = 11.sp, maxLines = if (expanded) Int.MAX_VALUE else 2)
                            if (t > 0) Text(DateFormat.getDateTimeInstance().format(Date(if (t > 1e12) t else t * 1000)), color = Dim, fontSize = 10.sp)
                            if (expanded) {
                                Spacer(Modifier.height(5.dp))
                                Text("Tap again to collapse", color = Cyan, fontSize = 9.sp)
                                val extra = x.keys().asSequence().filter { key ->
                                    key !in setOf("id", "type", "username", "value", "message", "description", "time")
                                }.toList()
                                extra.forEach { key ->
                                    val value = x.optString(key)
                                    if (value.isNotBlank() && value != "null") Text("${key.replace('_', ' ')}: $value", color = Dim, fontSize = 10.sp)
                                }
                            }
                        }
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Dim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(CardC)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val segment = maxWidth / 5
            val indicatorX by androidx.compose.animation.core.animateDpAsState(segment * tab + segment / 2 - 12.dp, tween(280), label = "navIndicator")
            Box(Modifier.offset(x = indicatorX).width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Red))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Triple("Users", Icons.Default.People, 0),
                    Triple("Invites", Icons.Default.ConfirmationNumber, 1),
                    Triple("Activity", Icons.Default.Timeline, 2),
                    Triple("Control", Icons.Default.Dashboard, 3),
                    Triple("Tools", Icons.Default.Tune, 4)
                ).forEach { (label, glyph, index) ->
                    val selectedTint by animateColorAsState(if (tab == index) Color.White else Dim, tween(220), label = "bottomNav$index")
                    Column(Modifier.weight(1f).clickable { tab = index }.padding(top = 10.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Icon(glyph, contentDescription = label, tint = selectedTint, modifier = Modifier.size(20.dp))
                            if (index == 3 && unreadNotificationCount > 0) {
                                Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp)
                                    .size(7.dp).clip(CircleShape).background(Cyan))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(label, fontSize = 9.sp, color = selectedTint,
                            fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
    if (tab == 0) {
        ExtendedFloatingActionButton(
            onClick = { dlg = "user" },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 82.dp)
                .zIndex(3f),
            containerColor = Red,
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 12.dp, pressedElevation = 16.dp),
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text("Add user", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        )
    }
    }

    if (advancedModulesOpen) {
        AdvancedModulesDialog(
            api = api,
            users = users,
            invites = invites,
            profiles = profs,
            onClose = { advancedModulesOpen = false },
            onRefresh = { refresh() },
            onMessage = { msg = it }
        )
    }
    if (featureDialog == "notifications") {
        val visibleAlerts = centerAlerts.filter {
            notificationFilter == "All" || it.category == notificationFilter
        }
        AlertDialog(
            onDismissRequest = { featureDialog = "" },
            containerColor = CardC,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notification center", color = Color.White, modifier = Modifier.weight(1f))
                        if (unreadNotificationCount > 0) {
                            Text("$unreadNotificationCount new", color = Cyan, fontSize = 10.sp)
                        }
                    }
                    Text("Account alerts and recent server activity", color = Dim, fontSize = 10.sp)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Accounts", "Activity").forEach { filter ->
                            FilterChip(
                                selected = notificationFilter == filter,
                                onClick = { notificationFilter = filter },
                                label = { Text(filter, fontSize = 10.sp) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = visibleAlerts.any { it.id !in readNotificationIds },
                            onClick = { markAllNotificationsRead(visibleAlerts.map { it.id }) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("Mark read", color = Cyan, fontSize = 9.sp) }
                    }
                    if (visibleAlerts.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.NotificationsNone, null, tint = Dim, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("You're all caught up", color = Color.White, fontSize = 13.sp)
                            Text("No matching alerts or recent activity.", color = Dim, fontSize = 10.sp)
                        }
                    } else {
                        visibleAlerts.forEach { alert ->
                            val unread = alert.id !in readNotificationIds
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(if (unread) PillC else Color.Transparent)
                                    .clickable { markNotificationRead(alert.id) }
                                    .padding(11.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape)
                                        .background(if (alert.warning) Color(0x33FFB547) else Color(0x2239D5F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (alert.category == "Activity") Icons.Default.History
                                        else Icons.Default.Schedule,
                                        null,
                                        tint = if (alert.warning) Color(0xFFFFB547) else Cyan,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            alert.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (unread) Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan))
                                    }
                                    Text(alert.detail, color = Dim, fontSize = 10.sp)
                                    Text(
                                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                            .format(Date(alert.timestamp)),
                                        color = Dim.copy(alpha = .72f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ featureDialog = "" }) { Text("Done", color = Red) } }
        )
    }
    if (featureDialog == "diagnostics") {
        val passedChecks = diagnosticChecks.count { it.passed }
        AlertDialog(onDismissRequest = { if (!diagnosticBusy) featureDialog = "" }, containerColor = CardC,
            title = { Text("Server diagnostics", color = Color.White) },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(s.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(s.url, color = Dim, fontSize = 10.sp)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s.url.trim().startsWith("https://", true)) Icons.Default.Lock else Icons.Default.Warning,
                            null, tint = if (s.url.trim().startsWith("https://", true)) Green else Color(0xFFFFC36B),
                            modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.url.trim().startsWith("https://", true))
                            "HTTPS transport configured" else "HTTP connection · HTTPS is recommended",
                            color = if (s.url.trim().startsWith("https://", true)) Green else Color(0xFFFFC36B),
                            fontSize = 10.sp)
                    }
                }
                val lastCrashReport = ctx.getSharedPreferences("allerium_diagnostics", Context.MODE_PRIVATE)
                    .getString("last_crash", null)
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, tint = Cyan, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("App crash diagnostics", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (lastCrashReport.isNullOrBlank()) "No crash report has been captured on this install."
                                else "A crash report is saved from the most recent app crash.", color = Dim, fontSize = 10.sp)
                        }
                    }
                    if (!lastCrashReport.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(lastCrashReport.take(12000), color = Dim, fontSize = 9.sp,
                                modifier = Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("Allerium crash report", lastCrashReport))
                                msg = "Crash report copied to clipboard."
                            }) { Text("Copy report", color = Cyan) }
                            TextButton(onClick = {
                                ctx.getSharedPreferences("allerium_diagnostics", Context.MODE_PRIVATE)
                                    .edit().remove("last_crash").remove("last_crash_at").apply()
                                msg = "Saved crash report cleared."
                            }) { Text("Clear", color = Dim) }
                        }
                    }
                }
                if (diagnosticChecks.isEmpty() && !diagnosticBusy) {
                    Text("Run a diagnostic to check authenticated JFA-GO endpoints and response times.",
                        color = Dim, fontSize = 11.sp)
                }
                if (diagnosticBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                    Text("Checking server endpoints…", color = Cyan, fontSize = 11.sp)
                }
                if (diagnosticChecks.isNotEmpty()) {
                    val allPassed = passedChecks == diagnosticChecks.size
                    JCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (allPassed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                null, tint = if (allPassed) Green else Color(0xFFFFC36B))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (allPassed) "All checks passed" else "Some checks need attention",
                                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("$passedChecks of ${diagnosticChecks.size} endpoints responding",
                                    color = Dim, fontSize = 10.sp)
                            }
                        }
                    }
                    diagnosticChecks.forEach { check ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top) {
                            Icon(if (check.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null, tint = if (check.passed) Green else Color(0xFFFF8585),
                                modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(check.label, color = Color.White, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text(check.detail, color = Dim, fontSize = 10.sp)
                            }
                            Text("${check.durationMs} ms", color = Cyan, fontSize = 9.sp)
                        }
                        Divider(color = Line)
                    }
                    Text("Response times measure each authenticated API request from this device. This does not measure CPU, memory, or disk usage.",
                        color = Dim, fontSize = 9.sp)
                }
            } },
            confirmButton = { TextButton(enabled = !diagnosticBusy, onClick = {
                diagnosticBusy = true
                scope.launch {
                    diagnosticChecks = withContext(Dispatchers.IO) { api.diagnostics() }
                    diagnosticBusy = false
                }
            }) { Text(if (diagnosticChecks.isEmpty()) "Run diagnostics" else "Run again", color = Red) } },
            dismissButton = { TextButton(enabled = !diagnosticBusy, onClick = { featureDialog = "" }) { Text("Close") } })
    }
    if (featureDialog == "operations") {
        OperationsCenterDialog(
            api = api,
            server = s,
            users = users,
            invites = invites,
            activities = acts,
            onClose = { featureDialog = "" },
            onLogout = { featureDialog = ""; back() }
        )
    }
    if (featureDialog == "export") {
        AlertDialog(onDismissRequest = { featureDialog = "" }, containerColor = CardC,
            title = { Text("Export workspace data", color = Color.White) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Share CSV files for users and activity. Passwords and saved credentials are never exported.", color = Dim, fontSize = 11.sp)
                RedBtn("Export users · CSV", icon = Icons.Default.People) {
                    val csv = buildString {
                        append("username,email,admin,disabled,expiry\n")
                        users.forEach { u ->
                            val values = listOf(u.optString("name"), u.optString("email"), u.optBoolean("admin").toString(), u.optBoolean("disabled").toString(), u.optLong("expiry").toString())
                            append(values.joinToString(",") { value -> "\"" + value.replace("\"", "\"\"") + "\"" })
                            append('\n')
                        }
                    }
                    shareCsv(ctx, "Allerium users.csv", csv)
                }
                RedBtn("Export activity · CSV", icon = Icons.Default.History) {
                    val csv = buildString {
                        append("time,type,username,details\n")
                        acts.forEach { a ->
                            val values = listOf(a.optLong("time").toString(), a.optString("type"), a.optString("username"), a.optString("message").ifBlank { a.optString("description") })
                            append(values.joinToString(",") { value -> "\"" + value.replace("\"", "\"\"") + "\"" })
                            append('\n')
                        }
                    }
                    shareCsv(ctx, "Allerium activity.csv", csv)
                }
            } },
            confirmButton = { TextButton({ featureDialog = "" }) { Text("Done", color = Red) } })
    }

    val close = { dlg = ""; target = null }
    if (dlg == "reset-confirm") target?.let { u ->
        AlertDialog(
            onDismissRequest = { if (!resetBusy) close() },
            containerColor = CardC,
            title = { Text("Reset ${u.optString("name")}'s password?", color = Color.White) },
            text = { Text(
                "Allerium will ask JFA-GO to generate an administrator-initiated reset link. If the account has a delivery method, JFA-GO can send the instructions. Otherwise, you can copy and share the link manually. The link expires after 30 minutes. JFA-GO's Password Resets > Link reset option must be enabled for the link to open.",
                color = Dim, fontSize = 12.sp
            ) },
            confirmButton = {
                TextButton(enabled = !resetBusy, onClick = {
                    val userId = u.optString("id")
                    resetBusy = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { api.adminPasswordReset(userId) }
                            resetLink = result.optString("link")
                            resetMessage = if (result.optBoolean("manual")) {
                                "No delivery method is configured for this account. Copy the link below and send it to the user securely."
                            } else if (resetLink.isNotBlank()) {
                                "JFA-GO generated the reset link. Instructions may also have been sent using the user's configured delivery method."
                            } else {
                                "JFA-GO accepted the reset request. Check the user's configured delivery method."
                            }
                            dlg = "reset-result"
                        } catch (e: Exception) {
                            msg = e.message ?: "Password reset request failed."
                            close()
                        } finally { resetBusy = false }
                    }
                }) { Text(if (resetBusy) "Working…" else "Generate reset link", color = Red) }
            },
            dismissButton = { TextButton(enabled = !resetBusy, onClick = close) { Text("Cancel") } }
        )
    }
    if (dlg == "reset-result") {
        AlertDialog(
            onDismissRequest = close,
            containerColor = CardC,
            title = { Text("Password reset", color = Color.White) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(resetMessage, color = Dim, fontSize = 12.sp)
                if (resetLink.isNotBlank()) {
                    SelectionContainer { Text(resetLink, color = Cyan, fontSize = 11.sp) }
                    Text("Treat this link like a password. Anyone who has it may be able to reset the account.", color = Color(0xFFFFC36B), fontSize = 10.sp)
                }
            } },
            confirmButton = {
                if (resetLink.isNotBlank()) TextButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Allerium password reset link", resetLink))
                }) { Text("Copy link", color = Red) }
                TextButton(onClick = close) { Text("Done", color = Red) }
            }
        )
    }
    if (dlg == "invite" || dlg == "expiry") {
        var a by remember { mutableStateOf(if (dlg == "invite") "7" else "30") }; var b by remember { mutableStateOf("1") }; var st by remember { mutableStateOf("") }; var pf by remember { mutableStateOf("") }
        AlertDialog(close, containerColor = CardC, title = { Text(if (dlg == "invite") "New invite" else "Set expiry") },
            text = { Column { Field(a, { a = it.filter(Char::isDigit) }, "Days"); if (dlg == "invite") { Field(b, { b = it.filter(Char::isDigit) }, "Number of uses"); Field(st, { st = it }, "Send to email (optional)"); Chips(profs, pf) { pf = it } } } },
            confirmButton = { TextButton({ val t = target; val inv = dlg == "invite"; close()
                act { if (inv) api.createInvite(a.toIntOrNull() ?: 7, b.toIntOrNull() ?: 1, st, pf) else api.setExpiry(t!!.getString("id"), a.toIntOrNull() ?: 30) } }) { Text("OK", color = Red) } },
            dismissButton = { TextButton(close) { Text("Cancel") } })
    }
    if (dlg == "del") target?.let { u ->
        AlertDialog(close, containerColor = CardC, title = { Text("Delete ${u.optString("name")}?") }, text = { Text("This removes the account from Jellyfin.") },
            confirmButton = { TextButton({ close(); act { api.deleteUser(u.getString("id")) } }) { Text("Delete", color = Red) } },
            dismissButton = { TextButton(close) { Text("Cancel") } })
    }
    if (dlg == "user") {
        var n by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var e by remember { mutableStateOf("") }; var pf by remember { mutableStateOf("") }
        AlertDialog(close, containerColor = CardC, title = { Text("New user") },
            text = { Column { Field(n, { n = it }, "Username"); Field(p, { p = it }, "Password", true); Field(e, { e = it }, "Email (optional)"); Chips(profs, pf) { pf = it } } },
            confirmButton = { TextButton({ close(); act { api.createUser(n, p, e, pf) } }) { Text("Create", color = Red) } },
            dismissButton = { TextButton(close) { Text("Cancel") } })
    }
}