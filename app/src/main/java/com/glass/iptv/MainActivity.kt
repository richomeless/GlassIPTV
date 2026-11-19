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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Path
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
    fun names(): Map<String, String> = (p.getString("pn", "") ?: "").split("\n").filter { it.contains("\t") }
        .associate { val i = it.indexOf('\t'); it.substring(0, i) to it.substring(i + 1) }
    fun saveNames(m: Map<String, String>) = p.edit().putString("pn", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    // ترتيب خاص لكل قائمة: المفتاح "fav" للمفضلة أو رابط القائمة. 0 أبجدي، 1 الأكثر تشغيلاً
    fun sorts(): Map<String, Int> = (p.getString("sorts", "") ?: "").split("\n").filter { it.contains("\t") }
        .mapNotNull { l -> val i = l.lastIndexOf('\t'); l.substring(i + 1).toIntOrNull()?.let { m -> l.substring(0, i) to m } }.toMap()
    fun saveSorts(m: Map<String, Int>) = p.edit().putString("sorts", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    fun counts(): Map<String, Int> = (p.getString("cnt", "") ?: "").split("\n").filter { it.contains("\t") }
        .mapNotNull { l -> val i = l.lastIndexOf('\t'); l.substring(i + 1).toIntOrNull()?.let { n -> l.substring(0, i) to n } }.toMap()
    fun bumpCount(url: String) {
        val m = counts().toMutableMap(); m[url] = (m[url] ?: 0) + 1
        p.edit().putString("cnt", m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }
    // وضع الألوان: 0 حسب النظام، 1 فاتح، 2 داكن
    fun themeMode(): Int = p.getInt("tmode", 0)
    fun setThemeMode(m: Int) = p.edit().putInt("tmode", m).apply()
    // الثيم: 0 هوية التطبيق، 1 Material You
    fun themeStyle(): Int = p.getInt("tstyle", 0)
    fun setThemeStyle(m: Int) = p.edit().putInt("tstyle", m).apply()
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
    private val openReq = mutableStateOf(0)
    private val themeMode = mutableIntStateOf(0)
    private val themeStyle = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.install(applicationContext)
        handleOpenIntent(intent)
        enableEdgeToEdge()
        // نطلب أعلى معدل تحديث للشاشة (120Hz) لتبقى الإيماءات سلسة
        if (Build.VERSION.SDK_INT >= 35) window.decorView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        f.addListener({ controller.value = f.get() }, MoreExecutors.directExecutor())
        future = f
        val themeStore = Store(applicationContext)
        themeMode.intValue = themeStore.themeMode()
        themeStyle.intValue = themeStore.themeStyle()
        setContent {
            // وضع الألوان (نظام/فاتح/داكن) والثيم (هوية التطبيق/Material You) من الإعدادات
            val darkTheme = when (themeMode.intValue) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val appCtx = LocalContext.current
            val dyn = if (themeStyle.intValue == 1) dynamicScheme(appCtx, darkTheme) else null
            val scheme = dyn ?: if (darkTheme) darkColorScheme(
                surface = Color(0xFF14161D), onSurface = Color(0xFFE6E8F0),
                surfaceContainer = Color(0xFF191B23), surfaceContainerHigh = Color(0xFF1D2029),
                background = Color(0xFF0A0B10), onBackground = Color(0xFFE6E8F0)
            ) else lightColorScheme(
                surface = Color(0xFFF7F8FE), onSurface = Color(0xFF12141C),
                surfaceContainer = Color(0xFFEDEFF9), surfaceContainerHigh = Color(0xFFE8EBF7),
                background = Color(0xFFF2F4FB), onBackground = Color(0xFF12141C)
            )
            val base = if (darkTheme) DarkPal else LightPal
            val pal = if (dyn != null) materialYouPal(dyn, base) else base
            MaterialTheme(typography = appTypography(), colorScheme = scheme) {
                CompositionLocalProvider(LocalPal provides pal) {
                    App(controller.value, inPip.value, openReq.value, themeMode.intValue, themeStyle.intValue,
                        { m, st ->
                            themeMode.intValue = m; themeStyle.intValue = st
                            themeStore.setThemeMode(m); themeStore.setThemeStyle(st)
                        }) { enterPip() }
                }
            }
        }
    }

    // الضغط على إشعار التشغيل: نفتح الفيديو الشغال
    private fun handleOpenIntent(i: android.content.Intent?) {
        if (i?.getBooleanExtra("open_player", false) == true) {
            i.removeExtra("open_player")
            openReq.value = openReq.value + 1
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
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
        trace("onUserLeaveHint playing=${controller.value?.isPlaying} pip=${pipOn()}")
        if (controller.value?.isPlaying == true && pipOn()) enterPip()
    }

    override fun onStart() { super.onStart(); trace("activity onStart"); setVideo(true) }
    override fun onResume() { super.onResume(); trace("activity onResume") }
    override fun onPause() { super.onPause(); trace("activity onPause") }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); trace("window focus=$hasFocus") }

    // وضع الصوت فقط: نوقف الفيديو لما التطبيق يروح للخلفية عشان نوفر النت والبطارية
    override fun onStop() { super.onStop(); trace("activity onStop"); if (!pipOn()) setVideo(false) }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        trace("pipChanged=$isInPip")
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

// تمويه ناعم للإضاءة المحيطية: تمويه قوي، تشبع خفيف، وأطرافها تتلاشى
@androidx.annotation.RequiresApi(31)
fun ambientEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect {
    val cm = android.graphics.ColorMatrix().apply { setSaturation(1.1f) }
    val sat = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(cm))
    val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.DECAL)
    return android.graphics.RenderEffect.createChainEffect(sat, blur).asComposeRenderEffect()
}

// ألوان الواجهة: داكن وفاتح (يتبدلون تلقائياً حسب ثيم الجوال). المشغّل دايماً داكن.
class Pal(
    val dark: Boolean,
    val bg1: Color, val bg2: Color, val blob1: Color, val blob2: Color,
    val text: Color, val textDim: Color,
    val glassFill: Brush, val glassStroke: Brush,
    val chipHi: Color, val headerTint: Brush, val rim: Brush,
    val shim: Color, val dot: Color
)
val DarkPal = Pal(
    true, Color(0xFF040508), Color(0xFF0C0E18), Color(0x664B3AA8), Color(0x550A6C94),
    Color.White, Color.White.copy(0.65f), GlassFill, GlassStroke,
    Color.White.copy(0.22f),
    Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.07f))),
    Brush.horizontalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.12f), Color.White.copy(0.45f))),
    Color.White, Color.White
)
val LightPal = Pal(
    false, Color(0xFFF2F4FB), Color(0xFFE3E7F6), Color(0x558C7BFF), Color(0x4458B6E8),
    Color(0xFF12141C), Color(0xFF12141C).copy(0.6f),
    Brush.linearGradient(listOf(Color.White.copy(0.80f), Color.White.copy(0.42f))),
    Brush.linearGradient(listOf(Color.White.copy(0.95f), Color(0xFF8E98BD).copy(0.30f), Color.White.copy(0.75f))),
    Color(0xFF5B6CFF).copy(0.20f),
    Brush.verticalGradient(listOf(Color.White.copy(0.62f), Color.White.copy(0.36f))),
    Brush.horizontalGradient(listOf(Color(0xFF8E98BD).copy(0.55f), Color(0xFF8E98BD).copy(0.15f), Color(0xFF8E98BD).copy(0.45f))),
    Color(0xFF3F4BD6), Color(0xFF3F4BD6)
)
val LocalPal = staticCompositionLocalOf { DarkPal }

