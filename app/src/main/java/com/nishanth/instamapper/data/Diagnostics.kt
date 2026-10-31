package com.nishanth.instamapper.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress

/** "Check connection": works out what exactly is blocked so people can fix it themselves. */
object Diagnostics {

    data class Check(val name: String, val ok: Boolean, val detail: String)

    data class Report(val checks: List<Check>, val advice: String)

    suspend fun run(ctx: Context): Report = coroutineScope {
        val online = isOnline(ctx)
        val vpn = usesVpn(ctx)
        val checks = listOf(
            async { http("Internet (Google)", "https://www.google.com/generate_204") },
            async { dns("Instagram address lookup", "www.instagram.com") },
            async { http("Instagram", "https://www.instagram.com/") },
            async { http("Maps & weather data", "https://archive-api.open-meteo.com/v1/archive?latitude=0&longitude=0&start_date=2024-01-01&end_date=2024-01-01&daily=temperature_2m_max") },
        ).awaitAll()

        val internet = checks[0].ok
        val igLookup = checks[1].ok
        val ig = checks[2].ok
        val advice = when {
            !online -> "Your phone is offline. Turn on Wi‑Fi or mobile data and try again."
            !internet && !ig -> "Insta Mapper can't use the internet at all. Open Settings → Apps → Insta Mapper → " +
                "Mobile data & Wi‑Fi (or \"Network access\") and allow both. Some phones block new apps until you allow this."
            !igLookup && ig -> "Your phone's DNS blocks Instagram, but Insta Mapper worked around it. Reels should load now — try again."
            !ig -> "The internet works but Instagram is blocked on this network" +
                (if (vpn) " or by your VPN" else "") +
                ". Try another Wi‑Fi or mobile data, turn off ad-blockers / VPN, or set Private DNS to Automatic."
            else -> "Everything is reachable. If a reel still fails, Instagram may be limiting requests — sign in to Instagram in Settings and try again."
        }
        Report(checks, advice)
    }

    private suspend fun http(name: String, url: String): Check = withContext(Dispatchers.IO) {
        try {
            val r = Http.get(url)
            Check(name, r.code in 200..399, "HTTP ${r.code}")
        } catch (e: Exception) {
            Check(name, false, e.javaClass.simpleName + (e.message?.let { ": ${it.take(80)}" } ?: ""))
        }
    }

    private suspend fun dns(name: String, host: String): Check = withContext(Dispatchers.IO) {
        try {
            val a = InetAddress.getAllByName(host)
            Check(name, a.isNotEmpty(), a.firstOrNull()?.hostAddress ?: "no address")
        } catch (e: Exception) {
            Check(name, false, "Phone DNS can't find $host")
        }
    }

    private fun isOnline(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
    }.getOrDefault(true)

    private fun usesVpn(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)
}
