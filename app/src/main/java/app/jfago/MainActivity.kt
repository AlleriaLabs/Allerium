package app.jfago

import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.DateFormat
import java.util.Date

val Bg = Color(0xFF080B16)
val CardC = Color(0xFF11182A)
val Line = Color(0xFF26334D)
val Red = Color(0xFF9B72FF)
val Cyan = Color(0xFF39D5F2)
val Dim = Color(0xFF9AA8C3)
val Green = Color(0xFF43D9A3)
val PillC = Color(0xFF1C2740)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Bg.toArgb(); window.navigationBarColor = Bg.toArgb()
        val cs = darkColorScheme(primary = Red, background = Bg, surface = Bg, onSurface = Color.White,
            surfaceVariant = CardC, onPrimary = Color.White, secondaryContainer = PillC)
        setContent { MaterialTheme(colorScheme = cs) { Surface(Modifier.fillMaxSize(), color = Bg) { Root() } } }
    }
}

data class Srv(val name: String, val url: String, val user: String)

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
    Column(m.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF17223A), CardC, Color(0xFF101727))))
        .border(1.dp, Line.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
        .animateContentSize(animationSpec = tween(240)).padding(18.dp), content = c)

@Composable fun Bar(f: Float, m: Modifier = Modifier) =
    Box(m.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Line)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.03f, 1f)).fillMaxHeight()
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Red, Cyan))))
    }

@Composable fun Pill(t: String, dot: Color? = null) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(PillC).border(1.dp, Line, RoundedCornerShape(50))
        .padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Text(t, fontSize = 10.sp, color = Color(0xFFE4EAF8), fontWeight = FontWeight.Medium)
    }

@Composable fun RedBtn(t: String, enabled: Boolean = true, icon: ImageVector? = null, on: () -> Unit) =
    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
        .background(if (enabled) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF7458D6), Color(0xFF416DCE), Color(0xFF168DAA))) else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Line, Line)))
        .clickable(enabled = enabled, onClick = on).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(9.dp)) }
        Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }

@Composable fun Field(v: String, on: (String) -> Unit, label: String, pw: Boolean = false) =
    OutlinedTextField(v, on, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(16.dp), visualTransformation = if (pw) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, focusedLabelColor = Red, unfocusedBorderColor = Line, cursorColor = Red))

@Composable fun Header(sub: String) = Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.allerium_mark), null, Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("ALLERIUM", color = Color.White, fontSize = 15.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
            Text("ALLERIA LABS", color = Dim, fontSize = 8.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(Cyan))
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
    session?.let { (s, api) -> Home(s, api) { session = null }; return }
    ServerList(servers, ::save) { s, a -> session = s to a }
}

