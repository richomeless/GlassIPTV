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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

data class Channel(val name: String, val url: String, val logo: String = "")

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
    fun playlists(): List<String> =
        p.getString("pl", null)?.split("\n")?.filter { it.isNotBlank() }
            ?: listOf("https://iptv-org.github.io/iptv/languages/ara.m3u")
    fun savePlaylists(l: List<String>) = p.edit().putString("pl", l.joinToString("\n")).apply()
    fun favs(): List<Channel> = (p.getString("fav", "") ?: "").split("\n").filter { it.isNotBlank() }.map {
        val f = it.split("\t")
        Channel(f[0], f.getOrElse(1) { "" }, f.getOrElse(2) { "" })
    }
    fun saveFavs(l: List<Channel>) =
        p.edit().putString("fav", l.joinToString("\n") { "${it.name}\t${it.url}\t${it.logo}" }).apply()
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

@Composable
fun ChannelRow(c: Channel, fav: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(16.dp).clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = c.logo.ifEmpty { null }, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.08f))
        )
        Spacer(Modifier.width(12.dp))
        Text(c.name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            if (fav) "★" else "☆", fontSize = 24.sp,
            color = if (fav) Color(0xFFFFD60A) else Color.White.copy(0.7f),
            modifier = Modifier.clickable(onClick = onFav).padding(8.dp)
        )
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
        var name = ""; var logo = ""
        text.lineSequence().map { it.trim() }.forEach { l ->
            if (l.startsWith("#EXTINF")) {
                name = l.substringAfterLast(",").trim().replace("\t", " ")
                logo = logoRe.find(l)?.groupValues?.get(1) ?: ""
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                out += Channel(name.ifEmpty { l }, l, logo); name = ""; logo = ""
            }
        }
        return out
    } catch (e: Exception) { return emptyList() }
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
    var playing by remember { mutableStateOf(player?.isPlaying == true) }
    var error by remember { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    var quals by remember { mutableStateOf(listOf<Pair<Int, TrackSelectionOverride>>()) }
    var qLabel by remember { mutableStateOf("تلقائي") }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (isPlaying) error = null }
            override fun onPlayerError(e: PlaybackException) { error = "تعذّر تشغيل القناة (${e.errorCodeName})" }
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
        error = null; qLabel = "تلقائي"
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

    fun toggleFav(c: Channel) {
        favs = if (favs.any { it.url == c.url }) favs.filter { it.url != c.url } else favs + c
        store.saveFavs(favs)
    }

    BackHandler(full) { setFull(false) }

    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { player?.let { Video(it) } }
        return
    }
    if (full) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            player?.let { Video(it) }
            GlassButton("✕", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).width(64.dp)) { setFull(false) }
        }
        return
    }

    val shown = if (tab == -1) favs else channels

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF040508), Color(0xFF0C0E18))))) {
        Box(Modifier.size(280.dp).offset((-90).dp, (-50).dp).blur(100.dp).background(Color(0xFF4B3AA8).copy(0.45f), CircleShape))
        Box(Modifier.size(240.dp).align(Alignment.BottomEnd).offset(70.dp, 50.dp).blur(100.dp).background(Color(0xFF0A6C94).copy(0.4f), CircleShape))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Glass IPTV", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)

            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).glass(24.dp), contentAlignment = Alignment.Center) {
                if (player != null) Video(player) else CircularProgressIndicator(color = Color.White)
            }
            error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                GlassButton("تكبير", Modifier.weight(1f)) { setFull(true) }
                GlassButton("مصغّرة", Modifier.weight(1f), onPip)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("★ المفضلة", tab == -1) { tab = -1 } }
                itemsIndexed(lists) { i, u ->
                    Chip(u.substringAfterLast('/').substringBefore('?').removeSuffix(".m3u").ifEmpty { "قائمة" }.take(16), tab == i, onLong = {
                        lists = lists.filterIndexed { j, _ -> j != i }
                        store.savePlaylists(lists)
                        tab = if (lists.isEmpty()) -1 else 0
                    }) { tab = i }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("رابط قناة أو قائمة M3U", color = Color.White.copy(0.5f)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).glass(18.dp)
                )
                GlassButton("إضافة", Modifier.width(80.dp)) {
                    val u = url.trim()
                    if (u.isEmpty()) return@GlassButton
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
            }

            if (loading) Text("جاري التحميل...", color = Color.White.copy(0.7f))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { ch ->
                    ChannelRow(ch, favs.any { it.url == ch.url }, { start(ch) }, { toggleFav(ch) })
                }
            }
        }
    }
}
