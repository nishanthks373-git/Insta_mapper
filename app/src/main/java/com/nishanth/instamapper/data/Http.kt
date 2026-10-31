package com.nishanth.instamapper.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Uses the phone's normal DNS first. If that can't find a host (some networks, VPNs, ad-blockers or
 * "Private DNS" settings block Instagram), it retries through Cloudflare / Google DNS-over-HTTPS.
 */
object FallbackDns : Dns {
    private val bootstrap by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }

    private val resolvers: List<Dns> by lazy {
        listOf(
            DnsOverHttps.Builder().client(bootstrap)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                .build(),
            DnsOverHttps.Builder().client(bootstrap)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
                .build(),
        )
    }

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            for (r in resolvers) {
                try {
                    val found = r.lookup(hostname)
                    if (found.isNotEmpty()) return found
                } catch (_: Exception) {
                }
            }
            throw e
        }
    }
}

/** True for errors that mean "couldn't reach the server at all", not "the server said no". */
fun Throwable.isNetworkProblem(): Boolean =
    this is UnknownHostException || this is ConnectException || this is SocketTimeoutException ||
        this is SSLException || cause?.isNetworkProblem() == true

class NoConnectionException(message: String) : IOException(message)

const val NO_CONNECTION_HELP =
    "Insta Mapper couldn't reach Instagram — the phone blocked or couldn't look up instagram.com.\n\n" +
        "Please check:\n" +
        "• Settings → Apps → Insta Mapper → Mobile data & Wi‑Fi: allow both\n" +
        "• Turn off any VPN, ad-blocker or Private DNS (Settings → Network → Private DNS → Automatic)\n" +
        "• Then tap Try again. \"Check connection\" shows exactly what's blocked."

object Http {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .dns(FallbackDns)
            .build()
    }

    /** Cookies from the in-app Instagram sign-in (WebView). */
    fun instagramCookies(): String? = try {
        CookieManager.getInstance().getCookie("https://www.instagram.com")
    } catch (_: Throwable) {
        null
    }

    fun cookieValue(name: String): String? = instagramCookies()
        ?.split(";")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }

    data class Result(val code: Int, val body: String, val finalUrl: String)

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Result =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                header("User-Agent", BROWSER_UA)
                header("Accept-Language", "en-US,en;q=0.9")
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(req).execute().use { resp ->
                Result(resp.code, resp.body?.string().orEmpty(), resp.request.url.toString())
            }
        }

    suspend fun postJson(url: String, json: JSONObject, headers: Map<String, String>): Result =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(req).execute().use { resp ->
                Result(resp.code, resp.body?.string().orEmpty(), resp.request.url.toString())
            }
        }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val r = get(url, headers)
        if (r.code !in 200..299) throw IOException("HTTP ${r.code}")
        return JSONObject(r.body)
    }
}

// ---- small JSON helpers -------------------------------------------------------------

fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else opt(key)?.let { if (it is JSONObject || it is JSONArray) null else it.toString() }
        ?.takeIf { it.isNotBlank() }

fun JSONObject.long(key: String): Long? =
    if (isNull(key)) null else (opt(key) as? Number)?.toLong() ?: optString(key).toLongOrNull()

fun JSONObject.double(key: String): Double? =
    if (isNull(key)) null else (opt(key) as? Number)?.toDouble() ?: optString(key).toDoubleOrNull()

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.firstOf(key: String): JSONObject? = optJSONArray(key)?.optJSONObject(0)

object JsonDig {
    /** Depth-first search for the first JSONObject that matches [predicate]. */
    fun findObject(node: Any?, depth: Int = 0, predicate: (JSONObject) -> Boolean): JSONObject? {
        if (node == null || depth > 80) return null
        when (node) {
            is JSONObject -> {
                if (predicate(node)) return node
                val keys = node.keys()
                while (keys.hasNext()) {
                    findObject(node.opt(keys.next()), depth + 1, predicate)?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                findObject(node.opt(i), depth + 1, predicate)?.let { return it }
            }
        }
        return null
    }

    /** Pulls the first JSON object/string out of free text such as an AI reply. */
    fun extractJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }
}