@Composable fun ServerList(servers: List<Srv>, save: (List<Srv>) -> Unit, open: (Srv, Api) -> Unit) {
    var editing by remember { mutableStateOf<Srv?>(null) }
    var adding by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf<Srv?>(null) }
    val online = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val credentials = remember(context) { credentialPrefs(context) }
    val scope = rememberCoroutineScope()
    var authError by remember { mutableStateOf("") }
    var authenticating by remember { mutableStateOf(false) }
    fun selectServer(s: Srv) {
        val stored = credentials.getString(s.url, "").orEmpty()
        if (stored.isBlank()) { login = s; return }
        authenticating = true
        scope.launch {
            try {
                val api = Api(s.url)
                withContext(Dispatchers.IO) { api.login(s.user, stored) }
                open(s, api)
            } catch (e: Exception) {
                authError = e.message ?: "Saved sign-in failed. Please update the password."
                login = s
            } finally { authenticating = false }
        }
    }
    LaunchedEffect(servers) { servers.forEach { s -> launch(Dispatchers.IO) {
        online[s.url] = try { (URL(s.url).openConnection() as HttpURLConnection).run { connectTimeout = 4000; readTimeout = 4000; responseCode; true } } catch (e: Exception) { false } } } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header("Your secure JFA-GO workspace") }
        if (authenticating) item { Text("Connecting securely…", color = Cyan, fontSize = 11.sp) }
        if (authError.isNotBlank()) item { Text(authError, color = Color(0xFFFF8A8A), fontSize = 11.sp) }
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                Text("Welcome back", color = Dim, fontSize = 10.sp, letterSpacing = 1.sp)
                Text("Your servers", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Choose a server to manage its users and invitations.", color = Dim, fontSize = 10.sp)
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
        items(servers) { s ->
            AnimatedVisibility(visible = true, enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 5 }) {
            JCard(Modifier.clickable(enabled = !authenticating) { selectServer(s) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF6E56CF), Color(0xFF159EB8)))),
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
                    Text("Open server workspace", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, tint = Cyan, modifier = Modifier.size(18.dp))
                }
            }
            }
        }
        item { RedBtn("Add server", icon = Icons.Default.Add) { adding = true }; Spacer(Modifier.height(24.dp)) }
    }
    if (adding || editing != null) {
        val old = editing
        var n by remember { mutableStateOf(old?.name ?: "") }; var u by remember { mutableStateOf(old?.url ?: "https://") }
        var us by remember { mutableStateOf(old?.user ?: "") }
        var pw by remember { mutableStateOf(if (old == null) "" else credentials.getString(old.url, "").orEmpty()) }
        val close = { adding = false; editing = null }
        AlertDialog(close, containerColor = CardC, title = { Text(if (old == null) "Add server" else "Edit server") },
            text = { Column {
                Field(n, { n = it }, "Server name")
                Field(u, { u = it.trim() }, "Server URL")
                Field(us, { us = it }, "Username")
                Field(pw, { pw = it }, "Password", true)
                Text("Password is stored encrypted on this device and used for automatic sign-in.", color = Dim, fontSize = 10.sp)
            } },
            confirmButton = { TextButton(enabled = n.isNotBlank() && u.isNotBlank() && us.isNotBlank() && (pw.isNotBlank() || old != null),
                onClick = {
                    val normalized = u.trim().trimEnd('/')
                    val s = Srv(n.trim(), normalized, us.trim())
                    if (old != null && old.url != normalized) credentials.edit().remove(old.url).apply()
                    if (pw.isNotBlank()) credentials.edit().putString(normalized, pw).apply()
                    save(if (old == null) servers + s else servers.map { if (it == old) s else it })
                    close()
                }) { Text("Save", color = Red) } },
            dismissButton = { Row { if (old != null) TextButton({ credentials.edit().remove(old.url).apply(); save(servers - old); close() }) { Text("Delete", color = Red) }; TextButton(close) { Text("Cancel") } } })
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

@Composable fun Home(s: Srv, api: Api, back: () -> Unit) {
    BackHandler(onBack = back)
    var tab by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf(listOf<JSONObject>()) }
    var invites by remember { mutableStateOf(listOf<JSONObject>()) }
    var acts by remember { mutableStateOf(listOf<JSONObject>()) }
    var profs by remember { mutableStateOf(listOf<String>()) }
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    var cfgEdit by remember { mutableStateOf<Triple<String, String, JSONObject>?>(null) }
    var q by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf("") }; var target by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    fun refresh() = scope.launch { try { withContext(Dispatchers.IO) { users = api.users(); invites = api.invites(); acts = try { api.activity() } catch (e: Exception) { acts }; profs = try { api.profiles() } catch (e: Exception) { profs } }; msg = "" } catch (e: Exception) { msg = e.message ?: "Error" } }
    fun act(b: () -> Unit) = scope.launch { try { withContext(Dispatchers.IO) { b() } } catch (e: Exception) { msg = e.message ?: "Error" }; refresh() }
    fun setCfg(sec: String, k: String, v: Any) = scope.launch { try { withContext(Dispatchers.IO) { api.setConfig(sec, k, v); cfg = api.config() } } catch (e: Exception) { msg = e.message ?: "Error" } }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(tab) { if (tab == 3 && cfg == null) try { cfg = withContext(Dispatchers.IO) { api.config() } } catch (e: Exception) { msg = e.message ?: "Error" } }
    val off = users.count { it.optBoolean("disabled") }
    val shown = users.filter { q.isBlank() || it.optString("name").contains(q, true) || it.optString("email").contains(q, true) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Header(s.name) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("TOTAL USERS", users.size, Color(0xFF9B72FF)),
                    Triple("INVITATIONS", invites.size, Cyan),
                    Triple("DISABLED", off, Color(0xFFFFB86B))).forEach { (label, value, tint) ->
                    JCard(Modifier.weight(1f)) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                            Icon(when (label) {
                                "TOTAL USERS" -> Icons.Default.Groups
                                "INVITATIONS" -> Icons.Default.ConfirmationNumber
                                else -> Icons.Default.Block
                            }, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.height(13.dp))
                        Text("$value", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(label, fontSize = 8.sp, color = Dim, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (tab == 0) "Directory" else listOf("Directory", "Invitations", "Activity", "Settings")[tab],
                        fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Manage your JFA-GO server", color = Dim, fontSize = 12.sp)
                }
                IconButton({ refresh() }, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PillC)) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Cyan)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF0D1424))
                .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Triple("Users", Icons.Default.People, 0),
                    Triple("Invites", Icons.Default.ConfirmationNumber, 1),
                    Triple("Activity", Icons.Default.Timeline, 2),
                    Triple("Settings", Icons.Default.Tune, 3)
                ).forEach { (label, glyph, index) ->
                    val selectedTint by animateColorAsState(if (tab == index) Color.White else Dim, tween(220), label = "navTint$index")
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                        .background(if (tab == index) Color(0xFF293454) else Color.Transparent)
                        .clickable { tab = index }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(glyph, contentDescription = label, tint = selectedTint, modifier = Modifier.size(17.dp))
                            Text(label, fontSize = 10.sp, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                                color = selectedTint, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = Red) }
        if (tab == 0) {
            item { RedBtn("Create user", icon = Icons.Default.PersonAdd) { dlg = "user" } }
            item { OutlinedButton({ dlg = "announce" }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(24.dp)) { Text("Announce to ${shown.size} shown", color = Red) } }
            item { Field(q, { q = it }, "Search users") }
            items(shown) { u ->
                val dis = u.optBoolean("disabled"); val exp = u.optLong("expiry")
                JCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF6E56CF), Color(0xFF1A9DB8)))),
                            contentAlignment = Alignment.Center) {
                            Text(u.optString("name").firstOrNull()?.uppercase() ?: "U", fontSize = 10.sp,
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.optString("name"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(!dis, { on -> act { api.setEnabled(u.getString("id"), on) } },
                                colors = SwitchDefaults.colors(checkedTrackColor = Cyan, checkedThumbColor = Color.White))
                            Text(if (dis) "Disabled" else "Enabled", fontSize = 10.sp, color = Dim)
                        }
                        Spacer(Modifier.weight(1f))
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
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF39D5F2).copy(alpha = .12f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ConfirmationNumber, null, tint = Cyan)
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
            if (acts.isEmpty()) item { Text("No activity loaded.", color = Dim) }
            items(acts) { x ->
                val t = x.optLong("time")
                JCard { Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.optString("type").replaceFirstChar { it.uppercase() }, fontSize = 15.sp)
                        val who = x.optString("username").ifEmpty { x.optString("value") }
                        if (who.isNotEmpty()) Text(who, color = Dim)
                        if (t > 0) Text(DateFormat.getDateTimeInstance().format(Date(if (t > 1e12) t else t * 1000)), color = Dim, fontSize = 10.sp)
                    }
                    IconButton({ act { api.deleteActivity(x.getString("id")) } }) { Icon(Icons.Default.Delete, "Delete", tint = Red) } } }
            }
        }
        if (tab == 3) {
            val c = cfg
            if (c == null) item { Text("Loading settings…", color = Dim) } else c.keys().forEach { sec ->
                val st = c.optJSONObject(sec)?.optJSONObject("settings") ?: return@forEach
                item(key = "h$sec") { Text(c.getJSONObject(sec).optJSONObject("meta")?.optString("name").orEmpty().ifEmpty { sec }, color = Red, fontSize = 10.sp) }
                st.keys().forEach { k ->
                    val o = st.getJSONObject(k); val bool = o.optString("type") == "bool"
                    item(key = "$sec.$k") { JCard(Modifier.clickable(enabled = !bool) { cfgEdit = Triple(sec, k, o) }) { Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(o.optString("name", k))
                            Text(if (o.optString("type") == "password") "••••" else o.opt("value")?.toString().orEmpty(), color = Dim, maxLines = 1) }
                        if (bool) Switch(o.optBoolean("value"), { on -> setCfg(sec, k, on) }, colors = SwitchDefaults.colors(checkedTrackColor = Red)) } } }
                }
            }
        }
        item { TextButton(back) { Text("Switch server", color = Dim) }; Spacer(Modifier.height(24.dp)) }
    }
    val close = { dlg = ""; target = null }
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
    if (dlg == "announce") {
        var sj by remember { mutableStateOf("") }; var bd by remember { mutableStateOf("") }
        AlertDialog(close, containerColor = CardC, title = { Text("Announce") },
            text = { Column { Field(sj, { sj = it }, "Subject"); Field(bd, { bd = it }, "Message") } },
            confirmButton = { TextButton({ val ids = shown.map { it.getString("id") }; close(); act { api.announce(ids, sj, bd) } }) { Text("Send", color = Red) } },
            dismissButton = { TextButton(close) { Text("Cancel") } })
    }
    cfgEdit?.let { (sec, k, o) ->
        var v by remember { mutableStateOf(if (o.optString("type") == "password") "" else o.opt("value")?.toString().orEmpty()) }
        AlertDialog({ cfgEdit = null }, containerColor = CardC, title = { Text(o.optString("name", k)) },
            text = { Column { val d = o.optString("description"); if (d.isNotEmpty()) Text(d, color = Dim, fontSize = 10.sp)
                Field(v, { v = it }, sec, o.optString("type") == "password") } },
            confirmButton = { TextButton({ cfgEdit = null
                setCfg(sec, k, if (o.optString("type") == "number") (v.toIntOrNull() ?: v) else v) }) { Text("Save", color = Red) } },
            dismissButton = { TextButton({ cfgEdit = null }) { Text("Cancel") } })
    }
}
