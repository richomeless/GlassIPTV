package com.glass.iptv

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String, val logo: String = "", val ref: String = "", val ua: String = "")

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
    fun playlists(): List<String> =
        p.getString("pl", null)?.split("\n")?.filter { it.isNotBlank() }
            ?: listOf("https://iptv-org.github.io/iptv/languages/ara.m3u")
    fun savePlaylists(l: List<String>) = p.edit().putString("pl", l.joinToString("\n")).apply()
    fun favs(): List<Channel> = (p.getString("fav", "") ?: "").split("\n").filter { it.isNotBlank() }.map {
        val f = it.split("\t")
        Channel(f[0], f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, f.getOrElse(4) { "" })
    }
    fun saveFavs(l: List<Channel>) = p.edit().putString("fav",
        l.joinToString("\n") { "${it.name}\t${it.url}\t${it.logo}\t${it.ref}\t${it.ua}" }).apply()
}

class MainActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        f.addListener({ controller.value = f.get() }, MoreExecutors.directExecutor())
        future = f
        setContent { App(controller.value, inPip.value) { enterPip() } }
    }

    private fun enterPip() {
        val p = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        enterPictureInPictureMode(p)
    }

    override fun onUserLeaveHint() {
        if (controller.value?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        inPip.value = isInPip
    }

    override fun onDestroy() {
        future?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

fun Modifier.glass(r: Dp = 24.dp): Modifier = this
    .clip(RoundedCornerShape(r))
    .background(Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.White.copy(0.07f))))
    .border(1.dp, Color.White.copy(0.28f), RoundedCornerShape(r))

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.glass(18.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Chip(text: String, selected: Boolean, onLong: () -> Unit = {}, onClick: () -> Unit) {
    Text(
        text, color = Color.White, maxLines = 1, fontSize = 14.sp,
        modifier = Modifier.glass(14.dp)
            .background(if (selected) Color.White.copy(0.22f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(c: Channel, fav: Boolean, onLong: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).glass(22.dp), contentAlignment = Alignment.Center) {
            if (c.logo.isEmpty()) Text(c.name.take(1), color = Color.White, fontSize = 28.sp)
            else AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(14.dp))
            if (fav) Text("★", color = Color(0xFFFFD60A), fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(c.name, color = Color.White.copy(0.9f), fontSize = 12.sp, maxLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Video(p: Player) = AndroidView(
    factory = { PlayerView(it).apply { useController = false; player = p } },
    update = { it.player = p },
    modifier = Modifier.fillMaxSize()
)

val logoRe = Regex("tvg-logo=\"([^\"]*)\"")

fun loadPlaylist(u: String): List<Channel> {
    try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        val r = c.inputStream.bufferedReader()
        val first = r.readLine()?.trimStart('\uFEFF')
        if (first?.startsWith("#EXTM3U") != true) { c.disconnect(); return emptyList() }
        val text = r.readText()
        if (text.contains("#EXT-X-")) return emptyList()
        val out = mutableListOf<Channel>()
        var name = ""; var logo = ""; var ref = ""; var ua = ""
        text.lineSequence().map { it.trim() }.forEach { l ->
            if (l.startsWith("#EXTINF")) {
                name = l.substringAfterLast(",").trim().replace("\t", " ")
                logo = logoRe.find(l)?.groupValues?.get(1) ?: ""
                ref = ""; ua = ""
            } else if (l.startsWith("#EXTVLCOPT:")) {
                val kv = l.removePrefix("#EXTVLCOPT:")
                if (kv.startsWith("http-referrer=") || kv.startsWith("http-referer=")) ref = kv.substringAfter("=")
                else if (kv.startsWith("http-user-agent=")) ua = kv.substringAfter("=")
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                out += Channel(name.ifEmpty { l }, l, logo, ref, ua); name = ""; logo = ""; ref = ""; ua = ""
            }
        }
        return out
    } catch (e: Exception) { return emptyList() }
}

// يفحص الرابط بنفس الهيدرز ويرجع رد السيرفر، عشان نعرف سبب فشل القناة
suspend fun probe(c: Channel): String = withContext(Dispatchers.IO) {
    try {
        val k = URL(c.url).openConnection() as HttpURLConnection
        k.connectTimeout = 8000; k.readTimeout = 8000
        k.setRequestProperty("User-Agent", c.ua.ifEmpty { BROWSER_UA })
        if (c.ref.isNotEmpty()) k.setRequestProperty("Referer", c.ref)
        val code = k.responseCode
        val s = if (code < 400) k.inputStream else k.errorStream
        val buf = CharArray(300)
        val n = s?.bufferedReader()?.read(buf) ?: 0
        "HTTP $code: " + String(buf, 0, maxOf(n, 0)).replace("\n", " ")
    } catch (e: Exception) { e.javaClass.simpleName }
}

@Composable
fun App(player: Player?, inPip: Boolean, onPip: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val store = remember { Store(ctx) }
    var lists by remember { mutableStateOf(store.playlists()) }
    var favs by remember { mutableStateOf(store.favs()) }
    var tab by remember { mutableStateOf(0) }
    var channels by remember { mutableStateOf(listOf<Channel>()) }
    var loading by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(player?.isPlaying == true) }
    var error by remember { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    var cur by remember { mutableStateOf<Channel?>(null) }
    var ui by remember { mutableStateOf(true) }
    var quals by remember { mutableStateOf(listOf<Pair<Int, TrackSelectionOverride>>()) }
    var qLabel by remember { mutableStateOf("تلقائي") }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (isPlaying) error = null }
            override fun onPlayerError(e: PlaybackException) {
                val msg = "تعذّر تشغيل القناة (${e.errorCodeName})"
                error = msg
                val c = cur
                if (c != null) scope.launch { error = msg + "\n" + probe(c) }
            }
            override fun onTracksChanged(tracks: Tracks) {
                quals = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { g ->
                    (0 until g.length).filter { g.isTrackSupported(it) && g.getTrackFormat(it).height > 0 }
                        .map { i -> g.getTrackFormat(i).height to TrackSelectionOverride(g.mediaTrackGroup, i) }
                }.sortedByDescending { it.first }.distinctBy { it.first }
            }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }

    LaunchedEffect(tab, lists) {
        if (tab in lists.indices) {
            loading = true; channels = emptyList()
            channels = withContext(Dispatchers.IO) { loadPlaylist(lists[tab]) }
            loading = false
        }
    }

    fun start(c: Channel) {
        error = null; qLabel = "تلقائي"; cur = c; ui = true
        Hdr.ref = c.ref; Hdr.ua = c.ua
        player?.let { p ->
            val b = MediaItem.Builder().setUri(c.url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(c.name).build())
            if (c.url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
            p.setMediaItem(b.build()); p.prepare(); p.play()
        }
    }

    fun setQuality(o: TrackSelectionOverride?, label: String) {
        player?.let { p ->
            val b = p.trackSelectionParameters.buildUpon()
            if (o == null) b.clearOverridesOfType(C.TRACK_TYPE_VIDEO) else b.setOverrideForType(o)
            p.trackSelectionParameters = b.build()
        }
        qLabel = label
    }

    fun setFull(on: Boolean) {
        full = on
        act.requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (on) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    fun close() {
        if (full) setFull(false)
        player?.stop(); cur = null; error = null
    }

    fun toggleFav(c: Channel) {
        favs = if (favs.any { it.url == c.url }) favs.filter { it.url != c.url } else favs + c
        store.saveFavs(favs)
    }

    fun addUrl() {
        val u = url.trim()
        if (u.isEmpty()) return
        showAdd = false
        scope.launch {
            val list = withContext(Dispatchers.IO) { loadPlaylist(u) }
            if (list.isEmpty()) start(Channel("بث مباشر", u))
            else {
                if (u !in lists) { lists = lists + u; store.savePlaylists(lists) }
                tab = lists.indexOf(u)
            }
            url = ""
        }
    }

    BackHandler(cur != null) { close() }

    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { player?.let { Video(it) } }
        return
    }

    val c = cur
    if (c != null) {
        val isFav = favs.any { it.url == c.url }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui = !ui }
        ) {
            player?.let { Video(it) }
            error?.let {
                Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            if (ui) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton("رجوع", Modifier.width(80.dp)) { close() }
                    Text(c.name, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    GlassButton(if (isFav) "★" else "☆", Modifier.width(56.dp)) { toggleFav(c) }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton(if (playing) "إيقاف" else "تشغيل", Modifier.weight(1f)) {
                        player?.let { if (it.isPlaying) it.pause() else it.play() }
                    }
                    Box(Modifier.weight(1f)) {
                        GlassButton(qLabel, Modifier.fillMaxWidth()) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("تلقائي") }, onClick = { setQuality(null, "تلقائي"); menu = false })
                            quals.forEach { (h, o) ->
                                DropdownMenuItem(text = { Text("${h}p") }, onClick = { setQuality(o, "${h}p"); menu = false })
                            }
                        }
                    }
                    GlassButton(if (full) "تصغير" else "تكبير", Modifier.weight(1f)) { setFull(!full) }
                    GlassButton("مصغّرة", Modifier.weight(1f), onPip)
                }
            }
        }
        return
    }

    val shown = if (tab == -1) favs else channels

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF040508), Color(0xFF0C0E18))))) {
        Box(Modifier.size(280.dp).offset((-90).dp, (-50).dp).blur(100.dp).background(Color(0xFF4B3AA8).copy(0.45f), CircleShape))
        Box(Modifier.size(240.dp).align(Alignment.BottomEnd).offset(70.dp, 50.dp).blur(100.dp).background(Color(0xFF0A6C94).copy(0.4f), CircleShape))

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Text("Glass IPTV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("★ المفضلة", tab == -1) { tab = -1 } }
                itemsIndexed(lists) { i, u ->
                    Chip(u.substringAfterLast('/').substringBefore('?').removeSuffix(".m3u").ifEmpty { "قائمة" }.take(16), tab == i, onLong = {
                        lists = lists.filterIndexed { j, _ -> j != i }
                        store.savePlaylists(lists)
                        tab = if (lists.isEmpty()) -1 else 0
                    }) { tab = i }
                }
                item { Chip("＋ رابط", false) { showAdd = true } }
            }
            Spacer(Modifier.height(14.dp))
            if (loading) Text("جاري التحميل...", color = Color.White.copy(0.7f), modifier = Modifier.padding(bottom = 8.dp))
            if (tab == -1 && favs.isEmpty()) Text("اضغط مطولاً على أي قناة لإضافتها للمفضلة", color = Color.White.copy(0.6f))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(shown) { ch ->
                    Tile(ch, favs.any { it.url == ch.url }, onLong = { toggleFav(ch) }) { start(ch) }
                }
            }
        }
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("إضافة رابط") },
        text = {
            TextField(value = url, onValueChange = { url = it }, singleLine = true,
                placeholder = { Text("رابط قناة أو قائمة M3U") })
        },
        confirmButton = { TextButton(onClick = { addUrl() }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
    )
}
