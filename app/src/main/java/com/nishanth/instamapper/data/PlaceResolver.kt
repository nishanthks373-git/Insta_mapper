package com.nishanth.instamapper.data

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

/** Works out which real-world place a reel is about, and finds its coordinates. */
object PlaceResolver {

    private val STOP_TAGS = setOf(
        "travel", "travelgram", "travelphotography", "travelblogger", "traveller", "traveler", "travelreels",
        "travelvlog", "traveltheworld", "travelling", "traveling", "travelindia", "reels", "reel", "reelsinstagram",
        "reelitfeelit", "reelkarofeelkaro", "instareels", "explore", "explorepage", "exploremore", "explorer",
        "viral", "viralreels", "trending", "trendingreels", "instagood", "instagram", "instadaily", "photooftheday",
        "photography", "naturephotography", "mobilephotography", "nature", "naturelovers", "wanderlust", "love",
        "india", "incredibleindia", "trip", "vacation", "holiday", "roadtrip", "adventure", "mountains", "beach",
        "fyp", "foryou", "foryoupage", "view", "views", "sunset", "sunrise", "hiking", "trekking", "camping", "sky",
        "beautiful", "beauty", "video", "videography", "drone", "dronephotography", "tourism", "tourist", "follow",
        "like", "share", "weekend", "weekendgetaway", "getaway", "hiddengem", "hiddengems", "offbeat", "budgettravel",
        "solotravel", "couplegoals", "food", "foodie", "cafe", "waterfall", "waterfalls", "lake", "forest",
    )

    suspend fun resolve(ctx: Context, info: ReelInfo, settings: AppSettings): List<Place> {
        val found = LinkedHashMap<String, Place>()
        fun add(p: Place) { if (p.key !in found) found[p.key] = p }

        info.location?.let { loc ->
            if (loc.lat != null && loc.lng != null) {
                add(Place(loc.name, loc.lat, loc.lng, listOfNotNull(loc.address, loc.city).joinToString(", ").ifBlank { null }, "Tagged on Instagram"))
            }
        }

        val queries = LinkedHashMap<String, String>() // query -> source label
        info.location?.let { loc ->
            if (loc.lat == null) queries[listOfNotNull(loc.name, loc.city).distinct().joinToString(", ")] = "Tagged on Instagram"
        }
        if (settings.aiEnabled) {
            runCatching { aiQueries(info, settings) }.getOrNull()?.forEach { queries.putIfAbsent(it, "AI from caption") }
        }
        heuristicQueries(info).forEach { queries.putIfAbsent(it, "From caption") }

        for ((q, label) in queries.entries.take(6)) {
            if (found.size >= 4) break
            geocode(ctx, q, 1).firstOrNull()?.let { add(it.copy(source = label)) }
        }
        return found.values.toList()
    }

    private suspend fun aiQueries(info: ReelInfo, settings: AppSettings): List<String> {
        val prompt = """
            You identify the real-world travel spot shown in an Instagram reel from its metadata.
            Reply with ONLY JSON like {"places":[{"query":"Place name, City, State, Country","confidence":0.9}]}.
            Most likely first, at most 3. The query must be something a map search can find. If unknown, {"places":[]}.

            Creator: @${info.username ?: "unknown"}
            Tagged location: ${info.location?.name ?: "none"}
            Caption:
            ${info.caption?.take(1500) ?: "(none)"}
        """.trimIndent()
        val reply = AiClient.ask(settings, prompt, 300)
        val arr: JSONArray = JsonDig.extractJsonObject(reply)?.optJSONArray("places") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("query")?.takeIf { q -> q.isNotBlank() } }
    }

    fun heuristicQueries(info: ReelInfo): List<String> {
        val out = LinkedHashSet<String>()
        val caption = info.caption.orEmpty()

        fun clean(s: String): String = s
            .replace(Regex("[#@][\\p{L}\\p{N}_.]+"), " ")
            .replace(Regex("[^\\p{L}\\p{N} ,.'’&()-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '-', ':')
            .take(90)

        val markers = listOf("📍", "📌", "🗺", "🏞", "🏝", "🏔")
        for (line in caption.lines()) {
            val m = markers.firstOrNull { line.contains(it) } ?: continue
            clean(line.substringAfter(m)).takeIf { it.length >= 3 }?.let { out += it }
        }
        Regex("""(?i)\b(?:location|loc|place|spot|address|destination)\s*[:\-–]\s*(.+)""").findAll(caption)
            .forEach { clean(it.groupValues[1]).takeIf { s -> s.length >= 3 }?.let { s -> out += s } }
        Regex("""\b(?:in|at|near|visit|visited|explore|exploring)\s+((?:[A-Z][\p{L}'’.-]+)(?:[ ,]+(?:[A-Z][\p{L}'’.-]+)){0,3})""")
            .findAll(caption).take(3)
            .forEach { clean(it.groupValues[1]).takeIf { s -> s.length >= 4 }?.let { s -> out += s } }
        info.hashtags
            .filter { it.length >= 4 && it.lowercase() !in STOP_TAGS && !it.all(Char::isDigit) }
            .take(3)
            .forEach { out += splitCamel(it) }
        return out.toList()
    }

    private fun splitCamel(tag: String): String =
        tag.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ')

    suspend fun geocode(ctx: Context, query: String, limit: Int = 3): List<Place> = withContext(Dispatchers.IO) {
        val viaAndroid = runCatching { androidGeocode(ctx, query, limit) }.getOrDefault(emptyList())
        viaAndroid.ifEmpty { runCatching { nominatim(query, limit) }.getOrDefault(emptyList()) }
    }

    @Suppress("DEPRECATION")
    private fun androidGeocode(ctx: Context, query: String, limit: Int): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        val list = Geocoder(ctx, Locale.getDefault()).getFromLocationName(query, limit) ?: return emptyList()
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val name = a.featureName?.takeUnless { it.matches(Regex("[\\d\\-/ ]+")) }
                ?: a.locality ?: query
            Place(name = name, lat = a.latitude, lng = a.longitude, address = a.getAddressLine(0), source = "Search")
        }
    }

    private suspend fun nominatim(query: String, limit: Int): List<Place> {
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$limit&q=${Uri.encode(query)}"
        val r = Http.get(url, mapOf("User-Agent" to "InstaMapper/1.0 (personal Android app)"))
        if (r.code != 200) return emptyList()
        val arr = JSONArray(r.body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val display = o.optString("display_name")
            Place(
                name = o.optString("name").ifBlank { display.substringBefore(",") },
                lat = lat, lng = lng, address = display, source = "Search",
            )
        }
    }
}