// ألوان Material You الديناميكية (أندرويد 12+) من خلفية الجوال، وتنرجع null على الأجهزة الأقدم
fun dynamicScheme(ctx: Context, dark: Boolean): ColorScheme? =
    if (Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null

// نبني ألوان الواجهة (الخلفية، البقع، النص، النقطة) من مخطط Material You، ونبقي زجاج التطبيق كما هو
fun materialYouPal(cs: ColorScheme, base: Pal) = Pal(
    base.dark, cs.surface, cs.surfaceContainer,
    cs.primary.copy(alpha = if (base.dark) 0.40f else 0.30f), cs.tertiary.copy(alpha = if (base.dark) 0.34f else 0.26f),
    cs.onSurface, cs.onSurface.copy(alpha = 0.65f), base.glassFill, base.glassStroke,
    cs.primary.copy(alpha = 0.22f), base.headerTint, base.rim,
    if (base.dark) base.shim else cs.primary, cs.primary
)

// صف خيار في الإعدادات
@Composable
fun SettingChoice(t: String, sel: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(50.dp).glass(16.dp)
            .then(if (sel) Modifier.background(pal.chipHi).border(1.5.dp, pal.dot.copy(0.9f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(t, color = pal.text.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 14.sp,
            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

// صفحة الإعدادات: وضع الألوان + الثيم
@Composable
fun SettingsDialog(mode: Int, style: Int, onMode: (Int) -> Unit, onStyle: (Int) -> Unit, onClose: () -> Unit) {
    val pal = LocalPal.current
    val view = androidx.compose.ui.platform.LocalView.current
    val myOk = Build.VERSION.SDK_INT >= 31
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2)))
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(pal.blob1, Color.Transparent), Offset(size.width * 0.1f, size.height * 0.05f), size.width * 0.9f))
                    drawRect(Brush.radialGradient(listOf(pal.blob2, Color.Transparent), Offset(size.width * 0.95f, size.height * 0.95f), size.width * 0.9f))
                }
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).glass(14.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(IBack, contentDescription = null, tint = pal.text, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("الإعدادات", color = pal.text, fontSize = 24.sp, fontFamily = TitleFont,
                        fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.height(28.dp))
                Text("وضع الألوان", color = pal.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("حسب النظام", "فاتح", "داكن").forEachIndexed { i, t ->
                        SettingChoice(t, mode == i, true, Modifier.weight(1f)) { if (mode != i) { buzz(view, false); onMode(i) } }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("الثيم", color = pal.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChoice("هوية التطبيق", style == 0, true, Modifier.weight(1f)) { if (style != 0) { buzz(view, false); onStyle(0) } }
                    SettingChoice("Material You", style == 1, myOk, Modifier.weight(1f)) { if (style != 1) { buzz(view, false); onStyle(1) } }
                }
                if (!myOk) Text("Material You يحتاج أندرويد 12 أو أحدث", color = pal.textDim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                else Text("ألوان الواجهة تتبع خلفية الجوال", color = pal.textDim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
        }
    }
}

fun Modifier.glass(r: Dp = 24.dp, clipContent: Boolean = true): Modifier = composed {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(r)
    (if (clipContent) Modifier.clip(shape) else Modifier).background(pal.glassFill, shape).border(1.dp, pal.glassStroke, shape)
}

// هزة خفيفة: نجرب هزة النظام أول، وإذا ما اشتغلت نجرب الاهتزاز المباشر
fun buzz(v: View, release: Boolean) {
    val ok = try {
        val c = if (release && Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        v.performHapticFeedback(c)
    } catch (_: Throwable) { false }
    if (!ok) try {
        val ctx = v.context
        val vib: android.os.Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        vib.vibrate(android.os.VibrationEffect.createOneShot(if (release) 10L else 16L, 150))
    } catch (_: Throwable) {}
}

// زر حي: يصغر وهو مضغوط ويرتد، يتوهج عند اللمس، وإطاره يلمع بدوران بطيء جداً وسلس، مع هزة عند الضغط وعند الرفع
@Composable
fun Modifier.liveGlass(r: Dp, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val armed = remember { booleanArrayOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) { armed[0] = true; buzz(view, false) }
        else if (armed[0]) { armed[0] = false; buzz(view, true) }
    }
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "sc")
    val gl by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 90 else 420), label = "gl")
    val ang by rememberInfiniteTransition(label = "rot").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing)), label = "ang"
    )
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .glass(r)
        .drawWithContent {
            drawContent()
            val cr = CornerRadius(r.toPx())
            // توهج عند اللمس
            if (gl > 0.01f) drawRoundRect(
                Brush.radialGradient(listOf(Color.White.copy(0.55f * gl), Color.White.copy(0.12f * gl)), center = center, radius = size.maxDimension * 0.7f),
                cornerRadius = cr
            )
            // إطار لامع يدور ببطء: رأس لامع وذيل طويل يتلاشى بنعومة
            val t = ang / 360f
            val n = 48
            val stops = Array(n + 1) { i ->
                val pos = i / n.toFloat()
                var d = pos - t
                if (d > 0.5f) d -= 1f
                if (d < -0.5f) d += 1f
                val u = if (d <= 0f) (1f + d / 0.30f).coerceIn(0f, 1f) else (1f - d / 0.08f).coerceIn(0f, 1f)
                val k = u * u * (3f - 2f * u)
                pos to Color(0.74f + 0.26f * k, 0.87f + 0.13f * k, 1f, k * k)
            }
            val w = 2.dp.toPx()
            drawRoundRect(Brush.sweepGradient(*stops, center = center), topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w), cornerRadius = cr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(w), alpha = 0.8f + 0.2f * gl)
        }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

// خط التطبيق: Instrument Sans (رزين وهادئ). خط الشعار: Tektur (فضائي). الحروف العربية تنزل على خط النظام تلقائياً
val AppFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.instrumentsans_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.instrumentsans_regular, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.instrumentsans_bold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.instrumentsans_bold, FontWeight.Bold)
)
val TitleFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.tektur_medium, FontWeight.Medium)
)

fun appTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = AppFont), displayMedium = d.displayMedium.copy(fontFamily = AppFont),
        displaySmall = d.displaySmall.copy(fontFamily = AppFont), headlineLarge = d.headlineLarge.copy(fontFamily = AppFont),
        headlineMedium = d.headlineMedium.copy(fontFamily = AppFont), headlineSmall = d.headlineSmall.copy(fontFamily = AppFont),
        titleLarge = d.titleLarge.copy(fontFamily = AppFont), titleMedium = d.titleMedium.copy(fontFamily = AppFont),
        titleSmall = d.titleSmall.copy(fontFamily = AppFont), bodyLarge = d.bodyLarge.copy(fontFamily = AppFont),
        bodyMedium = d.bodyMedium.copy(fontFamily = AppFont), bodySmall = d.bodySmall.copy(fontFamily = AppFont),
        labelLarge = d.labelLarge.copy(fontFamily = AppFont), labelMedium = d.labelMedium.copy(fontFamily = AppFont),
        labelSmall = d.labelSmall.copy(fontFamily = AppFont)
    )
}

// لمعة الشعار: وميض يمر على الحروف، وبعده طرف النص يتوهج ثم ينطفئ، وبعدها مهلة قبل المرور التالي
@Composable
fun Modifier.logoShimmer(): Modifier {
    val sweep = remember { Animatable(0f) }
    val tip = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            sweep.snapTo(0f); tip.snapTo(0f)
            sweep.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            tip.animateTo(1f, tween(1700, easing = LinearEasing))
            sweep.snapTo(0f); tip.snapTo(0f)
        }
    }
    return this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val tw = size.width - 20.dp.toPx()   // عرض النص الفعلي (20dp مساحة للتوهج عند الطرف)
            val cy = size.height / 2f
            val lw = 1.6.dp.toPx()
            val v = sweep.value
            if (v > 0f && v < 1f) {
                val band = tw * 0.28f
                val cx = -band + (tw + 2f * band) * v
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White, Color.Transparent),
                        start = Offset(cx - band / 2f, 0f), end = Offset(cx + band / 2f, size.height * 0.35f)
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop
                )
                val a = kotlin.math.sin(v * Math.PI.toFloat()).coerceIn(0f, 1f)
                val c = Offset(cx.coerceIn(0f, tw), cy)
                val gr = 18.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.5f * a), Color.Transparent), center = c, radius = gr), radius = gr, center = c)
                drawLine(Color.White.copy(0.9f * a), Offset(c.x - 12.dp.toPx(), c.y), Offset(c.x + 12.dp.toPx(), c.y), lw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White.copy(0.9f * a), Offset(c.x, c.y - 9.dp.toPx()), Offset(c.x, c.y + 9.dp.toPx()), lw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            val g = tip.value
            if (g > 0f && g < 1f) {
                // يتوهج بسرعة ثم ينطفئ ببطء
                val e = if (g < 0.18f) g / 0.18f else { val u = 1f - (g - 0.18f) / 0.82f; u * u }
                // توهج على الحروف الأخيرة نفسها
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(0.6f * e)), startX = tw * 0.55f, endX = tw),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop
                )
                // لمعة عند طرف آخر حرف
                val c = Offset(tw - 2.dp.toPx(), cy)
                val gr = 22.dp.toPx() * (0.55f + 0.45f * e)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.65f * e), Color.Transparent), center = c, radius = gr), radius = gr, center = c)
                drawLine(Color.White.copy(0.95f * e), Offset(c.x - 16.dp.toPx() * e, c.y), Offset(c.x + 16.dp.toPx() * e, c.y), lw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White.copy(0.95f * e), Offset(c.x, c.y - 12.dp.toPx() * e), Offset(c.x, c.y + 12.dp.toPx() * e), lw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        .padding(end = 20.dp)
}

