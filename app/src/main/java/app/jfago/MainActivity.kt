package app.jfago

import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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

val Bg = Color(0xFF141414); val CardC = Color(0xFF1F1F1F); val Line = Color(0xFF333333)
val Red = Color(0xFFE50914); val Dim = Color(0xFFB3B3B3); val Green = Color(0xFF4CD964); val PillC = Color(0xFF2A2A2C)

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

@Composable fun JCard(m: Modifier = Modifier, c: @Composable ColumnScope.() -> Unit) =
    Column(m.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CardC)
        .border(1.dp, Line, RoundedCornerShape(28.dp)).padding(20.dp), content = c)

@Composable fun Bar(f: Float, m: Modifier = Modifier) =
    Box(m.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.03f, 1f)).fillMaxHeight().background(Red))
    }

@Composable fun Pill(t: String, dot: Color? = null) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(PillC).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Text(t, fontSize = 14.sp)
    }

@Composable fun RedBtn(t: String, enabled: Boolean = true, on: () -> Unit) =
    Button(on, Modifier.fillMaxWidth().height(56.dp), enabled, shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)) { Text(t, fontSize = 18.sp) }

@Composable fun Field(v: String, on: (String) -> Unit, label: String, pw: Boolean = false) =
    OutlinedTextField(v, on, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(16.dp), visualTransformation = if (pw) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Red, focusedLabelColor = Red, unfocusedBorderColor = Line, cursorColor = Red))

