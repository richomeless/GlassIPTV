package com.glass.iptv

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String)

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
    .background(Brush.verticalGradient(listOf(Color.White.copy(0.28f), Color.White.copy(0.10f))))
    .border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(r))

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.glass(18.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Video(p: Player) = AndroidView(
    factory = { PlayerView(it).apply { useController = false; player = p } },
    update = { it.player = p },
    modifier = Modifier.fillMaxSize()
)

fun loadPlaylist(u: String): List<Channel> {
    try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        val r = c.inputStream.bufferedReader()
        val first = r.readLine()?.trimStart('\uFEFF')
        if (first?.startsWith("#EXTM3U") != true) { c.disconnect(); return emptyList() }
        val text = r.readText()
        if (text.contains("#EXT-X-")) return emptyList() // قناة HLS واحدة مو قائمة
        val out = mutableListOf<Channel>()
        var name = ""
        text.lineSequence().map { it.trim() }.forEach { l ->
            if (l.startsWith("#EXTINF")) name = l.substringAfterLast(",").trim()
            else if (l.isNotEmpty() && !l.startsWith("#")) { out += Channel(name.ifEmpty { l }, l); name = "" }
        }
        return out
    } catch (e: Exception) { return emptyList() }
}

@Composable
fun App(player: Player?, inPip: Boolean, onPip: () -> Unit) {
    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { player?.let { Video(it) } }
        return
    }
    var url by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(listOf<Channel>()) }
    var loading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(player?.isPlaying == true) }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }

    fun play(u: String, title: String) {
        player?.apply {
            setMediaItem(MediaItem.Builder().setUri(u).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build())
            prepare(); play()
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF2A1B4D))))) {
        Box(Modifier.size(280.dp).offset((-80).dp, (-40).dp).blur(90.dp).background(Color(0xFF7C5CFF), CircleShape))
        Box(Modifier.size(240.dp).align(Alignment.BottomEnd).offset(60.dp, 40.dp).blur(90.dp).background(Color(0xFF00C2FF), CircleShape))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Glass IPTV", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)

            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).glass(24.dp), contentAlignment = Alignment.Center) {
                if (player != null) Video(player) else CircularProgressIndicator(color = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(if (playing) "إيقاف مؤقت" else "تشغيل", Modifier.weight(1f)) {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                GlassButton("نافذة مصغّرة", Modifier.weight(1f), onPip)
            }

            TextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                placeholder = { Text("الصق رابط القناة أو قائمة M3U", color = Color.White.copy(0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().glass(18.dp)
            )
            GlassButton(if (loading) "جاري التحميل..." else "افتح الرابط", Modifier.fillMaxWidth()) {
                val u = url.trim()
                if (u.isEmpty() || loading) return@GlassButton
                scope.launch {
                    loading = true
                    val list = withContext(Dispatchers.IO) { loadPlaylist(u) }
                    if (list.isEmpty()) { channels = emptyList(); play(u, "بث مباشر") } else channels = list
                    loading = false
                }
            }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels) { ch ->
                    Text(ch.name, color = Color.White, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().glass(16.dp).clickable { play(ch.url, ch.name) }.padding(16.dp))
                }
            }
        }
    }
}
