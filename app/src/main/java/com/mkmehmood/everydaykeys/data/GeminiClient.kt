package com.mkmehmood.everydaykeys.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.mkmehmood.everydaykeys.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gemini REST API client for:
 *  1. Auto sentence completion while typing ([autoComplete], using gemini-3.5-flash-lite)
 *  2. Smart sentence completion & rewriting ([smartTransform], using gemini-3.8-flash with fallback)
 *  3. AI Text-to-Prompt generation ([textToPrompt], using gemini-3.8-flash with fallback)
 *  4. Image creation & editing ([generateOrEditImage], using gemini-nano-banana-2.1 with fallback)
 *
 * Uses [BuildConfig.GEMINI_API_KEY] injected via the Secrets Gradle Plugin (.env / GitHub / AI Studio Secrets).
 */
object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_FAST = "gemini-3.5-flash-lite"
    private const val MODEL_SMART = "gemini-3.8-flash"
    private const val MODEL_IMAGE = "gemini-nano-banana-2.1"
    private const val MODEL_IMAGE_LITE = "gemini-3.1-flash-lite-image"

    open class Failure(message: String) : Exception(message)

    private class RateLimitFailure(
        message: String,
        val retryAfterMs: Long? = null,
        val isHardQuota: Boolean = false,
        val quotaId: String? = null
    ) : Failure(message)

    data class ImageResult(val bitmap: Bitmap, val caption: String? = null)

    enum class QuotaStatus { UNCONFIGURED, READY, IN_FLIGHT, COOLDOWN, QUOTA_EXHAUSTED }

    data class QuotaSnapshot(
        val status: QuotaStatus = QuotaStatus.READY,
        val inFlightCount: Int = 0,
        val activeModel: String? = null,
        val lastModel: String? = null,
        val rpmLast60s: Int = 0,
        val tpmLast60s: Int = 0,
        val requestsToday: Int = 0,
        val imagesToday: Int = 0,
        val tokensToday: Long = 0L,
        val cooldownUntilMs: Long = 0L,
        val lastQuotaId: String? = null,
        val lastError: String? = null,
        val lastSuccessMs: Long = 0L
    )

    private data class RequestEvent(val timestampMs: Long, val tokens: Int)

    enum class SmartAction(val label: String) {
        COMPLETE("Complete"),
        POLISH("Polish"),
        EXPAND("Expand"),
        SHORTEN("Shorten")
    }

    private val autoCache = LruCache<String, String>(128)
    private val retryInRegex = Regex("""retry\s+in\s+([0-9]+(?:\.[0-9]+)?)s""", RegexOption.IGNORE_CASE)
    private val statsLock = Any()
    private val recentEvents = ArrayDeque<RequestEvent>()
    private var currentDay: LocalDate = LocalDate.now()
    private var inFlightCount = 0
    private var activeModel: String? = null
    private var lastModel: String? = null
    private var requestsToday = 0
    private var imagesToday = 0
    private var tokensToday = 0L
    private var cooldownUntilMs = 0L
    private var hardQuotaBlocked = false
    private var lastQuotaId: String? = null
    private var lastError: String? = null
    private var lastSuccessMs = 0L

    private val _quotaFlow = MutableStateFlow(
        QuotaSnapshot(status = if (isConfigured()) QuotaStatus.READY else QuotaStatus.UNCONFIGURED)
    )
    val quotaFlow: StateFlow<QuotaSnapshot> = _quotaFlow.asStateFlow()

    @Volatile
    private var autoCooldownUntilMs: Long = 0L

    @Volatile
    private var lastAutoRequestMs: Long = 0L

    fun liveWindowCounts(nowMs: Long = System.currentTimeMillis()): Pair<Int, Int> = synchronized(statsLock) {
        pruneRecentLocked(nowMs)
        recentEvents.size to recentEvents.sumOf { it.tokens }
    }

    fun clearCooldown() = synchronized(statsLock) {
        autoCooldownUntilMs = 0L
        cooldownUntilMs = 0L
        hardQuotaBlocked = false
        lastError = null
        emitSnapshotLocked(System.currentTimeMillis())
    }

    fun resetUsageStats() = synchronized(statsLock) {
        recentEvents.clear()
        requestsToday = 0
        imagesToday = 0
        tokensToday = 0L
        cooldownUntilMs = 0L
        autoCooldownUntilMs = 0L
        hardQuotaBlocked = false
        lastQuotaId = null
        lastError = null
        emitSnapshotLocked(System.currentTimeMillis())
    }

    private fun pruneRecentLocked(nowMs: Long) {
        val cutoff = nowMs - 60_000L
        while (recentEvents.isNotEmpty() && recentEvents.first().timestampMs < cutoff) {
            recentEvents.removeFirst()
        }
        val today = LocalDate.now()
        if (today != currentDay) {
            currentDay = today
            requestsToday = 0
            imagesToday = 0
            tokensToday = 0L
        }
    }

    private fun emitSnapshotLocked(nowMs: Long) {
        pruneRecentLocked(nowMs)
        val status = when {
            !isConfigured() -> QuotaStatus.UNCONFIGURED
            inFlightCount > 0 -> QuotaStatus.IN_FLIGHT
            hardQuotaBlocked -> QuotaStatus.QUOTA_EXHAUSTED
            cooldownUntilMs > nowMs -> QuotaStatus.COOLDOWN
            else -> QuotaStatus.READY
        }
        _quotaFlow.value = QuotaSnapshot(
            status = status,
            inFlightCount = inFlightCount,
            activeModel = activeModel,
            lastModel = lastModel,
            rpmLast60s = recentEvents.size,
            tpmLast60s = recentEvents.sumOf { it.tokens },
            requestsToday = requestsToday,
            imagesToday = imagesToday,
            tokensToday = tokensToday,
            cooldownUntilMs = cooldownUntilMs,
            lastQuotaId = lastQuotaId,
            lastError = lastError,
            lastSuccessMs = lastSuccessMs
        )
    }

    private fun recordStart(model: String, isImage: Boolean) = synchronized(statsLock) {
        val now = System.currentTimeMillis()
        pruneRecentLocked(now)
        inFlightCount++
        activeModel = model
        lastModel = model
        requestsToday++
        if (isImage) imagesToday++
        recentEvents.addLast(RequestEvent(now, 0))
        emitSnapshotLocked(now)
    }

    private fun recordTokens(tokens: Int) = synchronized(statsLock) {
        val now = System.currentTimeMillis()
        pruneRecentLocked(now)
        if (tokens > 0) {
            tokensToday += tokens
            recentEvents.addLast(RequestEvent(now, tokens))
            // Avoid double-counting the request entry itself: adjust last event if needed
            if (recentEvents.size >= 2) {
                recentEvents.removeLast()
                val prev = recentEvents.removeLast()
                recentEvents.addLast(prev.copy(tokens = prev.tokens + tokens))
            }
        }
        hardQuotaBlocked = false
        cooldownUntilMs = 0L
        lastError = null
        lastQuotaId = null
        lastSuccessMs = now
        emitSnapshotLocked(now)
    }

    private fun recordError(e: Throwable) = synchronized(statsLock) {
        val now = System.currentTimeMillis()
        if (e is RateLimitFailure) {
            lastError = e.message
            lastQuotaId = e.quotaId
            if (e.isHardQuota) {
                hardQuotaBlocked = true
                cooldownUntilMs = 0L
            } else {
                val wait = (e.retryAfterMs ?: 15_000L).coerceIn(3_000L, 120_000L)
                cooldownUntilMs = max(cooldownUntilMs, now + wait)
            }
        } else if (e !is CancellationException) {
            lastError = e.message
        }
        emitSnapshotLocked(now)
    }

    private fun recordEnd() = synchronized(statsLock) {
        inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
        if (inFlightCount == 0) activeModel = null
        emitSnapshotLocked(System.currentTimeMillis())
    }

    fun isConfigured(): Boolean {
        val k = BuildConfig.GEMINI_API_KEY.trim()
        return k.isNotEmpty() && k != "your_gemini_api_key_here"
    }

    private fun requireKey(): String {
        val k = BuildConfig.GEMINI_API_KEY.trim()
        if (k.isEmpty() || k == "your_gemini_api_key_here") {
            throw Failure("GEMINI_API_KEY is not set. Add it in AI Studio Secrets or GitHub Actions Secrets.")
        }
        return k
    }

    /**
     * Predicts a concise inline continuation for the current sentence while the user types.
     * Returns null when unconfigured, throttled, in 429 cooldown, or no confident continuation is produced.
     */
    suspend fun autoComplete(beforeCursor: String, tone: String, lang: String): String? {
        if (!isConfigured()) return null
        val snippet = beforeCursor.takeLast(240)
        if (snippet.trim().length < 10) return null
        val cacheKey = "$tone|$lang|${snippet.takeLast(140)}"
        autoCache.get(cacheKey)?.let { return it }

        val now = System.currentTimeMillis()
        if (now < autoCooldownUntilMs) return null
        if (now - lastAutoRequestMs < 2_500L) return null
        lastAutoRequestMs = now

        val system = "You are a fast mobile keyboard sentence autocompletion engine. " +
            "Given the text typed so far, output ONLY the next 3 to 12 words that naturally complete the current sentence or clause. " +
            "Never repeat words already at the end of the input. Do not add quotation marks, explanations, or markdown. " +
            "Tone: $tone. Language code: $lang."
        val prompt = "Text before cursor:\n$snippet\n\nContinuation:"

        val raw = try {
            withTimeout(12_000L) {
                generateOnce(MODEL_FAST, system, prompt, maxTokens = 48, temperature = 0.35)
            }
        } catch (e: RateLimitFailure) {
            autoCooldownUntilMs = System.currentTimeMillis() + if (e.isHardQuota) 5 * 60_000L else 60_000L
            Log.w(TAG, "Pausing autoComplete after 429 rate limit", e)
            return null
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
        val raw = withTimeout(45_000L) {
            generateWithRetry(MODEL_SMART, MODEL_FAST, system, text.takeLast(1500), maxTokens = 280, temperature = 0.55)
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
        return withTimeout(45_000L) {
            generateWithRetry(MODEL_SMART, MODEL_FAST, system, userMsg, maxTokens = 360, temperature = 0.65).trim()
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

    private suspend fun generateWithRetry(
        primaryModel: String,
        fallbackModel: String?,
        systemInstruction: String,
        userText: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val models = listOfNotNull(primaryModel, fallbackModel?.takeIf { it != primaryModel })
        var lastRateLimit: RateLimitFailure? = null
        for (model in models) {
            var attempt = 0
            while (attempt < 3) {
                try {
                    return generateOnce(model, systemInstruction, userText, maxTokens, temperature)
                } catch (e: RateLimitFailure) {
                    lastRateLimit = e
                    if (e.isHardQuota || attempt >= 2) break
                    val waitMs = (e.retryAfterMs ?: (2_000L * (attempt + 1))).coerceIn(1_000L, 8_000L)
                    delay(waitMs)
                    attempt++
                }
            }
        }
        throw lastRateLimit ?: Failure("Gemini rate limit reached. Try again in a moment.")
    }

    private suspend fun generateOnce(
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
        recordStart(model, isImage = false)
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (code !in 200..299) {
                throw parseHttpError(code, body, conn.getHeaderField("Retry-After"), isImage = false)
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw Failure("Gemini returned an unreadable response.")
            val totalTokens = json.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0
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
            recordTokens(totalTokens)
            text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            recordError(e)
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Gemini network error", e)
            val err = Failure("No connection. Check your internet and try again.")
            recordError(err)
            throw err
        } catch (e: Exception) {
            Log.e(TAG, "Gemini request failed", e)
            val err = Failure(e.localizedMessage ?: "Gemini request failed.")
            recordError(err)
            throw err
        } finally {
            recordEnd()
            conn.disconnect()
        }
    }

    /**
     * Generates a new image from a text prompt, or edits [inputBitmap] using [prompt], via gemini-nano-banana-2.1
     * (with automatic transient-429 backoff and gemini-3.1-flash-lite-image fallback if rate-limited).
     */
    suspend fun generateOrEditImage(
        prompt: String,
        inputBitmap: Bitmap? = null,
        aspectRatio: String = "1:1"
    ): ImageResult {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) throw Failure("Enter a prompt to create or edit an image.")
        requireKey()
        val inputB64 = inputBitmap?.let { withContext(Dispatchers.Default) { encodeBitmapJpegBase64(it) } }
        return withTimeout(75_000L) {
            val models = listOf(MODEL_IMAGE, MODEL_IMAGE_LITE)
            var lastRateLimit: RateLimitFailure? = null
            for (model in models) {
                var attempt = 0
                val maxAttempts = if (model == MODEL_IMAGE) 3 else 2
                while (attempt < maxAttempts) {
                    try {
                        return@withTimeout generateOrEditImageOnce(model, cleanPrompt, inputB64, aspectRatio)
                    } catch (e: RateLimitFailure) {
                        lastRateLimit = e
                        Log.w(TAG, "Image rate limit on $model (attempt ${attempt + 1}): ${e.message}")
                        if (e.isHardQuota || attempt >= maxAttempts - 1) break
                        val waitMs = (e.retryAfterMs ?: (2_500L * (attempt + 1))).coerceIn(1_500L, 8_000L)
                        delay(waitMs)
                        attempt++
                    }
                }
            }
            throw lastRateLimit ?: Failure("Gemini rate limit reached. Please try again shortly.")
        }
    }

    private suspend fun generateOrEditImageOnce(
        model: String,
        cleanPrompt: String,
        inputB64: String?,
        aspectRatio: String
    ): ImageResult = withContext(Dispatchers.IO) {
        val key = requireKey()
        val parts = JSONArray()
        if (inputB64 != null) {
            parts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", inputB64)
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

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-goog-api-key", key)
        }
        recordStart(model, isImage = true)
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            if (code !in 200..299) {
                throw parseHttpError(code, body, conn.getHeaderField("Retry-After"), isImage = true)
            }

            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw Failure("Gemini returned an unreadable image response.")
            val totalTokens = json.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0
            val respParts = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw Failure("No output returned by $model.")

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
                caption.toString().ifBlank { "$model did not return an image for this prompt." }
            )
            recordTokens(totalTokens)
            ImageResult(bmp, caption.toString().takeIf { it.isNotBlank() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            recordError(e)
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Gemini image network error", e)
            val err = Failure("No connection. Check your internet and try again.")
            recordError(err)
            throw err
        } catch (e: Exception) {
            Log.e(TAG, "Gemini image generation failed", e)
            val err = Failure(e.localizedMessage ?: "Image generation failed.")
            recordError(err)
            throw err
        } finally {
            recordEnd()
            conn.disconnect()
        }
    }

    private fun parseHttpError(code: Int, body: String, retryHeader: String?, isImage: Boolean): Failure {
        val errObj = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val msg = errObj?.optString("message").orEmpty()
        if (code == 429) {
            var quotaId: String? = null
            val details = errObj?.optJSONArray("details")
            if (details != null) {
                for (i in 0 until details.length()) {
                    val violations = details.optJSONObject(i)?.optJSONArray("violations") ?: continue
                    val first = violations.optJSONObject(0) ?: continue
                    quotaId = first.optString("quotaId").ifBlank { first.optString("quotaMetric") }
                        .takeIf { it.isNotBlank() }
                    if (quotaId != null) break
                }
            }
            val headerMs = retryHeader?.trim()?.toDoubleOrNull()?.let { (it * 1000).roundToInt().toLong() }
            val bodyMs = retryInRegex.find(msg)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?.let { (it * 1000).roundToInt().toLong() }
            val retryMs = headerMs ?: bodyMs
            val isHardQuota = msg.contains("limit: 0", ignoreCase = true) ||
                msg.contains("free_tier", ignoreCase = true) ||
                msg.contains("billing", ignoreCase = true) ||
                msg.contains("exceeded your current quota", ignoreCase = true)
            val summary = msg.lineSequence().firstOrNull { it.isNotBlank() }?.take(160).orEmpty()
            val friendly = when {
                isImage && isHardQuota ->
                    "Image generation quota unavailable on this API key (${summary.ifBlank { "billing or paid tier required" }}). Enable billing in Google AI Studio."
                isHardQuota ->
                    "Gemini quota exceeded (${summary.ifBlank { "check your plan and billing in Google AI Studio" }})."
                retryMs != null -> {
                    val secs = ((retryMs + 999) / 1000).coerceAtLeast(1)
                    "Gemini rate limit reached. Please retry in ${secs}s."
                }
                summary.isNotBlank() -> "Gemini rate limit reached: $summary"
                else -> "Gemini rate limit reached. Please wait a moment and try again."
            }
            return RateLimitFailure(friendly, retryAfterMs = retryMs, isHardQuota = isHardQuota, quotaId = quotaId)
        }
        val friendly = when {
            code == 400 && msg.contains("API key", ignoreCase = true) -> "Invalid Gemini API key. Check your Secrets configuration."
            code == 403 -> "Gemini API access denied. Check your GEMINI_API_KEY."
            msg.isNotBlank() -> msg
            else -> if (isImage) "Gemini image error (HTTP $code)" else "Gemini error (HTTP $code)"
        }
        return Failure(friendly)
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