val IVolOff = mkIcon("M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z")
val IVolLow = mkIcon("M7 9v6h4l5 5V4l-5 5H7z")
val IVolMid = mkIcon("M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z")
val IVolHigh = mkIcon("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z")

// إطار لامع يدور ببطء (نفس لمعة الأزرار الحية) بدون ضغط ولا تصغير، لعناصر مثل السلايدر
@Composable
fun Modifier.shimmerBorder(r: Dp): Modifier {
    val ang by rememberInfiniteTransition(label = "rotB").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing)), label = "angB"
    )
    return this.drawWithContent {
        drawContent()
        val t = ang / 360f
        val n = 48
        val stops = Array(n + 1) { i ->
            val pos = i / n.toFloat()
            var d = pos - t
            if (d > 0.5f) d -= 1f
            if (d < -0.5f) d += 1f
            val u = if (d <= 0f) (1f + d / 0.30f).coerceIn(0f, 1f) else (1f - d / 0.08f).coerceIn(0f, 1f)
            val k = u * u * (3f - 2f * u)
            pos to Color(0.74f + 0.26f * k, 0.87f + 0.13f * k, 1f, k * k)
        }
        val w = 2.dp.toPx()
        drawRoundRect(Brush.sweepGradient(*stops, center = center), topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w), alpha = 0.8f)
    }
}

// سلايدر صوت زجاجي: يتحكم بصوت مشغّل التطبيق، ويتذكر آخر مستوى
@Composable
fun GlassVolume(player: Player, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("glass_volume", Context.MODE_PRIVATE) }
    var vol by remember(player) { mutableFloatStateOf(player.volume.coerceIn(0f, 1f)) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onVolumeChanged(volume: Float) { vol = volume } }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    fun set(v: Float) { val x = v.coerceIn(0f, 1f); vol = x; player.volume = x }
    fun save() { prefs.edit().putFloat("v", vol).apply() }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.fillMaxWidth().height(52.dp).glass(18.dp).shimmerBorder(18.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (vol <= 0.001f) IVolOff else if (vol < 0.34f) IVolLow else if (vol < 0.67f) IVolMid else IVolHigh,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { onTouch(); set(if (vol > 0.001f) 0f else 1f); save() }
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(Unit) {
                        val r = 10.dp.toPx()
                        fun frac(x: Float) = ((x - r) / (size.width - 2f * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        detectTapGestures { o -> onTouch(); set(frac(o.x)); save() }
                    }
                    .pointerInput(Unit) {
                        val r = 10.dp.toPx()
                        fun frac(x: Float) = ((x - r) / (size.width - 2f * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        detectHorizontalDragGestures(
                            onDragStart = { o -> onTouch(); set(frac(o.x)) },
                            onDragEnd = { save() }, onDragCancel = { save() }
                        ) { ch, _ -> ch.consume(); onTouch(); set(frac(ch.position.x)) }
                    }
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val r = 10.dp.toPx()
                    val th = 6.dp.toPx()
                    val cy = size.height / 2f
                    val x = r + vol * (size.width - 2f * r)
                    drawRoundRect(Color.White.copy(0.20f), Offset(r, cy - th / 2f), Size(size.width - 2f * r, th), CornerRadius(th / 2f))
                    if (x > r) drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFFA9D2FF), Color.White), startX = r, endX = x.coerceAtLeast(r + 1f)),
                        Offset(r, cy - th / 2f), Size(x - r, th), CornerRadius(th / 2f)
                    )
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.55f), Color.Transparent), center = Offset(x, cy), radius = r * 2f), radius = r * 2f, center = Offset(x, cy))
                    drawCircle(Color.White, radius = r * 0.75f, center = Offset(x, cy))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("${(vol * 100).toInt()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).liveGlass(18.dp, onClick).padding(horizontal = 8.dp),
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
val ISort = mkIcon("M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z")
val IMore = mkIcon("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z")
val IHead = mkIcon("M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z")

@Composable
fun GlassIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).liveGlass(18.dp, onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Chip(text: String, sel: () -> Float, modifier: Modifier = Modifier, onLong: () -> Unit = {}, onClick: () -> Unit) {
    // نفس الارتفاع ونفس الشكل لكل الأزرار، والتحديد تظليل حي يتبع السحب (يُقرأ وقت الرسم بس)
    val pal = LocalPal.current
    Box(
        modifier.height(40.dp).glass(14.dp)
            .drawBehind {
                drawRoundRect(pal.chipHi.copy(alpha = pal.chipHi.alpha * sel().coerceIn(0f, 1f)), cornerRadius = CornerRadius(14.dp.toPx()))
            }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = pal.text, maxLines = 1, fontSize = 14.sp) }
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
            if (c.logo.isEmpty()) Text(c.name.take(1), color = LocalPal.current.text, fontSize = 28.sp)
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
        Text(cleanName(c.name), color = LocalPal.current.text.copy(0.9f), fontSize = 12.sp, maxLines = 2, minLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

// نجمة رباعية الرؤوس بجوانب مقعّرة
private fun starPath(c: Offset, R: Float, r: Float): Path = Path().apply {
    val k = 0.12f
    moveTo(c.x, c.y - r)
    quadraticBezierTo(c.x + k * R, c.y - k * r, c.x + R, c.y)
    quadraticBezierTo(c.x + k * R, c.y + k * r, c.x, c.y + r)
    quadraticBezierTo(c.x - k * R, c.y + k * r, c.x - R, c.y)
    quadraticBezierTo(c.x - k * R, c.y - k * r, c.x, c.y - r)
    close()
}

// سحب السجل: يحفظ آخر انهيار ويجمع سجل التطبيق (logcat) عشان تنسخه أو تشاركه بدون adb
const val TG = "GIPTV"
fun trace(m: String, e: Throwable? = null) { if (e != null) android.util.Log.i(TG, m, e) else android.util.Log.i(TG, m) }

object Logs {
    private const val CRASH = "last_crash.txt"

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(ctx.filesDir, CRASH).writeText(
                    "${java.util.Date()}\nthread=${t.name}\n" + android.util.Log.getStackTraceString(e)
                )
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun collect(ctx: Context): String {
        val sb = StringBuilder()
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "?" }
        sb.append("Glass IPTV $ver | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Time: ${java.util.Date()}\n\n")
        try {
            val f = java.io.File(ctx.filesDir, CRASH)
            if (f.exists()) sb.append("=== آخر انهيار ===\n").append(f.readText()).append("\n\n")
        } catch (_: Throwable) {}
        try {
            val pr = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-s", "$TG:V").redirectErrorStream(true).start()
            sb.append("=== تتبع التطبيق (GIPTV) ===\n").append(pr.inputStream.bufferedReader().readText()).append("\n")
            pr.destroy()
        } catch (_: Throwable) {}
        sb.append("=== logcat ===\n")
        try {
            val pr = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "1500").redirectErrorStream(true).start()
            sb.append(pr.inputStream.bufferedReader().readText())
            pr.destroy()
        } catch (e: Throwable) { sb.append("تعذّر قراءة logcat: ${e.message}") }
        val t = sb.toString()
        return if (t.length > 90_000) "...(مقصوص)\n" + t.takeLast(90_000) else t
    }
}

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
fun Video(p: Player, modifier: Modifier = Modifier, onView: ((PlayerView) -> Unit)? = null) {
    DisposableEffect(Unit) {
        trace("Video composed")
        onDispose { trace("Video DISPOSED") }
    }
    VideoView(p, modifier, onView)
}

