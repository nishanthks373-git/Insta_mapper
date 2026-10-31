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

    data class Resolution(val places: List<Place>, val note: String?)

    suspend fun resolve(
        ctx: Context,
        info: ReelInfo,
        settings: AppSettings,
        progress: (String) -> Unit = {},
    ): Resolution {
        val found = LinkedHashMap<String, Place>()
        fun add(p: Place) { if (p.key !in found) found[p.key] = p }
        var note: String? = null

        // 1) Instagram location tag with coordinates – these are real places, usually the spot itself.
        info.location?.let { loc ->
            if (loc.lat != null && loc.lng != null) {
                add(
                    Place(
                        loc.name, loc.lat, loc.lng,
                        listOfNotNull(loc.address, loc.city).joinToString(", ").ifBlank { null },
                        "Tagged on Instagram", exact = true,
                        mapsQuery = listOfNotNull(loc.name, loc.city).distinct().joinToString(", "),
                    )
                )
            }
        }

        // 2) AI watches the video / frames + reads the caption and names the exact spot(s).
        if (settings.aiEnabled) {
            try {
                val candidates = VisionLocator.locate(ctx, info, settings, progress)
                if (candidates.isEmpty()) note = "The AI couldn't spot a specific place in this reel."
                for (c in candidates.take(6)) {
                    progress("Pinning ${c.name} on the map…")
                    pin(ctx, c)?.let { add(it) }
                }
            } catch (e: Exception) {
                note = "AI video check failed: ${e.message?.take(160)}"
            }
        } else {
            note = "Tip: add a free Gemini key in Settings and the app will watch the video to find the exact spot."
        }

        // 3) Tagged location without coordinates, then caption clues.
        if (found.values.none { it.exact }) {
            progress("Reading the caption for clues…")
            val queries = LinkedHashMap<String, String>()
            info.location?.let { loc ->
                if (loc.lat == null) queries[listOfNotNull(loc.name, loc.city).distinct().joinToString(", ")] = "Tagged on Instagram"
            }
            heuristicQueries(info).forEach { queries.putIfAbsent(it, "From caption") }
            for ((q, label) in queries.entries.take(5)) {
                if (found.size >= 6) break
                bestHit(ctx, q, null)?.let { add(it.copy(source = label, mapsQuery = q)) }
            }
        }

        // Exact spots first, keeping the AI's order among them.
        return Resolution(found.values.sortedByDescending { it.exact }, note)
    }

    /** Finds coordinates for an AI candidate, preferring the named point itself over its town. */
    private suspend fun pin(ctx: Context, c: VisionLocator.Candidate): Place? {
        val evidence = c.evidence
        val label = "AI · video & caption"
        val queries = listOfNotNull(
            c.query,
            c.area?.let { "${c.name}, $it" },
            c.area?.substringAfterLast(",")?.trim()?.takeIf { it.isNotBlank() }?.let { "${c.name}, $it" },
        ).distinct()

        for (q in queries) {
            val hit = bestHit(ctx, q, c.lat?.let { lat -> c.lng?.let { lng -> lat to lng } })
            if (hit != null && hit.exact) {
                return hit.copy(name = c.name, source = label, mapsQuery = c.query, evidence = evidence)
            }
        }

        // No map database knows the point itself. Trust the AI's coordinates if they sit in the right area.
        val areaHit = c.area?.let { bestHit(ctx, it, null) } ?: bestHit(ctx, c.query, null)
        if (c.lat != null && c.lng != null) {
            val plausible = areaHit == null || distanceKm(areaHit.lat, areaHit.lng, c.lat, c.lng) < 120
            if (plausible) {
                return Place(
                    c.name, c.lat, c.lng, c.area, label, exact = c.confidence >= 0.5,
                    mapsQuery = c.query, evidence = evidence,
                )
            }
        }
        return areaHit?.copy(name = c.name, address = c.area ?: areaHit.address, source = label, exact = false, mapsQuery = c.query, evidence = evidence)
    }

    private val AREA_TYPES = setOf(
        "country", "state", "region", "province", "county", "state_district", "district", "city", "town",
        "village", "municipality", "suburb", "neighbourhood", "quarter", "borough", "postcode", "administrative",
        "hamlet", "locality", "city_district", "subdistrict", "continent", "archipelago",
    )

    private var lastNominatim = 0L

    /** Best map hit for [query]; `exact` is true when it is a named point (falls, temple, café…), not a region. */
    private suspend fun bestHit(ctx: Context, query: String, near: Pair<Double, Double>?): Place? {
        val close = { p: Place -> near == null || distanceKm(p.lat, p.lng, near.first, near.second) < 80 }
        // OpenStreetMap knows what kind of thing each result is.
        val osm = runCatching { nominatimHits(query, 5) }.getOrDefault(emptyList())
        osm.firstOrNull { it.exact && close(it) }?.let { return it }
        // Android's geocoder (Google data on most phones) often knows small businesses and viewpoints.
        val android = runCatching { withContext(Dispatchers.IO) { androidGeocode(ctx, query, 3) } }.getOrDefault(emptyList())
        android.firstOrNull { it.exact && close(it) }?.let { return it }
        return osm.firstOrNull(close) ?: android.firstOrNull(close) ?: osm.firstOrNull() ?: android.firstOrNull()
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
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

    /** Manual search from the place sheet. */
    suspend fun geocode(ctx: Context, query: String, limit: Int = 3): List<Place> = withContext(Dispatchers.IO) {
        val osm = runCatching { nominatimHits(query, limit) }.getOrDefault(emptyList())
        val android = runCatching { androidGeocode(ctx, query, limit) }.getOrDefault(emptyList())
        (osm.filter { it.exact } + android.filter { it.exact } + osm + android)
            .map { it.copy(mapsQuery = it.mapsQuery ?: query) }
            .distinctBy { it.key }
            .take(limit + 2)
    }

    @Suppress("DEPRECATION")
    private fun androidGeocode(ctx: Context, query: String, limit: Int): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        val list = Geocoder(ctx, Locale.getDefault()).getFromLocationName(query, limit) ?: return emptyList()
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val feature = a.featureName?.takeUnless { it.matches(Regex("[\\d\\-/ ]+")) }
            val areaNames = setOfNotNull(a.locality, a.subLocality, a.subAdminArea, a.adminArea, a.countryName)
            val isPoint = feature != null && feature !in areaNames && a.thoroughfare != feature
            Place(
                name = feature ?: a.locality ?: query,
                lat = a.latitude, lng = a.longitude,
                address = a.getAddressLine(0),
                source = "Search",
                exact = isPoint,
            )
        }
    }

    private suspend fun nominatimHits(query: String, limit: Int): List<Place> {
        // OpenStreetMap asks for at most one request per second.
        val wait = 1100 - (System.currentTimeMillis() - lastNominatim)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastNominatim = System.currentTimeMillis()
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$limit&q=${Uri.encode(query)}"
        val r = Http.get(url, mapOf("User-Agent" to "InstaMapper/1.1 (Android app; github.com/nishanthks373-git/Insta_mapper)"))
        if (r.code != 200) return emptyList()
        val arr = JSONArray(r.body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val display = o.optString("display_name")
            val category = o.optString("category").ifBlank { o.optString("class") }
            val type = o.optString("type")
            val addressType = o.optString("addresstype")
            val isArea = category == "boundary" ||
                (category == "place" && type in AREA_TYPES) ||
                addressType in AREA_TYPES
            Place(
                name = o.optString("name").ifBlank { display.substringBefore(",") },
                lat = lat, lng = lng, address = display, source = "Search",
                exact = !isArea,
            )
        }
    }
}
