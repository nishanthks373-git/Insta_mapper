package com.nishanth.instamapper.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsRepo(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        provider = runCatching { AiProvider.valueOf(prefs.getString("provider", "NONE")!!) }.getOrDefault(AiProvider.NONE),
        apiKey = prefs.getString("apiKey", "").orEmpty(),
        model = prefs.getString("model", "").orEmpty(),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("provider", s.provider.name)
            .putString("apiKey", s.apiKey.trim())
            .putString("model", s.model.trim())
            .apply()
    }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply() }
}

data class RecentReel(
    val url: String,
    val username: String?,
    val caption: String?,
    val thumbnailUrl: String?,
    val place: String?,
    val at: Long,
)

/** The last few reels you opened, so they're one tap away. */
class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<RecentReel> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            RecentReel(
                url = o.getString("url"),
                username = o.str("username"),
                caption = o.str("caption"),
                thumbnailUrl = o.str("thumb"),
                place = o.str("place"),
                at = o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    fun add(current: List<RecentReel>, info: ReelInfo): List<RecentReel> {
        val item = RecentReel(
            url = info.url,
            username = info.username,
            caption = info.caption?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(120),
            thumbnailUrl = info.thumbnailUrl,
            place = info.location?.name,
            at = System.currentTimeMillis(),
        )
        val list = (listOf(item) + current.filterNot { it.url == item.url }).take(12)
        save(list)
        return list
    }

    fun save(list: List<RecentReel>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("url", it.url)
                    .put("username", it.username ?: JSONObject.NULL)
                    .put("caption", it.caption ?: JSONObject.NULL)
                    .put("thumb", it.thumbnailUrl ?: JSONObject.NULL)
                    .put("place", it.place ?: JSONObject.NULL)
                    .put("at", it.at)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

/** Saved spots, kept as a small JSON file in app storage. */
class SpotStore(private val context: Context) {
    private val file = File(context.filesDir, "spots.json")

    fun load(): List<Spot> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Spot(
                id = o.getString("id"),
                name = o.getString("name"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                address = o.str("address"),
                reelUrl = o.optString("reelUrl"),
                username = o.str("username"),
                thumbnailUrl = o.str("thumbnailUrl"),
                savedAt = o.optLong("savedAt"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(spots: List<Spot>) {
        val arr = JSONArray()
        spots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id).put("name", s.name).put("lat", s.lat).put("lng", s.lng)
                    .put("address", s.address ?: JSONObject.NULL)
                    .put("reelUrl", s.reelUrl)
                    .put("username", s.username ?: JSONObject.NULL)
                    .put("thumbnailUrl", s.thumbnailUrl ?: JSONObject.NULL)
                    .put("savedAt", s.savedAt)
            )
        }
        file.writeText(arr.toString())
    }

    /** Writes a KML file you can import into Google My Maps / Google Earth. */
    fun exportKml(spots: List<Spot>): Uri {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val kml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>Insta Mapper spots</name>""").append('\n')
            spots.forEach { s ->
                append("<Placemark><name>").append(esc(s.name)).append("</name>")
                append("<description>").append(esc(listOfNotNull(s.address, s.username?.let { "Reel by @$it" }, s.reelUrl).joinToString("\n"))).append("</description>")
                append("<Point><coordinates>").append(s.lng).append(',').append(s.lat).append(",0</coordinates></Point></Placemark>\n")
            }
            append("</Document></kml>\n")
        }
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val out = File(dir, "insta_mapper_spots.kml")
        out.writeText(kml)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", out)
    }
}
