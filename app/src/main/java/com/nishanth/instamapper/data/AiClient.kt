package com.nishanth.instamapper.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Optional AI helper (Gemini or Claude) using the API key you enter in Settings. */
object AiClient {

    suspend fun ask(settings: AppSettings, prompt: String, maxTokens: Int = 600): String {
        require(settings.aiEnabled) { "AI is not configured" }
        return when (settings.provider) {
            AiProvider.GEMINI -> gemini(settings, prompt, maxTokens)
            AiProvider.CLAUDE -> claude(settings, prompt, maxTokens)
            AiProvider.NONE -> throw IllegalStateException("AI is not configured")
        }
    }

    private suspend fun gemini(s: AppSettings, prompt: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.3))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${Uri.encode(s.effectiveModel)}:generateContent"
        val r = Http.postJson(url, body, mapOf("x-goog-api-key" to s.apiKey.trim()))
        if (r.code !in 200..299) throw IOException("Gemini error ${r.code}: ${errorMessage(r.body)}")
        val parts = JSONObject(r.body).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: throw IOException("Empty Gemini reply")
        return buildString {
            for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }.trim()
    }

    private suspend fun claude(s: AppSettings, prompt: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", s.effectiveModel)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val r = Http.postJson(
            "https://api.anthropic.com/v1/messages", body,
            mapOf("x-api-key" to s.apiKey.trim(), "anthropic-version" to "2023-06-01"),
        )
        if (r.code !in 200..299) throw IOException("Claude error ${r.code}: ${errorMessage(r.body)}")
        val content = JSONObject(r.body).optJSONArray("content") ?: throw IOException("Empty Claude reply")
        return buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.trim()
    }

    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(200)
}
