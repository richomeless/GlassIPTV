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
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    fun pipMode(): Boolean = p.getBoolean("pip", true)
    fun setPipMode(b: Boolean) = p.edit().putBoolean("pip", b).apply()
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
        // نطلب أعلى معدل تحديث للشاشة (120Hz) لتبقى الإيماءات سلسة
        if (Build.VERSION.SDK_INT >= 35) window.decorView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        f.addListener({ controller.value = f.get() }, MoreExecutors.directExecutor())
        future = f
        setContent {
            // ثيم داكن عشان القوائم المنبثقة والحوارات تطلع بخلفية داكنة
            MaterialTheme(
                colorScheme = darkColorScheme(
                    surface = Color(0xFF14161D), onSurface = Color(0xFFE6E8F0),
                    surfaceContainer = Color(0xFF191B23), surfaceContainerHigh = Color(0xFF1D2029),
                    background = Color(0xFF0A0B10), onBackground = Color(0xFFE6E8F0)
                )
            ) { App(controller.value, inPip.value) { enterPip() } }
        }
    }

    private fun enterPip() {
        val p = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        enterPictureInPictureMode(p)
    }

    private fun pipOn() = getSharedPreferences("glass", Context.MODE_PRIVATE).getBoolean("pip", true)

    private fun setVideo(on: Boolean) {
        controller.value?.let { c ->
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on).build()
        }
    }

    override fun onUserLeaveHint() {
        if (controller.value?.isPlaying == true && pipOn()) enterPip()
    }

    override fun onStart() { super.onStart(); setVideo(true) }

    // وضع الصوت فقط: نوقف الفيديو لما التطبيق يروح للخلفية عشان نوفر النت والبطارية
    override fun onStop() { super.onStop(); if (!pipOn()) setVideo(false) }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        inPip.value = isInPip
    }

    override fun onDestroy() {
        future?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

// فرش الزجاج مشتركة بين كل العناصر عشان ما تنعاد صياغتها مع كل تمرير
private val GlassFill = Brush.linearGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.06f)))
private val GlassStroke = Brush.linearGradient(listOf(Color.White.copy(0.75f), Color.White.copy(0.08f), Color.White.copy(0.45f)))

// زجاج مضبب حقيقي: تمويه قوي مع رفع التشبع زي زجاج iOS (يشتغل من أندرويد 12)
@androidx.annotation.RequiresApi(31)
fun frostEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect {
    val cm = android.graphics.ColorMatrix().apply { setSaturation(1.7f) }
    val sat = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(cm))
    val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
    return android.graphics.RenderEffect.createChainEffect(sat, blur).asComposeRenderEffect()
}

fun Modifier.glass(r: Dp = 24.dp, clipContent: Boolean = true): Modifier {
    val shape = RoundedCornerShape(r)
    val m = if (clipContent) this.clip(shape) else this
    return m.background(GlassFill, shape).border(1.dp, GlassStroke, shape)
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).glass(18.dp).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

fun mkIcon(p: String): ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).addPath(addPathNodes(p), fill = SolidColor(Color.White)).build()

