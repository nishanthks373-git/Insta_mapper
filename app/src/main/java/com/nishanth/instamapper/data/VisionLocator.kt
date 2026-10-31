package com.nishanth.instamapper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Watches the reel (or still frames from it) together with the caption and asks a vision AI for the
 * specific named spot: the waterfall, café, viewpoint or temple – not just the town.
 */
object VisionLocator {

    data class Candidate(
        val name: String,
        val area: String?,
        val query: String,
        val lat: Double?,
        val lng: Double?,
        val confidence: Double,
        val evidence: String?,
    )

    /** Gemini takes the whole clip inline up to this size (it also hears the narration). */
    private const val MAX_INLINE_VIDEO = 14L * 1024 * 1024
    private const val MAX_DOWNLOAD = 80L * 1024 * 1024
    private const val FRAME_COUNT = 10

    suspend fun locate(
        ctx: Context,
        info: ReelInfo,
        settings: AppSettings,
        progress: (String) -> Unit,
    ): List<Candidate> = withContext(Dispatchers.IO) {
        var video: File? = null
        var mediaNote = "no video or image"
        val frames = mutableListOf<ByteArray>()
        var inlineVideo: ByteArray? = null

        try {
            info.videoUrl?.let { url ->
                progress("Downloading the reel…")
                val f = File(ctx.cacheDir, "analyze_${info.shortcode}.mp4")
                runCatching {
                    Http.download(url, f, MAX_DOWNLOAD, mapOf("Referer" to "https://www.instagram.com/"))
                    video = f
                }
            }
            val v = video
            if (v != null) {
                if (settings.provider == AiProvider.GEMINI && v.length() <= MAX_INLINE_VIDEO) {
                    inlineVideo = v.readBytes()
                    mediaNote = "the full video (watch it and listen to any narration)"
                } else {
                    progress("Picking frames from the video…")
                    frames += extractFrames(v)
                    mediaNote = "${frames.size} frames taken evenly through the video"
                }
            }
            if (inlineVideo == null && frames.isEmpty()) {
                info.thumbnailUrl?.let { url ->
                    progress("Looking at the cover image…")
                    runCatching {
                        val f = File(ctx.cacheDir, "analyze_${info.shortcode}.jpg")
                        Http.download(url, f, 10L * 1024 * 1024)
                        BitmapFactory.decodeFile(f.absolutePath)?.let { frames += jpeg(it) }
                        f.delete()
                    }
                    if (frames.isNotEmpty()) mediaNote = "the reel's cover image only"
                }
            }

            progress(if (inlineVideo != null) "AI is watching the reel…" else "AI is studying the frames…")
            val reply = AiClient.ask(
                settings,
                prompt(info, mediaNote),
                maxTokens = 1500,
                media = if (inlineVideo != null || frames.isNotEmpty()) AiClient.Media(frames, inlineVideo) else null,
                json = true,
            )
            parse(reply)
        } finally {
            video?.delete()
        }
    }

    private fun prompt(info: ReelInfo, mediaNote: String) = """
        You are a travel location detective. Work out the EXACT spot shown in this Instagram reel – the specific
        named place a traveller would type into Google Maps (a particular waterfall, beach, viewpoint, trek,
        temple, fort, café, homestay, lake, street), not just the city, state or country.

        You are given $mediaNote, plus the caption and other details below. Use every clue:
        text written on the video, signboards, shop and hotel names, milestones, number plates' region codes,
        recognisable landmarks and terrain, what the creator says, the caption, hashtags, the tagged location and
        any @mentions of places.

        Reply with ONLY this JSON:
        {"places":[{"name":"Exact place name","area":"Village/Town, District, State, Country",
          "query":"Exact place name, Town, State, Country","lat":12.3456,"lng":76.5432,
          "confidence":0.0,"evidence":"one short line: what you saw, read or heard"}]}

        Rules: most likely first. If the reel shows several different spots (e.g. "5 places to visit in X"), list
        each one in the order shown, up to 6. Give lat/lng only if you genuinely know where that spot is, else null.
        If you can only narrow it to a town or region, still answer with that area and confidence below 0.4.
        If there is no clue at all, return {"places":[]}.

        Creator: @${info.username ?: "unknown"}${info.fullName?.let { " ($it)" } ?: ""}
        Tagged location: ${info.location?.let { listOfNotNull(it.name, it.city).joinToString(", ") } ?: "none"}
        Audio: ${info.audio?.let { listOfNotNull(it.title, it.artist).joinToString(" – ") } ?: "unknown"}
        Caption:
        ${info.caption?.take(2500) ?: "(no caption)"}
    """.trimIndent()

    private fun parse(reply: String): List<Candidate> {
        val arr = JsonDig.extractJsonObject(reply)?.optJSONArray("places") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val area = o.str("area")
            Candidate(
                name = name,
                area = area,
                query = o.str("query") ?: listOfNotNull(name, area).joinToString(", "),
                lat = o.double("lat")?.takeIf { it in -90.0..90.0 && it != 0.0 },
                lng = o.double("lng")?.takeIf { it in -180.0..180.0 && it != 0.0 },
                confidence = o.optDouble("confidence", 0.5).let { if (it.isNaN()) 0.5 else it },
                evidence = o.str("evidence"),
            )
        }
    }

    private fun extractFrames(file: File): List<ByteArray> {
        val mmr = MediaMetadataRetriever()
        val out = mutableListOf<ByteArray>()
        try {
            mmr.setDataSource(file.absolutePath)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durMs <= 0) return out
            for (i in 0 until FRAME_COUNT) {
                val tUs = (durMs * (i + 0.5) / FRAME_COUNT * 1000).toLong()
                val bmp = if (Build.VERSION.SDK_INT >= 27) {
                    mmr.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 768, 768)
                } else {
                    mmr.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                bmp?.let { out += jpeg(it) }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        return out
    }

    private fun jpeg(src: Bitmap): ByteArray {
        val longest = max(src.width, src.height)
        val bmp = if (longest > 768) {
            val scale = 768f / longest
            Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true)
        } else src
        return ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 72, bos)
            bos.toByteArray()
        }
    }
}
