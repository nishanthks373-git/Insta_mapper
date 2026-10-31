package com.nishanth.instamapper.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.math.BigInteger

/** Thrown when every strategy came back empty; [details] says what each one saw. */
class ReelFetchException(message: String, val details: String) : IOException(message)

/**
 * Collects reel details from Instagram using several strategies, merging whatever each one returns:
 *  1. The web API (only when signed in to Instagram inside the app) – richest data.
 *  2. Instagram's public GraphQL post query – works signed out on most reels.
 *  3. The link-preview page Instagram serves to WhatsApp/Facebook – caption, creator, thumbnail.
 *  4. The normal reel page and the embed page.
 *  5. A hidden in-app browser that opens the reel like Chrome would.
 */
object InstagramFetcher {

    private val URL_RE = Regex("""https?://(?:www\.|m\.)?(?:instagram\.com|instagr\.am)/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val CODE_RE = Regex("""/(?:reels?|p|tv)/([A-Za-z0-9_-]{5,})""")
    private const val IG_APP_ID = "936619743392459"
    private const val LSD = "AVqbxe3J_YA"
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private val GRAPHQL_DOC_IDS = listOf("8845758582119845", "10015901848480474", "24368985919464652")
    private val PREVIEW_UAS = listOf(
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
        "WhatsApp/2.24.20.80 A",
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
    )

    fun extractUrl(text: String): String? = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ']')

    fun shortcodeOf(url: String): String? = CODE_RE.find(url)?.groupValues?.get(1)

    fun isLoggedIn(): Boolean = Http.cookieValue("sessionid") != null

    suspend fun fetch(ctx: Context, input: String): ReelInfo = withContext(Dispatchers.IO) {
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
        val trace = mutableListOf("reel $code · signed in: ${isLoggedIn()}")
        val failures = mutableListOf<Throwable>()

        suspend fun attempt(name: String, block: suspend (MutableList<String>) -> ReelInfo) {
            if (info.isComplete) return
            try {
                val r = block(trace)
                info = info.merge(r)
                trace += "$name → ${summary(r)}"
            } catch (e: Exception) {
                failures += e
                trace += "$name → ✗ ${e.javaClass.simpleName}: ${e.message?.take(140)}"
            }
        }

        if (isLoggedIn()) attempt("api") { fromApi(code, canonical) }
        attempt("graphql") { t -> fromGraphQl(code, canonical, t) }
        attempt("preview") { t -> fromPreview(code, canonical, t) }
        attempt("page") { t -> fromPage(code, canonical, t) }
        attempt("embed") { t -> fromEmbed(code, t) }
        if (info.isEmpty || info.caption == null) attempt("browser") { t -> fromBrowser(ctx, code, canonical, t) }

        if (info.isEmpty && failures.isNotEmpty() && failures.all { it.isNetworkProblem() }) {
            throw NoConnectionException(NO_CONNECTION_HELP)
        }
        if (info.isEmpty) {
            throw ReelFetchException(
                "Instagram didn't share any details for this reel." +
                    if (isLoggedIn()) " Try again in a minute — Instagram may be limiting requests."
                    else " Signing in to Instagram (Settings) usually fixes this.",
                trace.joinToString("\n"),
            )
        }
        info
    }

    private fun summary(r: ReelInfo): String {
        if (r.isEmpty) return "nothing"
        return listOfNotNull(
            r.username?.let { "user" }, r.caption?.let { "caption" }, r.thumbnailUrl?.let { "thumb" },
            r.videoUrl?.let { "video" }, r.location?.let { "location" }, r.audio?.let { "audio" },
        ).joinToString(",")
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

    private fun csrfHeader(): Map<String, String> =
        Http.cookieValue("csrftoken")?.let { mapOf("X-CSRFToken" to it) } ?: emptyMap()

    private fun mediaIdFromShortcode(code: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var id = BigInteger.ZERO
        val sixtyFour = BigInteger.valueOf(64)
        for (c in code.take(11)) {
            id = id.multiply(sixtyFour).add(BigInteger.valueOf(alphabet.indexOf(c).toLong()))
        }
        return id.toString()
    }

    private fun pageTitle(html: String): String =
        Regex("<title[^>]*>([^<]{0,60})", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim().orEmpty()

    private fun stripGuard(body: String) = body.removePrefix("for (;;);").trim()

    private suspend fun fromApi(code: String, canonical: String): ReelInfo {
        val headers = cookieHeaders() + csrfHeader() + mapOf(
            "X-IG-App-ID" to IG_APP_ID,
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to canonical,
            "Accept" to "*/*",
        )
        val r = Http.get("https://www.instagram.com/api/v1/media/${mediaIdFromShortcode(code)}/info/", headers)
        if (r.code != 200) throw IOException("HTTP ${r.code}")
        val item = JSONObject(stripGuard(r.body)).optJSONArray("items")?.optJSONObject(0)
            ?: throw IOException("no items")
        return parseMedia(item, code, "api")
    }

    /** The same query instagram.com runs to show a post. Works signed out for most public reels. */
    private suspend fun fromGraphQl(code: String, canonical: String, t: MutableList<String>): ReelInfo {
        val variables = JSONObject()
            .put("shortcode", code)
            .put("fetch_tagged_user_count", JSONObject.NULL)
            .put("hoisted_comment_id", JSONObject.NULL)
            .put("hoisted_reply_id", JSONObject.NULL)
            .toString()
        val headers = cookieHeaders() + csrfHeader() + mapOf(
            "User-Agent" to DESKTOP_UA,
            "X-IG-App-ID" to IG_APP_ID,
            "X-FB-LSD" to LSD,
            "X-ASBD-ID" to "129477",
            "X-FB-Friendly-Name" to "PolarisPostActionLoadPostQueryQuery",
            "Origin" to "https://www.instagram.com",
            "Referer" to canonical,
            "Sec-Fetch-Site" to "same-origin",
            "Accept" to "*/*",
        )
        for (docId in GRAPHQL_DOC_IDS) {
            val form = "variables=${Uri.encode(variables)}&doc_id=$docId&lsd=$LSD&server_timestamps=true" +
                "&fb_api_caller_class=RelayModern&fb_api_req_friendly_name=PolarisPostActionLoadPostQueryQuery"
            val r = Http.postForm("https://www.instagram.com/graphql/query", form, headers)
            val body = stripGuard(r.body)
            val json = runCatching { JSONObject(body) }.getOrNull()
            val media = json?.let { j -> JsonDig.findObject(j) { isMediaFor(it, code) } }
            t += "  graphql $docId: HTTP ${r.code}, ${body.length}B, media=${media != null}" +
                (json?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.take(60)?.let { ", err=$it" } ?: "")
            if (media != null) return parseMedia(media, code, "graphql")
        }
        return ReelInfo(canonical, code)
    }

    /** Instagram serves Open Graph tags to link-preview bots (WhatsApp, Facebook, Google). */
    private suspend fun fromPreview(code: String, canonical: String, t: MutableList<String>): ReelInfo {
        for (ua in PREVIEW_UAS) {
            val r = Http.get(canonical, mapOf("User-Agent" to ua, "Accept" to "text/html,*/*"))
            val og = fromOpenGraph(Jsoup.parse(r.body, canonical), code, canonical)
            t += "  preview ${ua.substringBefore('/')}: HTTP ${r.code}, '${pageTitle(r.body)}', og=${!og.isEmpty}"
            if (!og.isEmpty) return og.copy(sources = listOf("preview"))
        }
        return ReelInfo(canonical, code)
    }

    private suspend fun fromPage(code: String, canonical: String, t: MutableList<String>): ReelInfo {
        val r = Http.get(canonical, cookieHeaders())
        t += "  page: HTTP ${r.code}, ${r.body.length}B, '${pageTitle(r.body)}', final=${r.finalUrl.take(60)}"
        if (r.code != 200) throw IOException("HTTP ${r.code}")
        val doc = Jsoup.parse(r.body, canonical)
        var info = ReelInfo(url = canonical, shortcode = code)
        info = info.merge(fromJsonScripts(doc.select("script[type=application/json]").map { it.data() }, code, "page-json"))
        return info.merge(fromOpenGraph(doc, code, canonical))
    }

    private fun fromJsonScripts(scripts: List<String>, code: String, source: String): ReelInfo {
        var info = ReelInfo(url = "https://www.instagram.com/reel/$code/", shortcode = code)
        for (data in scripts) {
            if (!data.contains(code)) continue
            val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
            JsonDig.findObject(json) { isMediaFor(it, code) }?.let {
                info = info.merge(parseMedia(it, code, source))
            }
        }
        return info
    }

    private suspend fun fromEmbed(code: String, t: MutableList<String>): ReelInfo {
        val url = "https://www.instagram.com/p/$code/embed/captioned/"
        val r = Http.get(url, cookieHeaders())
        val html = r.body
        if (r.code != 200) {
            t += "  embed: HTTP ${r.code}"
            throw IOException("HTTP ${r.code}")
        }
        var info = ReelInfo(url = "https://www.instagram.com/reel/$code/", shortcode = code)

        // 1) "contextJSON":"{...escaped json...}"
        val ctxIdx = html.indexOf("\"contextJSON\":")
        var ctxNote = "no contextJSON"
        if (ctxIdx >= 0) {
            runCatching {
                val literal = JSONTokener(html.substring(ctxIdx + "\"contextJSON\":".length)).nextValue()
                val ctxJson = JSONObject(literal as String)
                ctxNote = "contextJSON keys=" + ctxJson.keys().asSequence().take(8).joinToString("|")
                val media = JsonDig.findObject(ctxJson) { isMediaFor(it, code) }
                    ?: JsonDig.findObject(ctxJson) { it.optString("shortcode") == code || it.optString("code") == code }
                if (media != null) info = info.merge(parseMedia(media, code, "embed-json"))
                // Flat "context" object used for some videos
                ctxJson.optJSONObject("context")?.let { c ->
                    info = info.merge(
                        ReelInfo(
                            url = info.url, shortcode = code,
                            username = c.str("username"),
                            caption = c.str("caption_title")?.let { Jsoup.parse(it).text() } ?: c.str("caption"),
                            thumbnailUrl = c.str("display_src") ?: c.str("thumbnail_src"),
                            videoUrl = c.str("video_url"),
                            sources = listOf("embed-context"),
                        )
                    )
                }
            }.onFailure { ctxNote = "contextJSON unreadable" }
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
        t += "  embed: HTTP 200, ${html.length}B, $ctxNote, captionEl=${captionEl != null}"
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

    /** Opens the reel in a hidden WebView (real browser engine, shares your Instagram sign-in). */
    private suspend fun fromBrowser(ctx: Context, code: String, canonical: String, t: MutableList<String>): ReelInfo {
        val raw = withContext(Dispatchers.Main) {
            withTimeoutOrNull(30_000) { WebScraper.scrape(ctx, canonical, code) }
        }
        if (raw == null) {
            t += "  browser: timed out"
            return ReelInfo(canonical, code)
        }
        val o = JSONObject(raw)
        o.optString("error").takeIf { it.isNotBlank() }?.let { t += "  browser: js error $it" }
        val meta = o.optJSONObject("meta") ?: JSONObject()
        val scriptsArr = o.optJSONArray("scripts")
        val scripts = (0 until (scriptsArr?.length() ?: 0)).map { scriptsArr!!.optString(it) }
        t += "  browser: '${o.optString("title").take(50)}', url=${o.optString("url").take(60)}, scripts=${scripts.size}, og=${meta.has("og:description")}"

        var info = fromJsonScripts(scripts, code, "browser-json")
        info = info.merge(ogInfo({ meta.str(it) }, code, canonical).copy(sources = listOf("browser-og")))
        if (info.caption == null && o.optString("url").contains(code)) {
            o.str("h1")?.takeIf { it.length > 3 }?.let { info = info.merge(ReelInfo(canonical, code, caption = it)) }
        }
        return info
    }

    private fun fromOpenGraph(doc: Document, code: String, canonical: String): ReelInfo =
        ogInfo({ p ->
            (doc.selectFirst("meta[property=$p]") ?: doc.selectFirst("meta[name=$p]"))
                ?.attr("content")?.takeIf { it.isNotBlank() }
        }, code, canonical)

    private fun ogInfo(og: (String) -> String?, code: String, canonical: String): ReelInfo {
        val desc = og("og:description") ?: og("description")
        val title = og("og:title")
        if (desc == null && title == null) return ReelInfo(canonical, code)

        // e.g. 1,234 likes, 56 comments - someuser on March 3, 2026: "caption text".
        val quoted = Regex(""":\s*["“](.+)["”]\s*\.?\s*$""", RegexOption.DOT_MATCHES_ALL)
        val caption = desc?.let { quoted.find(it)?.groupValues?.get(1) }
            ?: title?.let { quoted.find(it)?.groupValues?.get(1) }
        val username = desc?.let { Regex("""-\s*([A-Za-z0-9._]+)\s+on\s""").find(it)?.groupValues?.get(1) }
        // A generic "log in to Instagram" page has OG tags too – ignore it.
        val ogUrl = og("og:url").orEmpty()
        if (caption == null && username == null && !ogUrl.contains(code)) return ReelInfo(canonical, code)

        val likes = desc?.let { Regex("""^([\d,]+)\s+likes?""").find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
        val comments = desc?.let { Regex("""([\d,]+)\s+comments?""").find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
        val fullName = title?.substringBefore(" on Instagram", "")?.takeIf { it.isNotBlank() }

        return ReelInfo(
            url = canonical, shortcode = code,
            username = username,
            fullName = fullName,
            caption = caption?.trim(),
            thumbnailUrl = og("og:image"),
            videoUrl = (og("og:video:secure_url") ?: og("og:video"))?.takeUnless { it.startsWith("blob:") },
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
