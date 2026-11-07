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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AlleriumUpdateInfo(val tag:String,val versionName:String,val apkUrl:String,val notes:String,val digest:String)

object AlleriumUpdateChecker {
    private const val API="https://api.github.com/repos/AlleriaLabs/Allerium/releases/latest"
    private const val PREFS="allerium_updates"

    suspend fun check(context:Context):AlleriumUpdateInfo?=withContext(Dispatchers.IO) {
        val c=URL(API).openConnection() as HttpURLConnection
        c.connectTimeout=12000; c.readTimeout=20000
        c.setRequestProperty("Accept","application/vnd.github+json")
        c.setRequestProperty("User-Agent","Allerium-Android")
        val j=try {
            val code=c.responseCode
            if(code==404) return@withContext null
            if(code !in 200..299) error("GitHub update check failed (HTTP $code).")
            JSONObject(c.inputStream.bufferedReader().use{it.readText()})
        } finally { c.disconnect() }
        val tag=j.optString("tag_name").trim()
        val version=tag.removePrefix("v").removePrefix("V")
        val installed=runCatching { packageInfo(context.packageManager,context.packageName).versionName.orEmpty() }.getOrDefault("")
        if(tag.isBlank()||j.optBoolean("draft")||j.optBoolean("prerelease")||!newer(version,installed)) return@withContext null
        if(context.getSharedPreferences(PREFS,0).getString("dismissed_tag",null)==tag) return@withContext null
        val assets=j.optJSONArray("assets")?:return@withContext null
        var apk=""; var digest=""
        for(i in 0 until assets.length()) {
            val a=assets.optJSONObject(i)?:continue
            if(a.optString("name").endsWith(".apk",true)) {
                apk=a.optString("browser_download_url"); digest=a.optString("digest").removePrefix("sha256:"); break
            }
        }
        if(!apk.startsWith("https://")) return@withContext null
        AlleriumUpdateInfo(tag,version,apk,j.optString("body").trim(),digest)
    }

    fun dismiss(context:Context,tag:String) {
        context.getSharedPreferences(PREFS,0).edit().putString("dismissed_tag",tag).apply()
    }

    suspend fun install(context:Context,u:AlleriumUpdateInfo):String? {
        if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Allow Allerium to install updates, then return and tap Update again."
        }
        return withContext(Dispatchers.IO) {
            val c=URL(u.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout=15000;c.readTimeout=60000;c.instanceFollowRedirects=true
            c.setRequestProperty("User-Agent","Allerium-Android")
            val file=File(context.cacheDir,"updates/Allerium-update.apk")
            try {
                val code=c.responseCode
                if(code !in 200..299) error("APK download failed (HTTP $code).")
                file.parentFile?.mkdirs()
                c.inputStream.use{input->file.outputStream().use{input.copyTo(it)}}
            } finally { c.disconnect() }
            if(file.length()<1_000_000L) { file.delete(); error("Downloaded APK is invalid or incomplete.") }
            if(u.digest.isNotBlank()&&!hash(file).equals(u.digest,true)) { file.delete(); error("APK checksum verification failed.") }
            if(!sameSigner(context,file)) { file.delete(); error("This APK is not signed with the same certificate as your installed Allerium. Installation was stopped; your app and data are unchanged.") }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            null
        }
    }

    private fun packageInfo(pm:PackageManager,name:String,flags:Int=0):PackageInfo =
        if(Build.VERSION.SDK_INT>=33) pm.getPackageInfo(name,PackageManager.PackageInfoFlags.of(flags.toLong()))
        else { @Suppress("DEPRECATION") pm.getPackageInfo(name,flags) }

    @Suppress("DEPRECATION")
    private fun sameSigner(ctx:Context,file:File):Boolean=runCatching {
        val pm=ctx.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed=packageInfo(pm,ctx.packageName,flags)
        val candidate=pm.getPackageArchiveInfo(file.absolutePath,flags)?:return false
        fun signatures(p:PackageInfo):List<ByteArray> {
            val s=if(Build.VERSION.SDK_INT>=28) p.signingInfo?.apkContentsSigners?.toList().orEmpty() else p.signatures?.toList().orEmpty()
            return s.map{it.toByteArray()}
        }
        signatures(installed).map{hash(it)}.toSet().let { it.isNotEmpty()&&it==signatures(candidate).map{b->hash(b)}.toSet() }
    }.getOrDefault(false)

    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun hash(file:File):String {
        val d=MessageDigest.getInstance("SHA-256")
        file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)}}
        return d.digest().joinToString(""){"%02x".format(it)}
    }
    private fun newer(a:String,b:String):Boolean {
        fun p(s:String)=Regex("\\d+").findAll(s).mapNotNull{it.value.toIntOrNull()}.toList()
        val x=p(a);val y=p(b);if(x.isEmpty())return false
        for(i in 0 until maxOf(x.size,y.size)){val c=x.getOrElse(i){0}.compareTo(y.getOrElse(i){0});if(c!=0)return c>0}
        return false
    }
}

@Composable
fun AlleriumUpdateDialog(update:AlleriumUpdateInfo,onDismiss:()->Unit) {
    val ctx= LocalContext.current
    val scope= rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    AlertDialog(
        onDismissRequest={if(!busy)onDismiss()},
        title={Text("Allerium update available")},
        text={Column {
            Text("Version ${update.versionName} is ready to install.")
            if(update.notes.isNotBlank()) Text(update.notes.take(900),Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodySmall)
            message?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=10.dp))}
            if(busy){CircularProgressIndicator(Modifier.padding(top=12.dp));Text("Downloading and verifying update…")}
        }},
        confirmButton={TextButton(enabled=!busy,onClick={
            busy=true;message=null
            scope.launch {
                message=runCatching{AlleriumUpdateChecker.install(ctx,update)}.getOrElse{it.message?:"Update failed."}
                busy=false
            }
        }){Text(if(busy)"Updating…" else "Update")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Later")}}
    )
}