val IPlay = mkIcon("M8 5v14l11-7z")
val IPause = mkIcon("M6 19h4V5H6v14zm8-14v14h4V5h-4z")
val IBack = mkIcon("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
val IStarOn = mkIcon("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")
val IStarOff = mkIcon("M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z")
val IFull = mkIcon("M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z")
val IFullExit = mkIcon("M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z")
val IPip = mkIcon("M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z")
val IMore = mkIcon("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z")
val IHead = mkIcon("M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z")

@Composable
fun GlassIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).glass(18.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Chip(text: String, sel: () -> Float, modifier: Modifier = Modifier, onLong: () -> Unit = {}, onClick: () -> Unit) {
    // نفس الارتفاع ونفس الشكل لكل الأزرار، والتحديد تظليل حي يتبع السحب (يُقرأ وقت الرسم بس)
    Box(
        modifier.height(40.dp).glass(14.dp)
            .drawBehind {
                drawRoundRect(Color.White.copy(alpha = 0.22f * sel().coerceIn(0f, 1f)), cornerRadius = CornerRadius(14.dp.toPx()))
            }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, maxLines = 1, fontSize = 14.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(c: Channel, fav: Boolean, onLong: () -> Unit, onClick: (Rect) -> Unit) {
    val ctx = LocalContext.current
    // نحفظ مكان البطاقة بدون ما نسبب إعادة رسم، ونستخدمه وقت الضغط لانيميشن الفتح
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        Modifier.combinedClickable(
            onClick = { onClick(holder[0]?.let { if (it.isAttached) it.boundsInRoot() else Rect.Zero } ?: Rect.Zero) },
            onLongClick = onLong
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned { holder[0] = it }.glass(22.dp, clipContent = false),
            contentAlignment = Alignment.Center) {
            if (c.logo.isEmpty()) Text(c.name.take(1), color = Color.White, fontSize = 28.sp)
            else {
                // نصغّر الشعار وقت فك الضغط عشان التمرير يبقى سلس
                val req = remember(c.logo) { ImageRequest.Builder(ctx).data(c.logo).size(180).crossfade(false).build() }
                AsyncImage(model = req, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(14.dp))
            }
            if (fav) Text("★", color = Color(0xFFFFD60A), fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        // سطرين دايماً عشان كل الصفوف بنفس الارتفاع
        Text(c.name, color = Color.White.copy(0.9f), fontSize = 12.sp, maxLines = 2, minLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

// شكل القص: مستطيل يكبر من مكان بطاقة القناة لين يملأ الشاشة
class RevealShape(private val p: Float, private val o: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val l = mix(o.left, 0f, p); val t = mix(o.top, 0f, p)
        val r = mix(o.right, size.width, p); val b = mix(o.bottom, size.height, p)
        val rad = mix(corner, 0f, p)
        return Outline.Rounded(RoundRect(l, t, r, b, CornerRadius(rad, rad)))
    }
}

@Composable
fun Video(p: Player, modifier: Modifier = Modifier) = AndroidView(
    // TextureView عشان الزوم والتدوير يشتغلون صح
    factory = { c -> (LayoutInflater.from(c).inflate(R.layout.player_view, null, false) as PlayerView).apply { player = p } },
    update = { it.player = p },
    modifier = modifier.fillMaxSize()
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
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    val reveal = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Rect.Zero) }
    var closing by remember { mutableStateOf(false) }
    // fullP: 0 = بورتريه، 1 = شاشة كاملة (الفيديو يلف 90 درجة عكس عقارب الساعة)
    val fullP = remember { Animatable(0f) }
    var fpDrag by remember { mutableStateOf(-1f) }   // أثناء السحب بالإصبع: تقدم الشاشة الكاملة الحي
    fun fpNow() = if (fpDrag >= 0f) fpDrag else fullP.value
    // رمي الفيديو للأسفل (سحب للأسفل في البورتريه)
    val dismissA = remember { Animatable(0f) }
    var dismissDrag by remember { mutableStateOf(-1f) }
    var flying by remember { mutableStateOf(false) }   // الفيديو راجع لبطاقته بأنيميشن
    fun dyNow() = if (dismissDrag >= 0f) dismissDrag else dismissA.value
    val cfg = LocalConfiguration.current
    val winPortraitNow by rememberUpdatedState(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
    var vAr by remember { mutableStateOf(0f) }
    val ctlRot by remember { derivedStateOf { fpNow() > 0.5f } }
    val chipX = remember { mutableStateMapOf<Int, Float>() }
    var dotLeft by remember { mutableStateOf(0f) }
    val uiAlpha by animateFloatAsState(
        targetValue = if (ui) 1f else 0f,
        animationSpec = tween(durationMillis = if (ui) 280 else 650, easing = FastOutSlowInEasing),
        label = "ui"
    )
    var quals by remember { mutableStateOf(listOf<Pair<Int, TrackSelectionOverride>>()) }
    var qSel by remember { mutableStateOf<Int?>(null) }
    var vh by remember { mutableStateOf(0) }
    var pipMode by remember { mutableStateOf(store.pipMode()) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // الأزرار تختفي تلقائياً بعد 3 ثواني من ظهورها (يتجدد العد عند أي لمسة على الأزرار)
    var uiTick by remember { mutableStateOf(0) }
    LaunchedEffect(cur, ui, menu, uiTick) {
        if (cur != null && ui && !menu) {
            snapshotFlow { reveal.value }.first { it >= 0.99f }
            delay(3000)
            ui = false
        }
    }
    // حالة الشبكة هنا فوق عشان ترجع لنفس المكان بعد الخروج من القناة
    val gridState = rememberLazyGridState()
    val favState = rememberLazyGridState()
    val pager = rememberPagerState(pageCount = { 2 })
    var mainTab by remember { mutableStateOf(0) }
    var lastMainTab by remember { mutableStateOf(0) }
    // الصفحة 0 = القائمة الرئيسية، الصفحة 1 = المفضلة، والسحب بينهم حي
    LaunchedEffect(tab) {
        if (tab >= 0) {
            mainTab = tab
            if (tab != lastMainTab) { lastMainTab = tab; gridState.scrollToItem(0) }
        }
        val target = if (tab == -1) 1 else 0
        if (pager.currentPage != target) pager.animateScrollToPage(target)
    }
    fun pageProg() = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { pg ->
            if (pg == 1 && tab != -1) tab = -1
            else if (pg == 0 && tab == -1) tab = mainTab
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (isPlaying) error = null }
            override fun onPlayerError(e: PlaybackException) {
                val msg = "تعذّر تشغيل القناة (${e.errorCodeName})"
                error = msg
                val c = cur
                if (c != null) scope.launch { error = msg + "\n" + probe(c) }
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                vh = size.height
                vAr = if (size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 0f
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

    LaunchedEffect(mainTab, lists) {
        if (mainTab in lists.indices) {
            loading = true; channels = emptyList()
            channels = withContext(Dispatchers.IO) { loadPlaylist(lists[mainTab]) }
            loading = false
        }
    }

    fun start(c: Channel, from: Rect? = null) {
        if (closing) return
        error = null; qSel = null; vh = 0; ui = true; zoom = 1f; panX = 0f; panY = 0f
        Hdr.ref = c.ref; Hdr.ua = c.ua
        val dm = ctx.resources.displayMetrics
        val cx = dm.widthPixels / 2f; val cy = dm.heightPixels / 2f
        origin = if (from != null && from.width > 0f) from else Rect(cx - 80f, cy - 80f, cx + 80f, cy + 80f)
        cur = c
        scope.launch {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
        player?.let { p ->
            val b = MediaItem.Builder().setUri(c.url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(c.name).apply {
                    // لوقو القناة يظهر في إشعار التشغيل وبطاقة الميديا
                    if (c.logo.startsWith("http")) setArtworkUri(android.net.Uri.parse(c.logo))
                }.build())
            if (c.url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
            p.setMediaItem(b.build()); p.prepare(); p.play()
        }
    }

    fun setQuality(o: TrackSelectionOverride?, h: Int?) {
        player?.let { p ->
            val b = p.trackSelectionParameters.buildUpon()
            if (o == null) b.clearOverridesOfType(C.TRACK_TYPE_VIDEO) else b.setOverrideForType(o)
            p.trackSelectionParameters = b.build()
        }
        qSel = h
    }

    fun bars(show: Boolean) {
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (show) c.show(WindowInsetsCompat.Type.systemBars())
        else {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // يكمّل أنيميشن الشاشة الكاملة: دخول (وبعد ثانية نقلب واجهة النظام فعلياً)، أو خروج
    fun settleFull(p0: Float, target: Float) {
        scope.launch {
            fullP.snapTo(p0); fpDrag = -1f
            if (target >= 1f) {
                fullP.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                delay(1000)
                if (full) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                if (!winPortraitNow) {
                    // النظام أفقي فعلياً: نرجّعه بورتريه ثم الفيديو ينكمش بالأنيميشن
                    fullP.snapTo(1f)
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    withTimeoutOrNull(2000) { snapshotFlow { winPortraitNow }.first { it } }
                }
                fullP.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun setFull(on: Boolean) {
        if (on == full) return
        full = on
        bars(!on)
        settleFull(fpNow(), if (on) 1f else 0f)
    }

    // الخروج: الفيديو ينكمش راجع لمكان البطاقة، وبعدها نوقف التشغيل
    fun close() {
        if (closing || cur == null) return
        closing = true
        if (full) { full = false; bars(true); act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        scope.launch {
            if (fullP.value != 0f) fullP.snapTo(0f)
            reveal.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            player?.stop(); cur = null; error = null
            zoom = 1f; panX = 0f; panY = 0f
            closing = false
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
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

    BackHandler(cur != null) { if (full) setFull(false) else close() }

    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { player?.let { Video(it) } }
        return
    }

    val favUrls = remember(favs) { favs.map { it.url }.toHashSet() }
    val hh = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 112.dp
    val layer = rememberGraphicsLayer()
    val frost = remember { if (Build.VERSION.SDK_INT >= 31) frostEffect(110f) else null }
    var menuMore by remember { mutableStateOf(false) }
    // لمعة الهيدر: حلقة إطارات تشتغل فقط وأنت في الواجهة الرئيسية
    var shT by remember { mutableStateOf(0f) }
    LaunchedEffect(cur == null) {
        if (cur == null) {
            val t0 = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val ph = ((t - t0) / 1_000_000L) % 7800L
                shT = if (ph < 4400L) ph / 4400f else 1f
            }
        }
    }
    val c = cur

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            // الواجهة الرئيسية تصغر وتخفت شوي وقت ما الفيديو يفتح فوقها
            val dp = (dyNow() / (size.height * 0.6f)).coerceIn(0f, 1f)
            val p = (reveal.value * (1f - dp)).coerceIn(0f, 1f)
            val k = 1f - 0.07f * p
            scaleX = k; scaleY = k; alpha = 1f - 0.6f * p
        }) {
            Box(Modifier.fillMaxSize().background(Color(0xFF040508))) {
                // الخلفية والقنوات كلها تتسجل في طبقة وحدة، وتنرسم تحت الهيدر فقط
                Box(Modifier.fillMaxSize().drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    clipRect(top = hh.toPx()) { drawLayer(layer) }
                }) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF040508), Color(0xFF0C0E18)))))
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Brush.radialGradient(listOf(Color(0x664B3AA8), Color.Transparent), Offset(size.width * 0.1f, size.height * 0.05f), size.width * 0.9f))
                        drawRect(Brush.radialGradient(listOf(Color(0x550A6C94), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.95f), size.width * 0.9f))
                    })
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        val isFavPage = page == 1
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = if (isFavPage) favState else gridState,
                                columns = GridCells.Adaptive(96.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = hh + 8.dp, bottom = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(if (isFavPage) favs else channels, contentType = { "tile" }) { ch ->
                                    Tile(ch, ch.url in favUrls, onLong = { toggleFav(ch) }) { r -> start(ch, r) }
                                }
                            }
                            if (isFavPage && favs.isEmpty()) Text("اضغط مطولاً على أي قناة لإضافتها للمفضلة", color = Color.White.copy(0.6f),
                                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                            else if (!isFavPage && loading) Text("جاري التحميل...", color = Color.White.copy(0.7f),
                                modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                // الهيدر: زجاج مضبب حقيقي، نفس الطبقة لكن مموهة بقوة
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(hh)) {
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer { renderEffect = frost; clip = true }
                        .drawBehind { drawLayer(layer) })
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.07f)))))
                    // حافة الهيدر السفلية: لمعة بطيئة تمر من اليسار لليمين مع توهج ناعم حولها
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 30.dp).fillMaxWidth().height(60.dp).drawBehind {
                        val yb = size.height / 2f
                        val bw = size.width * 0.4f
                        val x = -bw + (size.width + bw * 2f) * shT
                        val cx = x + bw / 2f
                        val c0 = Offset(cx, yb)
                        scale(scaleX = 8f, scaleY = 1f, pivot = c0) {
                            drawCircle(Brush.radialGradient(listOf(Color(0x30AFC4FF), Color.Transparent), center = c0, radius = 24.dp.toPx()),
                                radius = 24.dp.toPx(), center = c0)
                        }
                        scale(scaleX = 4f, scaleY = 1f, pivot = c0) {
                            drawCircle(Brush.radialGradient(listOf(Color(0x80FFFFFF), Color(0x40AFC4FF), Color.Transparent), center = c0, radius = 28.dp.toPx()),
                                radius = 28.dp.toPx(), center = c0)
                        }
                        drawRect(Brush.horizontalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.12f), Color.White.copy(0.45f))),
                            topLeft = Offset(0f, yb - 1.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), startX = x, endX = x + bw),
                            topLeft = Offset(0f, yb - 1.5f.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                    })
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Glass IPTV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                            Box {
                                Box(Modifier.size(40.dp).glass(14.dp).clickable { menuMore = true }, contentAlignment = Alignment.Center) {
                                    Icon(IMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(expanded = menuMore, onDismissRequest = { menuMore = false }) {
                                    DropdownMenuItem(text = { Text("إضافة رابط") }, onClick = { menuMore = false; showAdd = true })
                                }
                            }
                        }
                        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            item {
                                Chip("★ المفضلة", { pageProg() }, Modifier.onGloballyPositioned { chipX[-1] = it.positionInRoot().x + it.size.width / 2f }) { tab = -1 }
                            }
                            itemsIndexed(lists) { i, u ->
                                val a by animateFloatAsState(targetValue = if (mainTab == i) 1f else 0f, animationSpec = tween(220), label = "sel")
                                Chip(u.substringAfterLast('/').substringBefore('?').removeSuffix(".m3u").ifEmpty { "قائمة" }.take(16), { a * (1f - pageProg()) },
                                    Modifier.onGloballyPositioned { chipX[i] = it.positionInRoot().x + it.size.width / 2f }, onLong = {
                                    lists = lists.filterIndexed { j, _ -> j != i }
                                    store.savePlaylists(lists)
                                    tab = if (lists.isEmpty()) -1 else 0
                                }) { tab = i }
                            }
                        }
                        // النقطة الديناميكية: تتبع السحب بين الصفحتين وتتمدد وهي تتحرك
                        val mainX by animateFloatAsState(
                            targetValue = chipX[mainTab] ?: 0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 260f), label = "mx"
                        )
                        Box(Modifier.fillMaxWidth().height(12.dp)
                            .onGloballyPositioned { dotLeft = it.positionInRoot().x }
                            .drawBehind {
                                val favX = chipX[-1] ?: 0f
                                val pageP = pageProg()
                                val x = mix(mainX, favX, pageP) - dotLeft
                                val moving = kotlin.math.sin(Math.PI.toFloat() * pageP)
                                val extra = kotlin.math.abs((chipX[mainTab] ?: 0f) - mainX).coerceAtMost(160f)
                                val w = 16.dp.toPx() + moving * 36.dp.toPx() + extra * 0.4f
                                val h = 5.dp.toPx()
                                drawRoundRect(Color.White.copy(0.28f), Offset(x - w / 2f - 3.dp.toPx(), 1.dp.toPx()),
                                    Size(w + 6.dp.toPx(), h + 4.dp.toPx()), CornerRadius(h))
                                drawRoundRect(Color.White.copy(0.95f), Offset(x - w / 2f, 3.dp.toPx()), Size(w, h), CornerRadius(h / 2f))
                            })
                    }
                }
            }
        }

        if (c != null) {
            val isFav = favs.any { it.url == c.url }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        // الإطار يتوسع/ينكمش بين البطاقة والشاشة الكاملة
                        val p = reveal.value.coerceIn(0f, 1f)
                        shape = RevealShape(p, origin, 22.dp.toPx())
                        clip = true
                        alpha = (p * 4f).coerceIn(0f, 1f)
                    }
                    .drawBehind {
                        // الخلفية السوداء تختفي أثناء السحب للأسفل عشان تشوف الواجهة وراء الفيديو
                        val bgA = if (flying) 0f else (1f - dyNow() / (size.height * 0.12f)).coerceIn(0f, 1f)
                        drawRect(Color.Black, alpha = bgA)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val vt = VelocityTracker()
                            vt.addPointerInputChange(down)
                            var moved = false
                            var released = false
                            var mode = 0            // 0 لا شي، 1 دخول شاشة كاملة، 2 خروج منها
                            var lastPos = down.position
                            val dist = 280.dp.toPx()
                            // الربع السفلي من الشاشة (منطقة إيماءة الخروج من التطبيق) ما يفعّل الشاشة الكاملة
                            val blockUp = !full && down.position.y > size.height * 0.8f
                            var prog = if (full) 1f else 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                vt.addPointerInputChange(ch)
                                if (!ch.pressed) { released = true; break }
                                val tot = ch.position - down.position
                                // اتجاهات الصورة: بعد التدوير مع عقارب الساعة، فوق الصورة = يمين الجهاز
                                val rot = full && size.height > size.width
                                val ay = if (rot) -tot.x else tot.y
                                val ax = if (rot) tot.y else tot.x
                                if (!moved && tot.getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                    if (zoom <= 1f && kotlin.math.abs(ay) > kotlin.math.abs(ax)) {
                                        if (!full && ay < 0f && !blockUp) mode = 1
                                        else if (full && ay > 0f) mode = 2
                                        else if (!full && ay > 0f) mode = 3
                                    }
                                }
                                if (moved && zoom > 1f) {
                                    // تحريك الفيديو بعد الزوم
                                    val d = ch.position - lastPos
                                    val mx = (zoom - 1f) * size.width / 2f
                                    val my = (zoom - 1f) * size.height / 2f
                                    panX = (panX + d.x).coerceIn(-mx, mx)
                                    panY = (panY + d.y).coerceIn(-my, my)
                                    ch.consume()
                                } else if (mode == 3) {
                                    // سحب للأسفل في البورتريه: الفيديو ينزل مع الإصبع
                                    dismissDrag = ay.coerceAtLeast(0f)
                                    ch.consume()
                                } else if (mode != 0) {
                                    // الفيديو يتحرك مع الإصبع حي
                                    prog = if (mode == 1) (-ay / dist).coerceIn(0f, 1f) else 1f - (ay / dist).coerceIn(0f, 1f)
                                    fpDrag = prog
                                    ch.consume()
                                }
                                lastPos = ch.position
                            }
                            if (mode == 3) {
                                val v = vt.calculateVelocity()
                                val hPx = size.height.toFloat()
                                val dyEnd = dismissDrag.coerceAtLeast(0f)
                                val goBack = dyEnd > hPx * 0.18f || v.y > 1600f
                                if (goBack) { closing = true; flying = true }
                                scope.launch {
                                    dismissA.snapTo(dyEnd); dismissDrag = -1f
                                    if (goBack) {
                                        // الفيديو يرجع لمكان القناة اللي فتحته منها
                                        launch { dismissA.animateTo(0f, tween(480, easing = FastOutSlowInEasing)) }
                                        reveal.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                                        player?.stop(); cur = null; error = null
                                        zoom = 1f; panX = 0f; panY = 0f
                                        flying = false; closing = false
                                    } else {
                                        dismissA.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                                    }
                                }
                                return@awaitEachGesture
                            }
                            if (mode != 0) {
                                // عند الإفلات: نكمل للنهاية أو نرجع حسب المسافة والسرعة
                                val v = vt.calculateVelocity()
                                val rot = full && size.height > size.width
                                val vy = if (rot) -v.x else v.y
                                val target = when {
                                    vy < -1200f -> 1f
                                    vy > 1200f -> 0f
                                    else -> if (prog > 0.5f) 1f else 0f
                                }
                                full = target == 1f
                                bars(target == 0f)
                                settleFull(prog, target)
                                return@awaitEachGesture
                            }
                            if (!released || moved) return@awaitEachGesture
                            // ضغطة وحدة: إظهار/إخفاء الأزرار. ضغطتين مع سحب: زوم
                            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                            if (second == null) { ui = !ui; return@awaitEachGesture }
                            var lastP = second.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == second.id } ?: break
                                if (!ch.pressed) break
                                val d = ch.position - lastP
                                lastP = ch.position
                                val dyPic = if (full && size.height > size.width) -d.x else d.y
                                if (dyPic != 0f) {
                                    zoom = (zoom * kotlin.math.exp(dyPic / 450f)).coerceIn(1f, 4f)
                                    val mx = (zoom - 1f) * size.width / 2f
                                    val my = (zoom - 1f) * size.height / 2f
                                    panX = panX.coerceIn(-mx, mx); panY = panY.coerceIn(-my, my)
                                    ch.consume()
                                }
                            }
                        }
                    }
            ) {
              Box(Modifier.fillMaxSize().graphicsLayer {
                // الفيديو يطير لبطاقة القناة: يصغر ويتحرك لمركزها، ومع السحب ينزل مع الإصبع
                val p = reveal.value.coerceIn(0f, 1f)
                val k0 = mix(origin.width.coerceAtLeast(1f) / size.width, 1f, p)
                val dy = dyNow()
                val kd = 1f - 0.2f * (dy / size.height).coerceIn(0f, 1f)
                scaleX = k0 * kd; scaleY = k0 * kd
                translationX = (origin.center.x - size.width / 2f) * (1f - p)
                translationY = (origin.center.y - size.height / 2f) * (1f - p) + dy
              }) {
                player?.let {
                    Video(it, Modifier.graphicsLayer {
                        val fp = fpNow()
                        val w = size.width; val h = size.height
                        val portrait = h > w
                        val ar = if (vAr > 0f) vAr else 16f / 9f
                        // معامل التكبير عشان الفيديو يملأ الشاشة بعد التدوير
                        val sFull = if (portrait) kotlin.math.min(h, w * ar) / kotlin.math.min(w, h * ar) else 1f
                        // خروج من أفقي حقيقي: الفيديو ينزل ويصغر مع الإصبع
                        val q = if (!portrait && full) 1f - fp else 0f
                        val k = (1f + (sFull - 1f) * fp) * zoom * (1f - 0.15f * q)
                        scaleX = k; scaleY = k
                        rotationZ = if (portrait) 90f * fp else 0f
                        translationX = panX; translationY = panY + q * 0.4f * h
                    })
                }
                // الأزرار تتدور مع الفيديو: نعيد ترتيبها بمقاس الشاشة بعد التدوير
                Box(Modifier.fillMaxSize().layout { m, cs ->
                    val rot = ctlRot && cs.maxHeight > cs.maxWidth
                    val w = if (rot) cs.maxHeight else cs.maxWidth
                    val h = if (rot) cs.maxWidth else cs.maxHeight
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(cs.maxWidth, cs.maxHeight) {
                        pl.placeWithLayer((cs.maxWidth - w) / 2, (cs.maxHeight - h) / 2) { rotationZ = if (rot) 90f else 0f }
                    }
                }) {
                    Box(Modifier.fillMaxSize()) {
                        error?.let {
                            Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        }
                        if (uiAlpha > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer {
                            val fp = fpNow()
                    val steady = kotlin.math.max((1f - fp * 4f).coerceIn(0f, 1f), ((fp - 0.75f) * 4f).coerceIn(0f, 1f))
                    val dd = if (flying) 0f else (1f - dyNow() / (size.height * 0.1f)).coerceIn(0f, 1f)
                    alpha = uiAlpha * ((reveal.value - 0.6f) / 0.4f).coerceIn(0f, 1f) * steady * dd
                        }) {
                            Row(
                                Modifier.align(Alignment.TopStart).graphicsLayer { translationY = -(1f - uiAlpha) * 28.dp.toPx() }
                                    .fillMaxWidth().statusBarsPadding().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassIcon(IBack, Modifier.width(64.dp)) { close() }
                                Text(c.name, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                GlassIcon(if (isFav) IStarOn else IStarOff, Modifier.width(64.dp), if (isFav) Color(0xFFFFD60A) else Color.White) { uiTick++; toggleFav(c) }
                            }
                            Row(
                                Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = (1f - uiAlpha) * 28.dp.toPx() }
                                    .fillMaxWidth().navigationBarsPadding().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassIcon(if (playing) IPause else IPlay, Modifier.weight(1f)) {
                                    uiTick++
                                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                                }
                                Box(Modifier.weight(1f)) {
                                    GlassButton(if (qSel != null) "$qSel" else if (vh > 0) "$vh" else "—", Modifier.fillMaxWidth()) { menu = true }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("تلقائي") }, onClick = { setQuality(null, null); menu = false })
                                        quals.forEach { (h, o) ->
                                            DropdownMenuItem(text = { Text("${h}p") }, onClick = { setQuality(o, h); menu = false })
                                        }
                                    }
                                }
                                GlassIcon(if (full) IFullExit else IFull, Modifier.weight(1f)) { uiTick++; setFull(!full) }
                                GlassIcon(if (pipMode) IPip else IHead, Modifier.weight(1f)) {
                                    uiTick++; pipMode = !pipMode; store.setPipMode(pipMode)
                                    Toast.makeText(ctx, if (pipMode) "نافذة مصغّرة عند الخروج" else "صوت فقط عند الخروج", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                    }
                }
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
