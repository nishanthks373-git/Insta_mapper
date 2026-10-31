package com.nishanth.instamapper.data

import com.nishanth.instamapper.BuildConfig
import java.util.Locale
import kotlin.math.roundToInt

/** Adapts units and language to whoever is using the phone. */
object Locales {
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")
    private val INCH_COUNTRIES = setOf("US", "LR", "MM")

    val useFahrenheit: Boolean get() = Locale.getDefault().country.uppercase() in FAHRENHEIT_COUNTRIES
    private val useInches: Boolean get() = Locale.getDefault().country.uppercase() in INCH_COUNTRIES

    fun temp(celsius: Double): String =
        if (useFahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()}°F" else "${celsius.roundToInt()}°C"

    fun tempShort(celsius: Double): String =
        if (useFahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()}°" else "${celsius.roundToInt()}°"

    fun rain(mm: Double): String =
        if (useInches) String.format(Locale.US, "%.1f in", mm / 25.4) else "${mm.roundToInt()} mm"

    val language: String get() = Locale.getDefault().language.ifBlank { "en" }
}

/** Checks the GitHub release so people you share the APK with know when there's a newer one. */
object Updates {
    data class Info(val version: String, val apkUrl: String?)

    private fun parts(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }

    private fun newer(a: String, b: String): Boolean {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
            if (d != 0) return d > 0
        }
        return false
    }

    suspend fun check(): Info? = runCatching {
        val json = Http.getJson(BuildConfig.LATEST_API)
        val version = Regex("""v(\d+(?:\.\d+)+)""").find(json.optString("name"))?.groupValues?.get(1)
            ?: return null
        if (!newer(version, BuildConfig.VERSION_NAME)) return null
        val assets = json.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
        }
        Info(version, apk)
    }.getOrNull()
}
