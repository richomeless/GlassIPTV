package com.glass.iptv

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.net.URL

const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

// بعض السيرفرات ترسل EXT-X-MEDIA بدون NAME، وExoPlayer يرفضها. نصلحها قبل التحليل
fun fixHls(t: String): String = t.lineSequence().joinToString("\n") { l ->
    if (l.startsWith("#EXT-X-MEDIA:") && !Regex("[:,]NAME=").containsMatchIn(l))
        l.replaceFirst("#EXT-X-MEDIA:", "#EXT-X-MEDIA:NAME=\"audio\",") else l
}

@androidx.annotation.OptIn(UnstableApi::class)
class FixDs(private val up: DataSource) : DataSource {
    private var buf: ByteArray? = null
    private var pos = 0
    private var last: Uri? = null

    override fun addTransferListener(l: TransferListener) = up.addTransferListener(l)

    override fun open(spec: DataSpec): Long {
        buf = null
        if (!spec.uri.toString().contains(".m3u8")) return up.open(spec)
        up.open(spec)
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (true) {
            val r = up.read(tmp, 0, tmp.size)
            if (r == C.RESULT_END_OF_INPUT) break
            out.write(tmp, 0, r)
        }
        last = up.uri ?: spec.uri
        up.close()
        val b = fixHls(out.toString("UTF-8")).toByteArray()
        buf = b; pos = 0
        return b.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val x = buf ?: return up.read(buffer, offset, length)
        if (pos >= x.size) return C.RESULT_END_OF_INPUT
        val c = minOf(length, x.size - pos)
        System.arraycopy(x, pos, buffer, offset, c)
        pos += c
        return c
    }

    override fun getUri(): Uri? = if (buf != null) last else up.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (buf != null) emptyMap() else up.responseHeaders

    override fun close() { buf = null; up.close() }
}

// هيدرز القناة الحالية (Referer / User-Agent) من قائمة M3U
object Hdr {
    @Volatile var ref = ""
    @Volatile var ua = ""
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val ds = ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec ->
            val h = mutableMapOf("User-Agent" to Hdr.ua.ifEmpty { BROWSER_UA })
            val r = Hdr.ref
            if (r.isNotEmpty()) {
                h["Referer"] = r
                runCatching { URL(r).let { h["Origin"] = "${it.protocol}://${it.host}" } }
            }
            spec.withAdditionalHeaders(h)
        })
        val fixed = DataSource.Factory { FixDs(ds.createDataSource()) }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(fixed))
            // false = ما نعتمد على الـ audio focus، عشان MixedAudio ما يوقف التشغيل
            .setAudioAttributes(AudioAttributes.DEFAULT, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // الضغط على إشعار التشغيل يفتح التطبيق على الفيديو
        val open = android.app.PendingIntent.getActivity(
            this, 0,
            android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_player", true)
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
