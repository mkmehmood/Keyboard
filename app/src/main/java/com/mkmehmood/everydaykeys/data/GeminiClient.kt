package com.mkmehmood.everydaykeys.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.mkmehmood.everydaykeys.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gemini REST API client for:
 *  1. Auto sentence completion while typing ([autoComplete], using gemini-3.5-flash-lite)
 *  2. Smart sentence completion & rewriting ([smartTransform], using gemini-3.8-flash)
 *  3. AI Text-to-Prompt generation ([textToPrompt], using gemini-3.8-flash)
 *
 * Uses [BuildConfig.GEMINI_API_KEY] injected via the Secrets Gradle Plugin (.env / AI Studio Secrets).
 */
object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_FAST = "gemini-3.5-flash-lite"
    private const val MODEL_SMART = "gemini-3.8-flash"
    private const val MODEL_IMAGE = "gemini-nano-banana-2.1"

    class Failure(message: String) : Exception(message)

    data class ImageResult(val bitmap: Bitmap, val caption: String? = null)

    enum class SmartAction(val label: String) {
        COMPLETE("Complete"),
        POLISH("Polish"),
        EXPAND("Expand"),
        SHORTEN("Shorten")
    }

    private val autoCache = LruCache<String, String>(64)

    fun isConfigured(): Boolean {
        val k = BuildConfig.GEMINI_API_KEY.trim()
        return k.isNotEmpty() && k != "your_gemini_api_key_here"
    }

    private fun requireKey(): String {
        val k = BuildConfig.GEMINI_API_KEY.trim()
        if (k.isEmpty() || k == "your_gemini_api_key_here") {
            throw Failure("Add your GEMINI_API_KEY in the AI Studio Secrets panel.")
        }
        return k
    }

    /**
     * Predicts a concise inline continuation for the current sentence while the user types.
     * Returns null when unconfigured or no confident continuation is produced.
     */
    suspend fun autoComplete(beforeCursor: String, tone: String, lang: String): String? {
        if (!isConfigured()) return null
        val snippet = beforeCursor.takeLast(240)
        if (snippet.trim().length < 10) return null
        val cacheKey = "$tone|$lang|${snippet.takeLast(140)}"
        autoCache.get(cacheKey)?.let { return it }

        val system = "You are a fast mobile keyboard sentence autocompletion engine. " +
            "Given the text typed so far, output ONLY the next 3 to 12 words that naturally complete the current sentence or clause. " +
            "Never repeat words already at the end of the input. Do not add quotation marks, explanations, or markdown. " +
            "Tone: $tone. Language code: $lang."
        val prompt = "Text before cursor:\n$snippet\n\nContinuation:"

        val raw = withTimeout(12_000L) {
            generate(MODEL_FAST, system, prompt, maxTokens = 48, temperature = 0.35)
        }
        val cleaned = cleanContinuation(snippet, raw)
        if (cleaned.isNullOrBlank()) return null
        autoCache.put(cacheKey, cleaned)
        return cleaned
    }

    /**
     * Smart sentence completion or text transformation triggered on demand.
     */
    suspend fun smartTransform(
        text: String,
        action: SmartAction,
        hasSelection: Boolean,
        tone: String,
        lang: String
    ): String {
        requireKey()
        val system = when {
            action == SmartAction.COMPLETE && !hasSelection ->
                "You are an AI writing assistant inside a mobile keyboard. Complete the user's unfinished sentence or thought naturally in a $tone tone (language: $lang). " +
                    "Output ONLY the continuation text to append after the cursor. Do not repeat the input text, do not wrap in quotes, and do not use markdown."
            action == SmartAction.COMPLETE ->
                "Complete and finish the selected text naturally in a $tone tone (language: $lang). Return only the completed plain text without markdown or quotes."
            action == SmartAction.POLISH ->
                "Polish and fix grammar, spelling, and flow while preserving the exact meaning in a $tone tone (language: $lang). Return only the improved plain text."
            action == SmartAction.EXPAND ->
                "Expand these brief notes or partial sentence into a clear, natural, well-written message in a $tone tone (language: $lang). Return only the plain text."
            else ->
                "Shorten and tighten this text so it is crisp and concise while keeping the core meaning (language: $lang). Return only the shortened plain text."
        }
        val raw = withTimeout(35_000L) {
            generate(MODEL_SMART, system, text.takeLast(1500), maxTokens = 280, temperature = 0.55)
        }
        val out = raw.trim()
        return if (action == SmartAction.COMPLETE && !hasSelection) {
            cleanContinuation(text, out) ?: out
        } else {
            out
        }
    }

    /**
     * Generates text from a user-entered AI prompt (optionally grounded in selected/preceding editor text).
     */
    suspend fun textToPrompt(prompt: String, editorContext: String, tone: String, lang: String): String {
        requireKey()
        val cleanPrompt = prompt.trim()
        val cleanCtx = editorContext.trim()
        val system = "You are a helpful AI writing assistant built into a mobile keyboard (tone: $tone, language: $lang). " +
            "Generate clean, ready-to-send plain text that directly fulfills the user's prompt. " +
            "Do not include markdown formatting (no ** or ```), preamble, or meta commentary."
        val userMsg = when {
            cleanPrompt.isNotEmpty() && cleanCtx.isNotEmpty() ->
                "Prompt: $cleanPrompt\n\nContext text:\n${cleanCtx.takeLast(1500)}"
            cleanPrompt.isNotEmpty() -> cleanPrompt
            else -> cleanCtx.takeLast(1500)
        }
        if (userMsg.isBlank()) throw Failure("Type a prompt or some text first.")
        return withTimeout(35_000L) {
            generate(MODEL_SMART, system, userMsg, maxTokens = 360, temperature = 0.65).trim()
        }
    }

    private fun cleanContinuation(before: String, raw: String): String? {
        var c = raw.replace("\r", "").trim()
        if (c.startsWith("\"") && c.endsWith("\"") && c.length > 2) c = c.substring(1, c.length - 1).trim()
        if (c.isBlank()) return null
        // Take first line/sentence chunk for crisp inline auto-completion
        c = c.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (c.isBlank()) return null

        val lastWord = before.takeLastWhile { !it.isWhitespace() }
        if (lastWord.isNotEmpty() && c.startsWith(lastWord, ignoreCase = true) && c.length > lastWord.length) {
            c = c.substring(lastWord.length)
        }
        val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace() &&
            c.firstOrNull()?.let { it !in ".,;:!?)]}…" && !it.isWhitespace() } == true
        return if (needsLeadingSpace) " $c" else c
    }

    private suspend fun generate(
        model: String,
        systemInstruction: String,
        userText: String,
        maxTokens: Int,
        temperature: Double
    ): String = withContext(Dispatchers.IO) {
        val key = requireKey()
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userText)))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", maxTokens)
                put("temperature", temperature)
            })
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-goog-api-key", key)
        }
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                val friendly = when {
                    code == 400 && msg.contains("API key", ignoreCase = true) -> "Invalid Gemini API key. Check AI Studio Secrets."
                    code == 403 -> "Gemini API access denied. Check your GEMINI_API_KEY."
                    code == 429 -> "Gemini rate limit reached. Try again in a moment."
                    msg.isNotBlank() -> msg
                    else -> "Gemini error (HTTP $code)"
                }
                throw Failure(friendly)
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw Failure("Gemini returned an unreadable response.")
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.let { parts ->
                    buildString {
                        for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }
                }.orEmpty()
            if (text.isBlank()) throw Failure("Gemini returned an empty response.")
            text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Gemini network error", e)
            throw Failure("No connection. Check your internet and try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini request failed", e)
            throw Failure(e.localizedMessage ?: "Gemini request failed.")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Generates a new image from a text prompt, or edits [inputBitmap] using [prompt], via gemini-nano-banana-2.1.
     */
    suspend fun generateOrEditImage(
        prompt: String,
        inputBitmap: Bitmap? = null,
        aspectRatio: String = "1:1"
    ): ImageResult {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) throw Failure("Enter a prompt to create or edit an image.")
        val key = requireKey()
        return withTimeout(65_000L) {
            withContext(Dispatchers.IO) {
                val parts = JSONArray()
                if (inputBitmap != null) {
                    val b64 = encodeBitmapJpegBase64(inputBitmap)
                    parts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", b64)
                        })
                    })
                }
                parts.put(JSONObject().put("text", cleanPrompt))

                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", parts)
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("TEXT")
                            put("IMAGE")
                        })
                        put("imageConfig", JSONObject().apply {
                            put("aspectRatio", aspectRatio)
                            put("imageSize", "1K")
                        })
                    })
                }

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL_IMAGE:generateContent")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("X-goog-api-key", key)
                }
                try {
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                    if (code !in 200..299) {
                        val msg = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                        }.getOrDefault("")
                        val friendly = when {
                            code == 400 && msg.contains("API key", ignoreCase = true) -> "Invalid Gemini API key. Check AI Studio Secrets."
                            code == 403 -> "Gemini API access denied. Check your GEMINI_API_KEY."
                            code == 429 -> "Gemini rate limit reached. Please try again shortly."
                            msg.isNotBlank() -> msg
                            else -> "Gemini image error (HTTP $code)"
                        }
                        throw Failure(friendly)
                    }

                    val json = runCatching { JSONObject(body) }.getOrNull()
                        ?: throw Failure("Gemini returned an unreadable image response.")
                    val respParts = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?: throw Failure("No output returned by gemini-nano-banana-2.1.")

                    var outBitmap: Bitmap? = null
                    val caption = StringBuilder()
                    for (i in 0 until respParts.length()) {
                        val part = respParts.optJSONObject(i) ?: continue
                        val t = part.optString("text")
                        if (t.isNotBlank()) {
                            if (caption.isNotEmpty()) caption.append('\n')
                            caption.append(t.trim())
                        }
                        val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                        val b64 = inline?.optString("data").orEmpty()
                        if (b64.isNotBlank() && outBitmap == null) {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            outBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                    val bmp = outBitmap ?: throw Failure(
                        caption.toString().ifBlank { "gemini-nano-banana-2.1 did not return an image for this prompt." }
                    )
                    ImageResult(bmp, caption.toString().takeIf { it.isNotBlank() })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Failure) {
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Gemini image network error", e)
                    throw Failure("No connection. Check your internet and try again.")
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini image generation failed", e)
                    throw Failure(e.localizedMessage ?: "Image generation failed.")
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun encodeBitmapJpegBase64(bitmap: Bitmap): String {
        val maxDim = 1280
        val scale = minOf(1f, maxDim.toFloat() / max(bitmap.width, bitmap.height).coerceAtLeast(1))
        val target = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val baos = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, 88, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
