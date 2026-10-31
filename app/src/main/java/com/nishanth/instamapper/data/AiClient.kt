package com.nishanth.instamapper.data

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Optional AI helper (Gemini or Claude) using the API key entered in Settings. */
object AiClient {

    /** Media to show the model alongside the prompt. */
    class Media(val images: List<ByteArray> = emptyList(), val video: ByteArray? = null)

    suspend fun ask(
        settings: AppSettings,
        prompt: String,
        maxTokens: Int = 600,
        media: Media? = null,
        json: Boolean = false,
    ): String {
        require(settings.aiEnabled) { "AI is not configured" }
        return when (settings.provider) {
            AiProvider.GEMINI -> gemini(settings, prompt, maxTokens, media, json)
            AiProvider.CLAUDE -> claude(settings, prompt, maxTokens, media)
            AiProvider.NONE -> throw IllegalStateException("AI is not configured")
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private suspend fun gemini(s: AppSettings, prompt: String, maxTokens: Int, media: Media?, json: Boolean): String {
        val parts = JSONArray()
        media?.video?.let {
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "video/mp4").put("data", b64(it))))
        }
        media?.images?.forEach {
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64(it))))
        }
        parts.put(JSONObject().put("text", prompt))
        val config = JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.2)
        if (json) config.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", config)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${Uri.encode(s.effectiveModel)}:generateContent"
        val r = Http.postJson(url, body, mapOf("x-goog-api-key" to s.apiKey.trim()), slow = media != null)
        if (r.code !in 200..299) throw IOException("Gemini error ${r.code}: ${errorMessage(r.body)}")
        val out = JSONObject(r.body).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: throw IOException("Empty Gemini reply")
        return buildString {
            for (i in 0 until out.length()) {
                val p = out.optJSONObject(i) ?: continue
                if (!p.optBoolean("thought", false)) append(p.optString("text"))
            }
        }.trim()
    }

    private suspend fun claude(s: AppSettings, prompt: String, maxTokens: Int, media: Media?): String {
        val content = JSONArray()
        media?.images?.take(20)?.forEach {
            content.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64(it)),
                )
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", s.effectiveModel)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val r = Http.postJson(
            "https://api.anthropic.com/v1/messages", body,
            mapOf("x-api-key" to s.apiKey.trim(), "anthropic-version" to "2023-06-01"),
            slow = media != null,
        )
        if (r.code !in 200..299) throw IOException("Claude error ${r.code}: ${errorMessage(r.body)}")
        val blocks = JSONObject(r.body).optJSONArray("content") ?: throw IOException("Empty Claude reply")
        return buildString {
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.trim()
    }

    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(200)
}