@Composable
fun VideoView(p: Player, modifier: Modifier = Modifier, onView: ((PlayerView) -> Unit)? = null) = AndroidView(
    // TextureView عشان الزوم والتدوير يشتغلون صح
    factory = { c -> (LayoutInflater.from(c).inflate(R.layout.player_view, null, false) as PlayerView).apply { player = p; post { onView?.invoke(this) } } },
    update = { it.player = p },
    modifier = modifier.fillMaxSize()
)

// إضاءة محيطية حول الفيديو: نأخذ إطار صغير جداً من الفيديو كل ~120ms، ننعّم ألوانه مع الإطار السابق،
// ونرسمه مكبّراً ومموهاً خلف الفيديو فيطلع توهج بألوان المشهد حوالين الفيديو
@Composable
fun AmbientGlow(pv: PlayerView?, ar: Float, modifier: Modifier = Modifier) {
    val gw = 16; val gh = 9      // حجم الشبكة اللي نرسمها
    val sw = 80; val sh = 45     // حجم السحب من الفيديو (5x5 بكسل لكل خانة) لتجنب الوميض من أخذ عينات قليلة
    val bmp = remember { android.graphics.Bitmap.createBitmap(gw, gh, android.graphics.Bitmap.Config.ARGB_8888) }
    val tmp = remember { android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888) }
    val img = remember { bmp.asImageBitmap() }
    var tick by remember { mutableStateOf(0) }
    val fx = remember { if (Build.VERSION.SDK_INT >= 31) ambientEffect(160f) else null }
    LaunchedEffect(pv) {
        if (pv == null) return@LaunchedEffect
        val target = FloatArray(gw * gh * 3)
        val cur = FloatArray(gw * gh * 3)
        val raw = IntArray(sw * sh)
        val px = IntArray(gw * gh)
        var have = false
        // 1) سحب الألوان من الفيديو ~7 مرات بالثانية (متوسط كل 5x5 بكسل)
        launch {
            val bw = sw / gw; val bh = sh / gh
            while (true) {
                val tv = pv.videoSurfaceView as? android.view.TextureView
                if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                    val ok = try { tv.getBitmap(tmp); true } catch (_: Throwable) { false }
                    if (ok) {
                        tmp.getPixels(raw, 0, sw, 0, 0, sw, sh)
                        for (gy in 0 until gh) for (gx in 0 until gw) {
                            var r = 0f; var g = 0f; var b = 0f
                            for (yy in 0 until bh) for (xx in 0 until bw) {
                                val c = raw[(gy * bh + yy) * sw + gx * bw + xx]
                                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
                            }
                            val n = (bw * bh).toFloat(); val o = (gy * gw + gx) * 3
                            target[o] = r / n; target[o + 1] = g / n; target[o + 2] = b / n
                        }
                        if (!have) { System.arraycopy(target, 0, cur, 0, cur.size); have = true }
                    }
                }
                delay(140)
            }
        }
        // 2) كل فريم (30fps) نقرّب الألوان الحالية من الهدف بنعومة، حتى تكون الحركة مايعة بدون قفزات
        var last = 0L
        while (true) {
            val t = androidx.compose.runtime.withFrameNanos { it }
            if (last == 0L) { last = t; continue }
            val dt = (t - last) / 1e9f
            if (dt < 0.033f || !have) continue
            last = t
            val k = 1f - kotlin.math.exp(-dt / 0.5f)   // ثابت زمني 0.5 ثانية
            for (i in 0 until gw * gh) {
                val o = i * 3
                cur[o] += (target[o] - cur[o]) * k
                cur[o + 1] += (target[o + 1] - cur[o + 1]) * k
                cur[o + 2] += (target[o + 2] - cur[o + 2]) * k
                // الخانات الداكنة تتلاشى (شفافية) بدل ما نرسمها: كذا ما تبين مربعات/تدرجات متقطعة في المشاهد الداكنة
                val l = 0.2126f * cur[o] + 0.7152f * cur[o + 1] + 0.0722f * cur[o + 2]
                val tt = ((l - 14f) / 34f).coerceIn(0f, 1f)
                val al = (tt * tt * (3f - 2f * tt) * 255f).toInt()
                px[i] = (al shl 24) or (cur[o].toInt() shl 16) or (cur[o + 1].toInt() shl 8) or cur[o + 2].toInt()
            }
            bmp.setPixels(px, 0, gw, 0, 0, gw, gh)
            tick++
        }
    }
    Box(modifier.fillMaxSize()
        .graphicsLayer { renderEffect = fx }
        .drawBehind {
            tick   // قراءة الحالة عشان يعاد الرسم مع كل إطار جديد
            val a = if (ar > 0f) ar else 16f / 9f
            val rw: Float; val rh: Float
            if (a > size.width / size.height) { rw = size.width; rh = size.width / a } else { rh = size.height; rw = size.height * a }
            val gwid = rw * 1.08f; val ghei = rh * 1.2f
            drawImage(img,
                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - gwid) / 2f).toInt(), ((size.height - ghei) / 2f).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(gwid.toInt(), ghei.toInt()),
                alpha = 0.5f, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
        })
}

// ترتيب القنوات: 0 أبجدي، 1 الأكثر تشغيلاً
fun sortChannels(list: List<Channel>, mode: Int, counts: Map<String, Int>): List<Channel> = when (mode) {
    1 -> list.sortedWith(compareByDescending<Channel> { counts[it.url] ?: 0 }.thenBy { cleanName(it.name).lowercase() })
    else -> list.sortedBy { cleanName(it.name).lowercase() }
}

// اسم القناة للعرض: نشيل أي شي بين أقواس (1080p) أو [Not 24/7] ونخلي الاسم فقط
private val tagRe = Regex("\\s*(\\([^)]*\\)|\\[[^\\]]*\\])")
private val spRe = Regex("\\s{2,}")
fun cleanName(n: String): String = n.replace(tagRe, "").replace(spRe, " ").trim().ifEmpty { n }

