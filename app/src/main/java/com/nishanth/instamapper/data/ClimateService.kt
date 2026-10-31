package com.nishanth.instamapper.data

import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class MonthClimate(
    val month: Int, // 1..12
    val avgHigh: Double,
    val avgLow: Double,
    val rainMm: Double,
    val rainyDays: Double,
    val score: Int,
) {
    val name: String get() = MONTHS[month - 1]
}

data class SeasonReport(
    val place: Place,
    val months: List<MonthClimate>,
    val best: List<Int>,
    val avoid: List<Int>,
    val summary: String,
    val sunrise: String?,
    val sunset: String?,
    val aiAdvice: String?,
    val aiError: String?,
)

val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Uses 5 years of real weather history from Open-Meteo (free, no key) to rank the months. */
object ClimateService {

    suspend fun report(place: Place, settings: AppSettings): SeasonReport {
        val months = monthlyClimate(place)
        if (months.any { it.avgHigh.isNaN() || it.avgLow.isNaN() }) {
            throw java.io.IOException("No weather history is available for this location.")
        }
        val ranked = months.sortedByDescending { it.score }
        val top = ranked.first().score
        val best = ranked.filter { it.score >= top - 6 }.take(4).map { it.month }.sorted()
            .ifEmpty { ranked.take(3).map { it.month }.sorted() }
        val avoid = ranked.reversed().filter { it.score < 50 && it.month !in best }.take(3).map { it.month }.sorted()

        val (sunrise, sunset) = runCatching { sunTimes(place) }.getOrDefault(null to null)

        var ai: String? = null
        var aiError: String? = null
        if (settings.aiEnabled) {
            runCatching { aiAdvice(place, months, best, settings) }
                .onSuccess { ai = it }
                .onFailure { aiError = it.message }
        }

        return SeasonReport(
            place = place,
            months = months,
            best = best,
            avoid = avoid,
            summary = summarize(months, best, avoid),
            sunrise = sunrise,
            sunset = sunset,
            aiAdvice = ai,
            aiError = aiError,
        )
    }

    private suspend fun monthlyClimate(place: Place): List<MonthClimate> {
        val endYear = Calendar.getInstance().get(Calendar.YEAR) - 1
        val startYear = endYear - 4
        val years = (endYear - startYear + 1).toDouble()
        val url = "https://archive-api.open-meteo.com/v1/archive" +
            "?latitude=${fmt(place.lat)}&longitude=${fmt(place.lng)}" +
            "&start_date=$startYear-01-01&end_date=$endYear-12-31" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto"
        val daily = Http.getJson(url).getJSONObject("daily")
        val time = daily.getJSONArray("time")
        val tMax = daily.getJSONArray("temperature_2m_max")
        val tMin = daily.getJSONArray("temperature_2m_min")
        val rain = daily.getJSONArray("precipitation_sum")

        val hiSum = DoubleArray(12); val hiN = IntArray(12)
        val loSum = DoubleArray(12); val loN = IntArray(12)
        val rainSum = DoubleArray(12); val rainyDays = IntArray(12)
        for (i in 0 until time.length()) {
            val m = time.getString(i).substring(5, 7).toInt() - 1
            num(tMax, i)?.let { hiSum[m] += it; hiN[m]++ }
            num(tMin, i)?.let { loSum[m] += it; loN[m]++ }
            num(rain, i)?.let { rainSum[m] += it; if (it >= 1.0) rainyDays[m]++ }
        }
        return (0 until 12).map { m ->
            val hi = if (hiN[m] > 0) hiSum[m] / hiN[m] else Double.NaN
            val lo = if (loN[m] > 0) loSum[m] / loN[m] else Double.NaN
            val rd = rainyDays[m] / years
            MonthClimate(m + 1, hi, lo, rainSum[m] / years, rd, score(hi, rd))
        }
    }

    /** 100 = ideal sightseeing weather: highs of 20–28 °C and few rainy days. */
    private fun score(high: Double, rainyDays: Double): Int {
        if (high.isNaN()) return 0
        val tempPenalty = when {
            high < 20 -> (20 - high) * 3.5
            high > 28 -> (high - 28) * 5.0
            else -> 0.0
        }
        val rainPenalty = rainyDays * 2.4
        return (100 - tempPenalty - rainPenalty).coerceIn(0.0, 100.0).roundToInt()
    }

    private fun summarize(months: List<MonthClimate>, best: List<Int>, avoid: List<Int>): String {
        val b = best.map { months[it - 1] }
        val sb = StringBuilder()
        sb.append("Best months: ").append(b.joinToString(", ") { it.name }).append(". ")
        sb.append("Expect highs around ${Locales.temp(b.minOf { it.avgHigh })}–${Locales.temp(b.maxOf { it.avgHigh })}")
        sb.append(" and about ${b.map { it.rainyDays }.average().roundToInt()} rainy days a month.")
        if (avoid.isNotEmpty()) {
            val reasons = avoid.map { months[it - 1] }.joinToString(", ") { m ->
                val why = when {
                    m.rainyDays >= 12 -> "very wet"
                    m.avgHigh >= 34 -> "very hot"
                    m.avgHigh <= 8 -> "very cold"
                    m.rainyDays >= 8 -> "rainy"
                    m.avgHigh >= 30 -> "hot"
                    else -> "chilly"
                }
                "${m.name} ($why)"
            }
            sb.append(" Avoid if you can: $reasons.")
        }
        return sb.toString()
    }

    private suspend fun sunTimes(place: Place): Pair<String?, String?> {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${fmt(place.lat)}&longitude=${fmt(place.lng)}" +
            "&daily=sunrise,sunset&timezone=auto&forecast_days=1"
        val daily = Http.getJson(url).getJSONObject("daily")
        val rise = daily.getJSONArray("sunrise").optString(0).substringAfter("T", "").ifBlank { null }
        val set = daily.getJSONArray("sunset").optString(0).substringAfter("T", "").ifBlank { null }
        return rise to set
    }

    private suspend fun aiAdvice(place: Place, months: List<MonthClimate>, best: List<Int>, s: AppSettings): String {
        val table = months.joinToString("\n") {
            "${it.name}: high ${Locales.temp(it.avgHigh)}, low ${Locales.temp(it.avgLow)}, ${it.rainyDays.roundToInt()} rainy days"
        }
        val prompt = """
            I want to visit ${place.name}${place.address?.let { " ($it)" } ?: ""}.
            Weather history (5-year averages):
            $table
            Weather-based best months: ${best.joinToString { MONTHS[it - 1] }}.

            Reply in the language with code "${Locales.language}". In under 120 words of plain text (no markdown headers), tell me: the best months and why,
            months to avoid, the best time of day to go (light for photos, crowds), and any
            festivals, closures or seasonal highlights worth knowing. Be specific to this place.
        """.trimIndent()
        return AiClient.ask(s, prompt, 500).replace("**", "")
    }

    private fun num(arr: JSONArray, i: Int): Double? = if (arr.isNull(i)) null else arr.optDouble(i).takeUnless { it.isNaN() }

    private fun fmt(d: Double) = String.format(Locale.US, "%.4f", d)

    /** "06:12" -> "07:12" */
    fun shift(time: String?, minutes: Int): String? {
        if (time == null) return null
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val total = max(0, h * 60 + m + minutes) % (24 * 60)
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }
}
