package com.nishanth.instamapper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.math.BigInteger

/**
 * Collects reel details from Instagram using several strategies, merging whatever each one returns:
 *  1. The web API (only when you've signed in to Instagram inside the app) – richest data.
 *  2. The public reel page (Open Graph tags + embedded JSON).
 *  3. The public embed page (caption, username, often the video URL).
 */
object InstagramFetcher {

    private val URL_RE = Regex("""https?://(?:www\.|m\.)?(?:instagram\.com|instagr\.am)/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val CODE_RE = Regex("""/(?:reels?|p|tv)/([A-Za-z0-9_-]{5,})""")
    private const val IG_APP_ID = "936619743392459"

    fun extractUrl(text: String): String? = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ']')

    fun shortcodeOf(url: String): String? = CODE_RE.find(url)?.groupValues?.get(1)

    fun isLoggedIn(): Boolean = Http.cookieValue("sessionid") != null

    suspend fun fetch(input: String): ReelInfo = withContext(Dispatchers.IO) {
        val link = extractUrl(input.trim())
            ?: throw IllegalArgumentException("That doesn't look like an Instagram link.")
        val code = shortcodeOf(link)
            ?: try {
                resolveShareLink(link)
            } catch (e: Exception) {
                if (e.isNetworkProblem()) throw NoConnectionException(NO_CONNECTION_HELP) else throw e
            }
            ?: throw IllegalArgumentException("Couldn't find the reel ID in that link.")

        val canonical = "https://www.instagram.com/reel/$code/"
        var info = ReelInfo(url = canonical, shortcode = code)
        val errors = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()

        if (isLoggedIn()) {
            runCatching { info = info.merge(fromApi(code, canonical)) }
                .onFailure { errors += "api: ${it.message}"; failures += it }
        }
        if (!info.isComplete) {
            runCatching { info = info.merge(fromPage(code, canonical)) }
                .onFailure { errors += "page: ${it.message}"; failures += it }
        }
        if (!info.isComplete) {
            runCatching { info = info.merge(fromEmbed(code)) }
                .onFailure { errors += "embed: ${it.message}"; failures += it }
        }

        if (info.isEmpty && failures.isNotEmpty() && failures.all { it.isNetworkProblem() }) {
            throw NoConnectionException(NO_CONNECTION_HELP)
        }
        if (info.isEmpty) {
            throw IOException(
                "Instagram didn't share any details for this reel. It may be private or Instagram is " +
                    "limiting requests — sign in to Instagram from Settings and try again." +
                    if (errors.isNotEmpty()) "\n(${errors.joinToString("; ")})" else ""
            )
        }
        info
    }

    // ---------------------------------------------------------------------------------

    /** instagram.com/share/... links redirect to the real reel URL. */
    private suspend fun resolveShareLink(link: String): String? {
        val r = Http.get(link, cookieHeaders())
        shortcodeOf(r.finalUrl)?.let { return it }
        // Logged-out redirects look like /accounts/login/?next=/reel/CODE/
        val next = Regex("""next=([^&]+)""").find(r.finalUrl)?.groupValues?.get(1)
        next?.let { shortcodeOf(java.net.URLDecoder.decode(it, "UTF-8"))?.let { c -> return c } }
        val doc = Jsoup.parse(r.body)
        doc.selectFirst("meta[property=og:url]")?.attr("content")?.let { shortcodeOf(it)?.let { c -> return c } }
        return shortcodeOf(r.body)
    }

    private fun cookieHeaders(): Map<String, String> =
        Http.instagramCookies()?.let { mapOf("Cookie" to it) } ?: emptyMap()

    private fun mediaIdFromShortcode(code: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var id = BigInteger.ZERO
        val sixtyFour = BigInteger.valueOf(64)
        for (c in code.take(11)) {
            id = id.multiply(sixtyFour).add(BigInteger.valueOf(alphabet.indexOf(c).toLong()))
        }
        return id.toString()
    }

    private suspend fun fromApi(code: String, canonical: String): ReelInfo {
        val headers = cookieHeaders() + mapOf(
            "X-IG-App-ID" to IG_APP_ID,
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to canonical,
            "Accept" to "*/*",
        ) + (Http.cookieValue("csrftoken")?.let { mapOf("X-CSRFToken" to it) } ?: emptyMap())
        val r = Http.get("https://www.instagram.com/api/v1/media/${mediaIdFromShortcode(code)}/info/", headers)
        if (r.code != 200) throw IOException("HTTP ${r.code}")
        val item = JSONObject(r.body).optJSONArray("items")?.optJSONObject(0)
            ?: throw IOException("no items")
        return parseMedia(item, code, "api")
    }

    private suspend fun fromPage(code: String, canonical: String): ReelInfo {
        val r = Http.get(canonical, cookieHeaders())
        if (r.code != 200) throw IOException("HTTP ${r.code}")
        val doc = Jsoup.parse(r.body, canonical)
        var info = ReelInfo(url = canonical, shortcode = code)

        // Embedded JSON blobs (present for signed-in sessions)
        for (script in doc.select("script[type=application/json]")) {
            val data = script.data()
            if (!data.contains(code)) continue
            val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
            JsonDig.findObject(json) { isMediaFor(it, code) }?.let {
                info = info.merge(parseMedia(it, code, "page-json"))
            }
        }
        return info.merge(fromOpenGraph(doc, code, canonical))
    }

    private suspend fun fromEmbed(code: String): ReelInfo {
        val url = "https://www.instagram.com/p/$code/embed/captioned/"
        val r = Http.get(url, cookieHeaders())
        if (r.code != 200) throw IOException("HTTP ${r.code}")
        val html = r.body
        var info = ReelInfo(url = "https://www.instagram.com/reel/$code/", shortcode = code)

        // 1) "contextJSON":"{...escaped json...}"
        val ctxIdx = html.indexOf("\"contextJSON\":")
        if (ctxIdx >= 0) {
            runCatching {
                val literal = JSONTokener(html.substring(ctxIdx + "\"contextJSON\":".length)).nextValue()
                val ctx = JSONObject(literal as String)
                JsonDig.findObject(ctx) { isMediaFor(it, code) }?.let {
                    info = info.merge(parseMedia(it, code, "embed-json"))
                }
            }
        }
        // 2) window.__additionalDataLoaded('extra', {...})
        val extraIdx = html.indexOf("__additionalDataLoaded(")
        if (extraIdx >= 0) {
            runCatching {
                val brace = html.indexOf('{', extraIdx)
                val obj = JSONTokener(html.substring(brace)).nextValue() as JSONObject
                JsonDig.findObject(obj) { isMediaFor(it, code) }?.let {
                    info = info.merge(parseMedia(it, code, "embed-extra"))
                }
            }
        }
        // 3) Visible HTML
        val doc = Jsoup.parse(html, url)
        doc.select("br").forEach { it.after(TextNode("\n")) }
        val captionEl = doc.selectFirst(".Caption")
        val caption = captionEl?.clone()?.apply {
            select(".CaptionUsername, .CaptionComments").remove()
        }?.wholeText()?.trim()?.takeIf { it.isNotBlank() }
        val username = doc.selectFirst(".UsernameText, .CaptionUsername, a.Username")?.text()?.trim()
        val thumb = doc.selectFirst("img.EmbeddedMediaImage")?.absUrl("src")
        info = info.merge(
            ReelInfo(
                url = info.url, shortcode = code,
                username = username?.takeIf { it.isNotBlank() },
                caption = caption,
                thumbnailUrl = thumb?.takeIf { it.isNotBlank() },
                sources = listOf("embed-html"),
            )
        )
        return info
    }

    private fun fromOpenGraph(doc: Document, code: String, canonical: String): ReelInfo {
        fun og(p: String) = doc.selectFirst("meta[property=$p]")?.attr("content")?.takeIf { it.isNotBlank() }
        val desc = og("og:description") ?: doc.selectFirst("meta[name=description]")?.attr("content")
        val title = og("og:title")
        if (desc == null && title == null) return ReelInfo(canonical, code)

        // e.g. 1,234 likes, 56 comments - someuser on March 3, 2026: "caption text".
        val quoted = Regex(""":\s*["“](.+)["”]\s*\.?\s*$""", RegexOption.DOT_MATCHES_ALL)
        val caption = desc?.let { quoted.find(it)?.groupValues?.get(1) }
            ?: title?.let { quoted.find(it)?.groupValues?.get(1) }
        val username = desc?.let { Regex("""-\s*([A-Za-z0-9._]+)\s+on\s""").find(it)?.groupValues?.get(1) }
        val likes = desc?.let { Regex("""^([\d,]+)\s+likes?""").find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
        val comments = desc?.let { Regex("""([\d,]+)\s+comments?""").find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
        val fullName = title?.substringBefore(" on Instagram", "")?.takeIf { it.isNotBlank() }

        return ReelInfo(
            url = canonical, shortcode = code,
            username = username,
            fullName = fullName,
            caption = caption?.trim(),
            thumbnailUrl = og("og:image"),
            videoUrl = og("og:video:secure_url") ?: og("og:video"),
            likes = likes, comments = comments,
            sources = listOf("og"),
        )
    }

    private fun isMediaFor(o: JSONObject, code: String): Boolean {
        val matches = o.optString("code") == code || o.optString("shortcode") == code
        if (!matches) return false
        return o.has("caption") || o.has("video_versions") || o.has("edge_media_to_caption") ||
            o.has("video_url") || o.has("owner") || o.has("user")
    }

    /** Understands both the v1 API shape and the older GraphQL "shortcode_media" shape. */
    private fun parseMedia(m: JSONObject, code: String, source: String): ReelInfo {
        val user = m.obj("user") ?: m.obj("owner")
        val caption = m.obj("caption")?.str("text")
            ?: m.str("caption")
            ?: m.obj("edge_media_to_caption")?.firstOf("edges")?.obj("node")?.str("text")
        val video = m.firstOf("video_versions")?.str("url") ?: m.str("video_url")
        val thumb = m.obj("image_versions2")?.firstOf("candidates")?.str("url")
            ?: m.str("display_url") ?: m.str("thumbnail_src")
        val likes = m.long("like_count")
            ?: m.obj("edge_media_preview_like")?.long("count")
            ?: m.obj("edge_liked_by")?.long("count")
        val comments = m.long("comment_count")
            ?: m.obj("edge_media_to_comment")?.long("count")
            ?: m.obj("edge_media_preview_comment")?.long("count")
        val views = m.long("play_count") ?: m.long("ig_play_count")
            ?: m.long("video_play_count") ?: m.long("video_view_count") ?: m.long("view_count")
        val takenAt = m.long("taken_at") ?: m.long("taken_at_timestamp")

        val location = m.obj("location")?.let { l ->
            l.str("name")?.let { name ->
                TaggedLocation(
                    name = name,
                    id = l.str("pk") ?: l.str("id"),
                    lat = l.double("lat")?.takeIf { it != 0.0 },
                    lng = l.double("lng")?.takeIf { it != 0.0 },
                    address = l.str("address"),
                    city = l.str("city"),
                )
            }
        }

        val clips = m.obj("clips_metadata")
        val music = clips?.obj("music_info")?.obj("music_asset_info")
        val original = clips?.obj("original_sound_info")
        val attribution = m.obj("clips_music_attribution_info")
        val audio = when {
            music != null -> AudioInfo(
                title = music.str("title"),
                artist = music.str("display_artist"),
                audioId = music.str("audio_cluster_id") ?: music.str("audio_asset_id"),
                isOriginal = false,
            )
            original != null -> AudioInfo(
                title = original.str("original_audio_title") ?: "Original audio",
                artist = original.obj("ig_artist")?.str("username"),
                audioId = original.str("audio_asset_id") ?: original.str("original_media_id"),
                isOriginal = true,
            )
            attribution != null -> AudioInfo(
                title = attribution.str("song_name"),
                artist = attribution.str("artist_name"),
                audioId = attribution.str("audio_id"),
                isOriginal = attribution.optBoolean("uses_original_audio", false),
            )
            else -> null
        }

        return ReelInfo(
            url = "https://www.instagram.com/reel/$code/",
            shortcode = code,
            username = user?.str("username"),
            fullName = user?.str("full_name"),
            caption = caption,
            thumbnailUrl = thumb,
            videoUrl = video,
            likes = likes,
            comments = comments,
            views = views,
            takenAt = takenAt,
            location = location,
            audio = audio,
            sources = listOf(source),
        )
    }
}