@Composable fun Header(sub: String) = Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF0A0A0A))
        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Red.copy(alpha = 0.28f), Color.Transparent, Red.copy(alpha = 0.18f))))) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo), null, Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Text("ALPASS", color = Red, fontSize = 28.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Red))
    }
    Text(sub.uppercase(), color = Dim, fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable fun Chips(opts: List<String>, sel: String, on: (String) -> Unit) {
    if (opts.isEmpty()) return
    Text("Profile", color = Dim, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("") + opts).forEach { o -> Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel == o) Red else PillC)
            .clickable { on(o) }.padding(horizontal = 14.dp, vertical = 8.dp)) { Text(o.ifEmpty { "Default" }, fontSize = 14.sp) } }
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
    LaunchedEffect(servers) { servers.forEach { s -> launch(Dispatchers.IO) {
        online[s.url] = try { (URL(s.url).openConnection() as HttpURLConnection).run { connectTimeout = 4000; readTimeout = 4000; responseCode; true } } catch (e: Exception) { false } } } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header("Jellyfin account admin") }
        item { Text("Your servers", fontSize = 32.sp) }
        items(servers) { s ->
            JCard(Modifier.clickable { login = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(s.name, fontSize = 26.sp); Text(s.url, color = Dim) }
                    Spacer(Modifier.width(8.dp))
                    val o = online[s.url]
                    Pill(if (o == null) "…" else if (o) "Online" else "Offline", if (o == true) Green else Red)
                    TextButton({ editing = s }) { Text("Edit", color = Red, fontSize = 18.sp) }
                }
            }
        }
        item { RedBtn("Add server") { adding = true }; Spacer(Modifier.height(24.dp)) }
    }
    if (adding || editing != null) {
        val old = editing
        var n by remember { mutableStateOf(old?.name ?: "") }; var u by remember { mutableStateOf(old?.url ?: "https://") }
        var us by remember { mutableStateOf(old?.user ?: "") }
        val close = { adding = false; editing = null }
        AlertDialog(close, containerColor = CardC, title = { Text(if (old == null) "Add server" else "Edit server") },
            text = { Column { Field(n, { n = it }, "Name"); Field(u, { u = it.trim() }, "URL"); Field(us, { us = it }, "Username") } },
            confirmButton = { TextButton({ val s = Srv(n, u.trimEnd('/'), us); save(if (old == null) servers + s else servers.map { if (it == old) s else it }); close() }) { Text("Save", color = Red) } },
            dismissButton = { Row { if (old != null) TextButton({ save(servers - old); close() }) { Text("Delete", color = Red) }; TextButton(close) { Text("Cancel") } } })
    }
    login?.let { s ->
        var pw by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        AlertDialog({ login = null }, containerColor = CardC, title = { Text(s.name) },
            text = { Column { Field(pw, { pw = it }, "Password for ${s.user}", true); if (err.isNotEmpty()) Text(err, color = Red) } },
            confirmButton = { TextButton({
                busy = true; err = ""
                scope.launch { try { val a = Api(s.url); withContext(Dispatchers.IO) { a.login(s.user, pw) }; login = null; open(s, a) }
                    catch (e: Exception) { err = e.message ?: "Failed" }; busy = false }
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
        item { JCard { Row {
            listOf(Triple("Users", users.size, users.size), Triple("Invites", invites.size, maxOf(invites.size, 10)), Triple("Disabled", off, maxOf(users.size, 1))).forEach { (l, v, t) ->
                Column(Modifier.weight(1f).padding(end = 10.dp)) { Text(l, color = Dim); Text("$v", fontSize = 34.sp); Spacer(Modifier.height(8.dp)); Bar(if (t == 0) 0f else v.toFloat() / t) } } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Users", "Invites", "Activity", "Settings").forEachIndexed { i, t ->
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) Red else PillC).clickable { tab = i }.padding(horizontal = 13.dp, vertical = 10.dp)) { Text(t, fontSize = 14.sp) } }
            Spacer(Modifier.weight(1f)); IconButton({ refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Red) } } }
        if (msg.isNotEmpty()) item { Text(msg, color = Red) }
        if (tab == 0) {
            item { RedBtn("New user") { dlg = "user" } }
            item { OutlinedButton({ dlg = "announce" }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(24.dp)) { Text("Announce to ${shown.size} shown", color = Red) } }
            item { Field(q, { q = it }, "Search users") }
            items(shown) { u ->
                val dis = u.optBoolean("disabled"); val exp = u.optLong("expiry")
                JCard { Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(u.optString("name"), fontSize = 22.sp)
                        val em = u.optString("email"); if (em.isNotEmpty()) Text(em, color = Dim)
                        if (exp > 0) Text("Expires " + DateFormat.getDateInstance().format(Date(if (exp > 1e12) exp else exp * 1000)), color = Dim, fontSize = 13.sp) }
                    Pill(if (u.optBoolean("admin")) "Admin" else if (dis) "Disabled" else "Active", if (dis) Red else Green) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(!dis, { on -> act { api.setEnabled(u.getString("id"), on) } }, colors = SwitchDefaults.colors(checkedTrackColor = Red))
                        Spacer(Modifier.weight(1f))
                        IconButton({ target = u; dlg = "expiry" }) { Icon(Icons.Default.Schedule, "Expiry", tint = Dim) }
                        IconButton({ target = u; dlg = "del" }) { Icon(Icons.Default.Delete, "Delete", tint = Red) } } }
            }
        } else if (tab == 1) {
            item { RedBtn("New invite") { dlg = "invite" } }
            items(invites) { i ->
                val code = i.optString("code")
                JCard { Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(code, fontSize = 20.sp); Text("Uses left: " + i.optString("remaining-uses").ifEmpty { "∞" }, color = Dim) }
                    IconButton({ (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("invite", "${api.base}/invite/$code")) }) { Icon(Icons.Default.ContentCopy, "Copy", tint = Dim) }
                    IconButton({ act { api.deleteInvite(code) } }) { Icon(Icons.Default.Delete, "Delete", tint = Red) } } }
            }
        }
        if (tab == 2) {
            if (acts.isEmpty()) item { Text("No activity loaded.", color = Dim) }
            items(acts) { x ->
                val t = x.optLong("time")
                JCard { Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.optString("type").replaceFirstChar { it.uppercase() }, fontSize = 18.sp)
                        val who = x.optString("username").ifEmpty { x.optString("value") }
                        if (who.isNotEmpty()) Text(who, color = Dim)
                        if (t > 0) Text(DateFormat.getDateTimeInstance().format(Date(if (t > 1e12) t else t * 1000)), color = Dim, fontSize = 13.sp)
                    }
                    IconButton({ act { api.deleteActivity(x.getString("id")) } }) { Icon(Icons.Default.Delete, "Delete", tint = Red) } } }
            }
        }
        if (tab == 3) {
            val c = cfg
            if (c == null) item { Text("Loading settings…", color = Dim) } else c.keys().forEach { sec ->
                val st = c.optJSONObject(sec)?.optJSONObject("settings") ?: return@forEach
                item(key = "h$sec") { Text(c.getJSONObject(sec).optJSONObject("meta")?.optString("name").orEmpty().ifEmpty { sec }, color = Red, fontSize = 20.sp) }
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
            text = { Column { val d = o.optString("description"); if (d.isNotEmpty()) Text(d, color = Dim, fontSize = 13.sp)
                Field(v, { v = it }, sec, o.optString("type") == "password") } },
            confirmButton = { TextButton({ cfgEdit = null
                setCfg(sec, k, if (o.optString("type") == "number") (v.toIntOrNull() ?: v) else v) }) { Text("Save", color = Red) } },
            dismissButton = { TextButton({ cfgEdit = null }) { Text("Cancel") } })
    }
}