// يجهّز شعار القناة كصورة مربعة (شعار كامل بدون قص) لإشعار التشغيل والجزيرة، بخلفية داكنة أو فاتحة حسب لون الشعار
fun fitLogo(u: String): ByteArray? {
    return try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        val raw = c.inputStream.use { it.readBytes() }
        c.disconnect()
        if (raw.isEmpty() || raw.size > 4_000_000) return null
        val src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val size = 512
        // متوسط سطوع الأجزاء الظاهرة من الشعار: الشعار الغامق ياخذ خلفية فاتحة
        val small = android.graphics.Bitmap.createScaledBitmap(src, 24, 24, true)
        var sum = 0.0; var cnt = 0
        for (y in 0 until 24) for (x in 0 until 24) {
            val px = small.getPixel(x, y)
            if ((px ushr 24) > 40) {
                sum += (0.299 * ((px shr 16) and 255) + 0.587 * ((px shr 8) and 255) + 0.114 * (px and 255)) / 255.0
                cnt++
            }
        }
        val lum = if (cnt > 0) sum / cnt else 0.5
        val bg = if (lum < 0.30) 0xFFF2F2F7.toInt() else 0xFF1C1C1E.toInt()
        val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(out)
        cv.drawColor(bg)
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val diag = kotlin.math.sqrt(w * w + h * h)
        // الشعار كامل داخل الدائرة المحيطة بالمربع، بهامش حوله
        val sc = minOf(0.82f * size / w, 0.82f * size / h, 0.92f * size / diag)
        val dw = w * sc; val dh = h * sc
        val l = (size - dw) / 2f; val t = (size - dh) / 2f
        cv.drawBitmap(src, null, android.graphics.RectF(l, t, l + dw, t + dh),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
        val bos = java.io.ByteArrayOutputStream()
        out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
        bos.toByteArray()
    } catch (_: Throwable) { null }
}

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
fun App(player: Player?, inPip: Boolean, openReq: Int, themeMode: Int, themeStyle: Int, onTheme: (Int, Int) -> Unit, onPip: () -> Unit) {
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
    var showSettings by remember { mutableStateOf(false) }
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
    // سحب للأسفل وهو بشاشة كاملة: الفيديو ينزل مع الإصبع، وعند الإفلات يلف ويصغر
    // الفيديو يلحق الإصبع (لأعلى أو لأسفل أو لليسار) وعند الإفلات يكمل التكبير أو التصغير
    var fsDrag by remember { mutableStateOf(-1f) }      // >=0 أثناء السحب
    var fsDx by remember { mutableStateOf(0f) }
    var fsDy by remember { mutableStateOf(0f) }
    val fsAx = remember { Animatable(0f) }
    val fsA = remember { Animatable(0f) }
    fun fsNowX() = if (fsDrag >= 0f) fsDx else fsAx.value
    fun fsNowY() = if (fsDrag >= 0f) fsDy else fsA.value
    // رمي الفيديو للأسفل (سحب للأسفل في البورتريه)
    val dismissA = remember { Animatable(0f) }
    var dismissDrag by remember { mutableStateOf(-1f) }
    var flying by remember { mutableStateOf(false) }   // الفيديو راجع لبطاقته بأنيميشن
    fun dyNow() = if (dismissDrag >= 0f) dismissDrag else dismissA.value
    val cfg = LocalConfiguration.current
    val winPortraitNow by rememberUpdatedState(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
    var vAr by remember { mutableStateOf(0f) }
    var pvRef by remember { mutableStateOf<PlayerView?>(null) }
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
    val hapticView = androidx.compose.ui.platform.LocalView.current
    // الأزرار تختفي تلقائياً بعد 5 ثواني من ظهورها (يتجدد العد عند أي لمسة على الأزرار)
    var uiTick by remember { mutableStateOf(0) }
    LaunchedEffect(cur, ui, menu, uiTick) {
        if (cur != null && ui && !menu) {
            snapshotFlow { reveal.value }.first { it >= 0.99f }
            delay(5000)
            ui = false
        }
    }
    // حالة الشبكة هنا فوق عشان ترجع لنفس المكان بعد الخروج من القناة
    val gridState = rememberLazyGridState()
    val favState = rememberLazyGridState()
    val pager = rememberPagerState(initialPage = 1, pageCount = { 2 })
    var mainTab by remember { mutableStateOf(0) }
    var lastMainTab by remember { mutableStateOf(0) }
    // الصفحة 0 = القائمة الرئيسية، الصفحة 1 = المفضلة، والسحب بينهم حي
    LaunchedEffect(tab) {
        if (tab >= 0) {
            mainTab = tab
            if (tab != lastMainTab) { lastMainTab = tab; gridState.scrollToItem(0) }
        }
        val target = if (tab == -1) 0 else 1
        if (pager.currentPage != target) pager.animateScrollToPage(target)
    }
    fun pageProg() = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { pg ->
            if (pg == 0 && tab != -1) tab = -1
            else if (pg == 1 && tab == -1) tab = mainTab
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (isPlaying) error = null }
            override fun onPlayerError(e: PlaybackException) {
                trace("onPlayerError ${e.errorCodeName}: ${e.message}")
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

    // ارتداد خفيف مع هزات بسيطة عند الوصول لحد التقريب (الأدنى أو الأقصى)
    val limScale = remember { Animatable(1f) }
    val limShake = remember { Animatable(0f) }
    fun limitBounce() {
        buzz(hapticView, false)
        scope.launch {
            launch { limScale.snapTo(0.93f); limScale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 520f)) }
            launch { limShake.snapTo(14f); limShake.animateTo(0f, spring(dampingRatio = 0.14f, stiffness = 800f)) }
        }
    }

    fun start(c: Channel, from: Rect? = null) {
        trace("start(${c.name}) closing=$closing")
        if (closing) return
        error = null; qSel = null; vh = 0; ui = true; zoom = 1f; panX = 0f; panY = 0f
        Hdr.ref = c.ref; Hdr.ua = c.ua
        store.bumpCount(c.url)
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
                .setMediaMetadata(MediaMetadata.Builder().setTitle(cleanName(c.name)).apply {
                    // لوقو القناة يظهر في إشعار التشغيل وبطاقة الميديا
                    if (c.logo.startsWith("http")) setArtworkUri(android.net.Uri.parse(c.logo))
                }.build())
            if (c.url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
            p.setMediaItem(b.build()); p.prepare(); p.play()
            // بعد ما يشتغل البث: نبدّل الشعار بنسخة مربعة كاملة (بدون إيقاف التشغيل)
            if (c.logo.startsWith("http")) scope.launch {
                val bytes = withContext(Dispatchers.IO) { fitLogo(c.logo) } ?: return@launch
                val item = p.currentMediaItem ?: return@launch
                if (item.localConfiguration?.uri?.toString() != c.url) return@launch   // القناة تغيّرت
                val md = item.mediaMetadata.buildUpon()
                    .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
                p.replaceMediaItem(p.currentMediaItemIndex, item.buildUpon().setMediaMetadata(md).build())
            }
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

    // يكمّل أنيميشن الشاشة الكاملة (تدوير الفيديو فقط، بدون تدوير النظام)
    fun settleFull(p0: Float, target: Float) {
        scope.launch {
            fullP.snapTo(p0); fpDrag = -1f
            fullP.animateTo(target, tween(if (target >= 1f) 320 else 380, easing = FastOutSlowInEasing))
            buzz(hapticView, false)   // هزة عند اكتمال التكبير أو التصغير
        }
    }

    fun setFull(on: Boolean) {
        if (on == full) return
        full = on
        if (on) ui = false   // عند التكبير الأزرار وسلايدر الصوت تنخفي حتى لو كانت ظاهرة
        bars(!on)
        settleFull(fpNow(), if (on) 1f else 0f)
    }

    // الخروج: الفيديو ينكمش راجع لمكان البطاقة، وبعدها نوقف التشغيل
    fun close() {
        trace("close() closing=$closing cur=${cur?.name} reveal=${reveal.value}", Throwable("close caller"))
        if (closing || cur == null) return
        closing = true
        if (full) { full = false; bars(true) }
        scope.launch {
            if (fullP.value != 0f) fullP.snapTo(0f)
            reveal.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            player?.stop(); cur = null; error = null
            zoom = 1f; panX = 0f; panY = 0f
            closing = false
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

    BackHandler(cur != null) { trace("BackHandler fired full=$full"); if (full) setFull(false) else close() }
    // فتح الفيديو الشغال من إشعار التشغيل (التطبيق كان بالخلفية)
    LaunchedEffect(openReq, player) {
        if (openReq > 0 && player != null && cur == null && !closing &&
            player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE) {
            val mi = player.currentMediaItem!!
            val md = mi.mediaMetadata
            val ch = Channel(md.title?.toString() ?: "بث مباشر", mi.localConfiguration?.uri?.toString() ?: "",
                md.artworkUri?.toString() ?: "", Hdr.ref, Hdr.ua)
            trace("resume video from notification: ${ch.name}")
            error = null; qSel = null; vh = 0; ui = true; zoom = 1f; panX = 0f; panY = 0f
            val dm = ctx.resources.displayMetrics
            val cx = dm.widthPixels / 2f; val cy = dm.heightPixels / 2f
            origin = Rect(cx - 80f, cy - 80f, cx + 80f, cy + 80f)
            cur = ch
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(player) {
        player?.let { it.volume = ctx.getSharedPreferences("glass_volume", Context.MODE_PRIVATE).getFloat("v", 1f) }
    }
    LaunchedEffect(cur) { trace("cur changed -> ${cur?.name} reveal=${reveal.value}") }
    LaunchedEffect(inPip) { trace("inPip=$inPip") }

    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { player?.let { Video(it) } }
        return
    }

    val pal = LocalPal.current
    val favUrls = remember(favs) { favs.map { it.url }.toHashSet() }
    val hh = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 112.dp
    val layer = rememberGraphicsLayer()
    val frost = remember { if (Build.VERSION.SDK_INT >= 31) frostEffect(110f) else null }
    var menuMore by remember { mutableStateOf(false) }
    var menuSort by remember { mutableStateOf(false) }
    var sorts by remember { mutableStateOf(store.sorts()) }
    var counts by remember { mutableStateOf(store.counts()) }
    // عدد مرات التشغيل يتحدّث لما ترجع للقائمة (عشان ما تتحرك البطاقات وأنت داخل قناة)
    LaunchedEffect(cur == null) { if (cur == null) counts = store.counts() }
    // كل قائمة لها ترتيبها الخاص (المفضلة = "fav")
    fun sortKey(): String = if (tab == -1) "fav" else (lists.getOrNull(mainTab) ?: "")
    // هزة عند التنقل بين القوائم
    // الهزة فورية: عند الضغط على القائمة مباشرة، أو لما الصفحة تعدّي نصف السحب (مو بعد ما تستقر)
    var lastTick by remember { mutableStateOf(0L) }
    fun tick() {
        val n = android.os.SystemClock.uptimeMillis()
        if (n - lastTick > 350L) { lastTick = n; buzz(hapticView, false) }
    }
    var prevTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) { if (tab != prevTab) { prevTab = tab; tick() } }
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.currentPage }.collect { if (first) first = false else tick() }
    }
    var names by remember { mutableStateOf(store.names()) }
    var menuFor by remember { mutableStateOf(-1) }
    var renameIdx by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    // لمعة الهيدر: حلقة إطارات تشتغل فقط وأنت في الواجهة الرئيسية
    var shT by remember { mutableStateOf(0f) }
    LaunchedEffect(cur == null) {
        if (cur == null) {
            val t0 = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val ph = ((t - t0) / 1_000_000L) % 16000L
                shT = if (ph < 12000L) ph / 12000f else 1f
            }
        }
    }
    val c = cur
    SideEffect {
        val ic = WindowCompat.getInsetsController(act.window, act.window.decorView)
        ic.isAppearanceLightStatusBars = !pal.dark && cur == null
        ic.isAppearanceLightNavigationBars = !pal.dark && cur == null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            // الواجهة الرئيسية تصغر وتخفت شوي وقت ما الفيديو يفتح فوقها
            val dp = (dyNow() / (size.height * 0.6f)).coerceIn(0f, 1f)
            val p = (reveal.value * (1f - dp)).coerceIn(0f, 1f)
            val k = 1f - 0.07f * p
            scaleX = k; scaleY = k; alpha = 1f - 0.6f * p
        }) {
            Box(Modifier.fillMaxSize().background(pal.bg1)) {
                // الخلفية والقنوات كلها تتسجل في طبقة وحدة، وتنرسم تحت الهيدر فقط
                Box(Modifier.fillMaxSize().drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    clipRect(top = hh.toPx()) { drawLayer(layer) }
                }) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2))))
                    Box(Modifier.fillMaxSize().drawBehind {
                        drawRect(Brush.radialGradient(listOf(pal.blob1, Color.Transparent), Offset(size.width * 0.1f, size.height * 0.05f), size.width * 0.9f))
                        drawRect(Brush.radialGradient(listOf(pal.blob2, Color.Transparent), Offset(size.width * 0.95f, size.height * 0.95f), size.width * 0.9f))
                    })
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            // الواجهة الرئيسية: نلغي السحب لليسار (عكس المفضلة) عشان ما يتداخل مع التمرير للأسفل
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (pager.settledPage != 1 || pager.isScrollInProgress) return@awaitEachGesture
                                var blocking = false
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    val tot = ch.position - down.position
                                    if (!blocking) {
                                        if (tot.getDistance() <= viewConfiguration.touchSlop) continue
                                        // أي حركة فيها ميل لليسار تنلغي؛ الحركة العمودية الصريحة تمر للقائمة تتمرر عادي
                                        blocking = tot.x < 0f && kotlin.math.abs(tot.x) * 2f > kotlin.math.abs(tot.y)
                                        if (!blocking) break
                                    }
                                    ch.consume()
                                }
                            }
                        }, beyondViewportPageCount = 1) { page ->
                        val isFavPage = page == 0
                        val smode = sorts[if (isFavPage) "fav" else (lists.getOrNull(mainTab) ?: "")] ?: 0
                        val shownList = remember(isFavPage, favs, channels, smode, counts) {
                            sortChannels(if (isFavPage) favs else channels, smode, counts)
                        }
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = if (isFavPage) favState else gridState,
                                columns = GridCells.Adaptive(96.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = hh + 8.dp, bottom = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(shownList, contentType = { "tile" }) { ch ->
                                    Tile(ch, ch.url in favUrls, onLong = { toggleFav(ch) }) { r -> start(ch, r) }
                                }
                            }
                            if (isFavPage && favs.isEmpty()) Text("اضغط مطولاً على أي قناة لإضافتها للمفضلة", color = pal.textDim,
                                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                            else if (!isFavPage && loading) Text("جاري التحميل...", color = pal.textDim,
                                modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                // الهيدر: زجاج مضبب حقيقي، نفس الطبقة لكن مموهة بقوة
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(hh)) {
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer { renderEffect = frost; clip = true }
                        .drawBehind { drawLayer(layer) })
                    Box(Modifier.fillMaxSize().background(pal.headerTint))
                    // حافة الهيدر السفلية: مذنّب متوهج متغير الألوان وذيل يتلاشى
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 30.dp).fillMaxWidth().height(60.dp).drawBehind {
                        val yb = size.height / 2f
                        val tail = size.width * 0.38f
                        val x0 = size.width * 0.25f
                        val x1 = size.width * 0.82f
                        val hx = x0 + (x1 - x0) * shT
                        fun sm(a: Float, b: Float, x: Float): Float { val u = ((x - a) / (b - a)).coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
                        val env = sm(0f, 0.3f, shT) * (1f - sm(0.6f, 1f, shT))
                        val hue = 190f + 110f * shT
                        val hc = Color.hsv(hue, 0.55f, 1f)
                        val hc2 = Color.hsv((hue + 70f) % 360f, 0.6f, 1f)
                        // الخط الأساسي
                        drawRect(pal.rim,
                            topLeft = Offset(0f, yb - 1.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                        // مذنّب صغير وناعم: يظهر تدريجياً من الربع ويتلاشى قبل نهاية الخط، وألوانه تتغير ببطء
                        fun Color.a(x: Float) = copy(alpha = alpha * x * env)
                        val tl = size.width * 0.20f
                        fun taper(tw: Float) = Path().apply {
                            moveTo(hx, yb - tw / 2f)
                            quadraticBezierTo(hx - tl * 0.5f, yb - tw * 0.25f, hx - tl, yb)
                            quadraticBezierTo(hx - tl * 0.5f, yb + tw * 0.25f, hx, yb + tw / 2f)
                            this.close()   // this عشان ما تنادي دالة close() حقت القناة
                        }
                        if (env > 0.002f) {
                            drawPath(taper(14.dp.toPx()), Brush.horizontalGradient(listOf(Color.Transparent, hc.a(0.05f), hc2.a(0.14f)), startX = hx - tl, endX = hx))
                            drawPath(taper(5.dp.toPx()), Brush.horizontalGradient(listOf(Color.Transparent, hc2.a(0.28f), pal.shim.a(0.6f)), startX = hx - tl, endX = hx))
                            for (i in 1..4) {
                                val f = i / 5f
                                val sp = Offset(hx - tl * f * 0.8f, yb + kotlin.math.sin(shT * 14f + i * 2.1f) * 4.dp.toPx() * f)
                                drawCircle(hc2.a((1f - f) * 0.4f), radius = (1.6f - f * 0.9f).dp.toPx(), center = sp)
                            }
                            val c0 = Offset(hx, yb)
                            val pulse = 1f + 0.06f * kotlin.math.sin(shT * 18f)
                            val halo = 16.dp.toPx()
                            drawCircle(Brush.radialGradient(listOf(hc.a(0.28f), Color.Transparent), center = c0, radius = halo),
                                radius = halo, center = c0)
                            val core = 6.dp.toPx() * pulse
                            drawCircle(Brush.radialGradient(listOf(pal.shim.a(0.85f), hc.a(0.5f), Color.Transparent), center = c0, radius = core),
                                radius = core, center = c0)
                        }
                    })
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Glass IPTV", color = pal.text.copy(alpha = 0.88f), fontSize = 27.sp, fontFamily = TitleFont,
                                fontWeight = FontWeight.Medium, letterSpacing = 2.sp,
                                modifier = Modifier.padding(vertical = 10.dp).logoShimmer())
                            Spacer(Modifier.weight(1f))
                            Box {
                                Box(Modifier.size(40.dp).glass(14.dp).clickable { menuSort = true }, contentAlignment = Alignment.Center) {
                                    Icon(ISort, contentDescription = null, tint = pal.text, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(expanded = menuSort, onDismissRequest = { menuSort = false }) {
                                    listOf("أبجدي (A-Z)", "الأكثر تشغيلاً").forEachIndexed { i, t ->
                                        DropdownMenuItem(text = { Text(if ((sorts[sortKey()] ?: 0) == i) "✓  $t" else t) }, onClick = {
                                            val k = sortKey()
                                            sorts = sorts + (k to i); store.saveSorts(sorts); menuSort = false
                                            scope.launch { (if (tab == -1) favState else gridState).scrollToItem(0) }
                                        })
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box {
                                Box(Modifier.size(40.dp).glass(14.dp).clickable { menuMore = true }, contentAlignment = Alignment.Center) {
                                    Icon(IMore, contentDescription = null, tint = pal.text, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(expanded = menuMore, onDismissRequest = { menuMore = false }) {
                                    DropdownMenuItem(text = { Text("الإعدادات") }, onClick = { menuMore = false; showSettings = true })
                                    DropdownMenuItem(text = { Text("إضافة رابط") }, onClick = { menuMore = false; showAdd = true })
                                    DropdownMenuItem(text = { Text("سحب السجل (Logs)") }, onClick = {
                                        menuMore = false
                                        scope.launch {
                                            val t = withContext(Dispatchers.IO) { Logs.collect(ctx) }
                                            try {
                                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Glass IPTV log", t))
                                            } catch (_: Throwable) {}
                                            Toast.makeText(ctx, "تم نسخ السجل", Toast.LENGTH_SHORT).show()
                                            try {
                                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Glass IPTV log")
                                                    putExtra(android.content.Intent.EXTRA_TEXT, t)
                                                }
                                                ctx.startActivity(android.content.Intent.createChooser(send, "مشاركة السجل").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                            } catch (_: Throwable) {}
                                        }
                                    })
                                }
                            }
                        }
                        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            item {
                                Chip("★ المفضلة", { 1f - pageProg() }, Modifier.onGloballyPositioned { chipX[-1] = it.positionInRoot().x + it.size.width / 2f }) { tab = -1 }
                            }
                            itemsIndexed(lists) { i, u ->
                                val a by animateFloatAsState(targetValue = if (mainTab == i) 1f else 0f, animationSpec = tween(220), label = "sel")
                                val label = names[u]?.takeIf { it.isNotBlank() }
                                    ?: u.substringAfterLast('/').substringBefore('?').removeSuffix(".m3u").ifEmpty { "قائمة" }
                                Box {
                                    Chip(label.take(24), { a * pageProg() },
                                        Modifier.onGloballyPositioned { chipX[i] = it.positionInRoot().x + it.size.width / 2f },
                                        onLong = { menuFor = i }) { tab = i }
                                    DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = -1 }) {
                                        DropdownMenuItem(text = { Text("إعادة تسمية") }, onClick = {
                                            menuFor = -1; renameIdx = i; renameText = names[u] ?: label
                                        })
                                        DropdownMenuItem(text = { Text("حذف القائمة") }, onClick = {
                                            menuFor = -1
                                            lists = lists.filterIndexed { j, _ -> j != i }
                                            store.savePlaylists(lists)
                                            tab = if (lists.isEmpty()) -1 else 0
                                        })
                                    }
                                }
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
                                val x = mix(favX, mainX, pageP) - dotLeft
                                val moving = kotlin.math.sin(Math.PI.toFloat() * pageP)
                                val extra = kotlin.math.abs((chipX[mainTab] ?: 0f) - mainX).coerceAtMost(160f)
                                val w = 16.dp.toPx() + moving * 36.dp.toPx() + extra * 0.4f
                                val h = 5.dp.toPx()
                                drawRoundRect(pal.dot.copy(0.28f), Offset(x - w / 2f - 3.dp.toPx(), 1.dp.toPx()),
                                    Size(w + 6.dp.toPx(), h + 4.dp.toPx()), CornerRadius(h))
                                drawRoundRect(pal.dot.copy(0.95f), Offset(x - w / 2f, 3.dp.toPx()), Size(w, h), CornerRadius(h / 2f))
                            })
                    }
                }
            }
        }

        CompositionLocalProvider(LocalPal provides DarkPal) {
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
                            val blockUp = !full && down.position.y > size.height * 0.86f
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
                                    val vert = kotlin.math.abs(ay) > kotlin.math.abs(ax)
                                    val myTop = (zoom - 1f) * size.height / 2f
                                    if (zoom <= 1.02f && full) {
                                        // شاشة كاملة: السحب بأي اتجاه يلغيها ويرجع للبورتريه
                                        mode = 2
                                    } else if (zoom <= 1.02f && vert) {
                                        if (!full && ay < 0f && !blockUp) mode = 1
                                        else if (!full && ay > 0f) mode = 3
                                    } else if (!full && ay > 0f && kotlin.math.abs(ay) > kotlin.math.abs(ax) * 0.7f) {
                                        // سحب للأسفل شبه عمودي، أو الفيديو مقرّب وواصل لحافته العليا: نقبل الرمي
                                        mode = 3
                                    }
                                }
                                if (mode == 3) {
                                    // سحب للأسفل في البورتريه: الفيديو ينزل مع الإصبع (حتى لو مقرّب)
                                    dismissDrag = ay.coerceAtLeast(0f)
                                    ch.consume()
                                } else if (mode == 1 || mode == 2) {
                                    // الفيديو يلحق إصبعك بنفس الاتجاه (في الخروج من الشاشة الكاملة: أي اتجاه)
                                    if (mode == 1) fsDy = tot.y.coerceAtMost(0f)
                                    else { fsDx = tot.x; fsDy = tot.y }
                                    fsDrag = 1f
                                    ch.consume()
                                } else if (moved && zoom > 1.02f) {
                                    // تحريك الفيديو بعد الزوم
                                    val d = ch.position - lastPos
                                    // الفيديو يبقى في منتصف الشاشة عمودياً: تحريك أفقي فقط
                                    val mx = (zoom - 1f) * size.width / 2f
                                    panX = (panX + d.x).coerceIn(-mx, mx)
                                    panY = 0f
                                    ch.consume()
                                } else if (mode != 0) {
                                    // الفيديو يتحرك مع الإصبع حي
                                    prog = if (mode == 1) (-ay / dist).coerceIn(0f, 1f) else 1f - ((if (rot) maxOf(ay, tot.y) else ay) / dist).coerceIn(0f, 1f)
                                    fpDrag = prog
                                    ch.consume()
                                }
                                lastPos = ch.position
                            }
                            if (mode == 1 || mode == 2) {
                                val v = vt.calculateVelocity()
                                val dispX = kotlin.math.abs(fsDx); val dispY = kotlin.math.abs(fsDy)
                                val disp = if (mode == 1) -fsDy else maxOf(dispX, dispY)
                                val thr = if (mode == 2 && dispX > dispY) size.width * 0.12f else size.height * 0.12f
                                val vel = if (mode == 1) -v.y else kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                                val go = disp > thr || vel > 900f
                                val enter = mode == 1
                                scope.launch {
                                    fsAx.snapTo(fsDx); fsA.snapTo(fsDy)
                                    fsDrag = -1f; fsDx = 0f; fsDy = 0f
                                    if (go) {
                                        full = enter; bars(!enter)
                                        if (enter) ui = false   // التكبير بالسحب: نخفي الأزرار والصوت
                                        launch { fsAx.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
                                        launch { fsA.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
                                        // يلف ويكبر أو يصغر، وهزة عند الاكتمال
                                        settleFull(if (enter) 0f else 1f, if (enter) 1f else 0f)
                                    } else {
                                        launch { fsAx.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
                                        fsA.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                                    }
                                }
                                return@awaitEachGesture
                            }
                            if (mode == 3) {
                                val v = vt.calculateVelocity()
                                val hPx = size.height.toFloat()
                                val dyEnd = dismissDrag.coerceAtLeast(0f)
                                val goBack = dyEnd > hPx * 0.10f || v.y > 900f
                                if (goBack) { closing = true; flying = true }
                                scope.launch {
                                    dismissA.snapTo(dyEnd); dismissDrag = -1f
                                    if (goBack) {
                                        // الفيديو يرجع لمكان القناة اللي فتحته منها
                                        launch { dismissA.animateTo(0f, tween(480, easing = FastOutSlowInEasing)) }
                                        reveal.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                                        trace("dismiss fly finished")
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
                                val vy = if (rot) (if (mode == 2) maxOf(-v.x, v.y) else -v.x) else v.y
                                val target = when {
                                    vy < -1200f -> 1f
                                    vy > 1200f -> 0f
                                    else -> if (prog > 0.5f) 1f else 0f
                                }
                                full = target == 1f
                                if (full) ui = false
                                bars(target == 0f)
                                settleFull(prog, target)
                                return@awaitEachGesture
                            }
                            if (!released || moved) return@awaitEachGesture
                            // ضغطة وحدة: إظهار/إخفاء الأزرار. ضغطتين مع سحب: زوم
                            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                            if (second == null) { ui = !ui; return@awaitEachGesture }
                            var lastP = second.position
                            var dragged = false
                            var over = 0f
                            var armed = true
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == second.id } ?: break
                                if (!ch.pressed) break
                                val d = ch.position - lastP
                                lastP = ch.position
                                if (!dragged && (ch.position - second.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                                val dyPic = if (full && size.height > size.width) -d.x else d.y
                                if (dyPic != 0f) {
                                    val zRaw = zoom * kotlin.math.exp(dyPic / 450f)
                                    zoom = zRaw.coerceIn(1f, 4f)
                                    // التقريب حول منتصف الشاشة، والفيديو يبقى في المنتصف عمودياً
                                    val mx = (zoom - 1f) * size.width / 2f
                                    panX = panX.coerceIn(-mx, mx); panY = 0f
                                    if (zRaw < 1f || zRaw > 4f) {
                                        // وصلنا لحد التقريب وما زال المستخدم يسحب: ارتداد وهزات بسيطة
                                        over += kotlin.math.abs(dyPic)
                                        if (armed && over > 28.dp.toPx()) { armed = false; limitBounce() }
                                    } else { over = 0f; armed = true }
                                    ch.consume()
                                }
                            }
                            // رجعنا تقريباً للحجم الطبيعي: نثبّته على 1 بالضبط عشان إيماءة الشاشة الكاملة ما تتعطل
                            if (dragged && zoom < 1.05f) { zoom = 1f; panX = 0f; panY = 0f }
                            if (!dragged) {
                                val z0 = zoom; val x0 = panX
                                if (zoom > 1.02f) {
                                    // ضغطتين وهو مقرّب: يرجع لحجمه بارتداد خفيف مع هزة
                                    buzz(hapticView, false)
                                    scope.launch {
                                        val t = Animatable(0f)
                                        t.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) {
                                            zoom = z0 + (1f - z0) * value
                                            panX = x0 * (1f - value); panY = 0f
                                        }
                                        zoom = 1f; panX = 0f; panY = 0f
                                    }
                                } else {
                                    // ضغطتين: تقريب 1.5x حول المنتصف
                                    scope.launch {
                                        val t = Animatable(0f)
                                        t.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) {
                                            zoom = 1f + 0.5f * value; panY = 0f
                                        }
                                        zoom = 1.5f
                                    }
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
                    // نفس تحويل الفيديو (تدوير/تكبير/سحب) ينطبق على الإضاءة المحيطية
                    val videoXf: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                        val fp = fpNow()
                        val w = size.width; val h = size.height
                        val portrait = h > w
                        val ar = if (vAr > 0f) vAr else 16f / 9f
                        // معامل التكبير عشان الفيديو يملأ الشاشة بعد التدوير
                        val sFull = if (portrait) kotlin.math.min(h, w * ar) / kotlin.math.min(w, h * ar) else 1f
                        // خروج من أفقي حقيقي: الفيديو ينزل ويصغر مع الإصبع
                        val q = if (!portrait && full && fpDrag >= 0f) 1f - fp else 0f
                        val k = (1f + (sFull - 1f) * fp) * zoom * limScale.value * (1f - 0.15f * q)
                        scaleX = k; scaleY = k
                        rotationZ = (if (portrait) 90f * fp else 0f) + limShake.value * 0.12f
                        translationX = panX + fsNowX() + limShake.value; translationY = panY + q * 0.4f * h + fsNowY()
                    }
                    AmbientGlow(pvRef, vAr, Modifier.graphicsLayer(videoXf))
                    Video(it, Modifier.graphicsLayer(videoXf), onView = { v -> pvRef = v })
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
                    alpha = uiAlpha * ((reveal.value - 0.6f) / 0.4f).coerceIn(0f, 1f) * steady * dd * (1f - kotlin.math.sqrt(fsNowX() * fsNowX() + fsNowY() * fsNowY()) / (size.height * 0.1f)).coerceIn(0f, 1f)
                        }) {
                            Row(
                                Modifier.align(Alignment.TopStart).graphicsLayer { translationY = -(1f - uiAlpha) * 28.dp.toPx() }
                                    .fillMaxWidth().statusBarsPadding().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassIcon(IBack, Modifier.width(64.dp)) { close() }
                                Text(cleanName(c.name), color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                GlassIcon(if (isFav) IStarOn else IStarOff, Modifier.width(64.dp), if (isFav) Color(0xFFFFD60A) else Color.White) { uiTick++; toggleFav(c) }
                            }
                            Column(
                                Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = (1f - uiAlpha) * 28.dp.toPx() }
                                    .fillMaxWidth().navigationBarsPadding().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            player?.let { GlassVolume(it, { uiTick++ }) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                GlassIcon(if (pipMode) IPip else IHead, Modifier.weight(1f)) {
                                    uiTick++; pipMode = !pipMode; store.setPipMode(pipMode)
                                }
                            }
                            }
                        }

                    }
                }
              }
            }
        }
        }
    }

    if (renameIdx in lists.indices) AlertDialog(
        onDismissRequest = { renameIdx = -1 },
        title = { Text("اسم القائمة") },
        text = { TextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val key = lists[renameIdx]
                val clean = renameText.replace("\t", " ").replace("\n", " ").trim()
                names = if (clean.isEmpty()) names - key else names + (key to clean)
                store.saveNames(names)
                renameIdx = -1
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { renameIdx = -1 }) { Text("إلغاء") } }
    )

    if (showSettings) SettingsDialog(
        mode = themeMode, style = themeStyle,
        onMode = { onTheme(it, themeStyle) }, onStyle = { onTheme(themeMode, it) },
        onClose = { showSettings = false }
    )

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
