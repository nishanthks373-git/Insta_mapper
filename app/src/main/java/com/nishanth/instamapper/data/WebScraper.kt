package com.nishanth.instamapper.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Loads a page in an off-screen WebView – the same engine as Chrome, with the app's Instagram
 * sign-in cookies – waits for it to render, then reads its meta tags and embedded JSON.
 * Must be called on the main thread.
 */
object WebScraper {

    private fun js(code: String) = """
        (function(){try{
          var m={};document.querySelectorAll('meta').forEach(function(e){
            var k=e.getAttribute('property')||e.getAttribute('name');if(k)m[k]=e.getAttribute('content');});
          var s=[],total=0;document.querySelectorAll('script[type="application/json"]').forEach(function(x){
            var c=x.textContent||'';if(c.indexOf('$code')>=0&&total+c.length<6000000){s.push(c);total+=c.length;}});
          var h1=document.querySelector('h1');
          return JSON.stringify({url:location.href,title:document.title,meta:m,h1:h1?h1.innerText:null,scripts:s});
        }catch(e){return JSON.stringify({error:String(e)});}})()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun scrape(ctx: Context, url: String, code: String): String? = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        val wv = WebView(ctx.applicationContext)
        var done = false

        fun finish(value: String?) {
            if (done) return
            done = true
            runCatching { wv.stopLoading(); wv.destroy() }
            if (cont.isActive) cont.resume(value)
        }

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        wv.layout(0, 0, 1080, 2200)
        var scheduled = false
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                if (scheduled || done) return
                scheduled = true
                // Give Instagram's scripts time to fill the page.
                main.postDelayed({
                    if (!done) {
                        view.evaluateJavascript(js(code)) { result ->
                            val decoded = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                            finish(decoded)
                        }
                    }
                }, 3500)
            }
        }
        cont.invokeOnCancellation { main.post { finish(null) } }
        wv.loadUrl(url)
    }
}
