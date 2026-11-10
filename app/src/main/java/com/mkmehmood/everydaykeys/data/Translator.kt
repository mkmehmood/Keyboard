package com.mkmehmood.everydaykeys.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Translation through documented provider APIs. The text is sent only when the user taps Translate.
 *  - mymemory: MyMemory (translated.net), free and keyless, with a daily quota (an email raises it)
 *  - google:   Google Cloud Translation v2, needs the user's own API key (free monthly tier)
 *  - deepl:    DeepL API Free, needs the user's own key (free monthly tier)
 */
object Translator {
    class Failure(message: String) : Exception(message)

    val languages = listOf(
        "en" to "English", "ur" to "Urdu", "ar" to "Arabic", "hi" to "Hindi", "es" to "Spanish", "fr" to "French",
        "de" to "German", "pt" to "Portuguese", "ru" to "Russian", "tr" to "Turkish", "it" to "Italian",
        "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "id" to "Indonesian", "fa" to "Persian", "bn" to "Bengali"
    )

    const val MAX_CHARS = 5000

    suspend fun translate(text: String, target: String, provider: String, googleKey: String, deeplKey: String, email: String): String =
        withContext(Dispatchers.IO) {
            val t = text.take(MAX_CHARS)
            when (provider) {
                "google" -> google(t, target, googleKey)
                "deepl" -> deepl(t, target, deeplKey)
                else -> myMemory(t, target, email)
            }
        }

    // ---- MyMemory (500 byte limit per request, so split on sentence / word boundaries)
    private fun chunks(text: String, max: Int = 450): List<String> {
        val out = ArrayList<String>(); var cur = StringBuilder()
        for (piece in Regex("(?<=[.!?۔؟\\n])").split(text)) {
            if (cur.length + piece.length > max && cur.isNotEmpty()) { out.add(cur.toString()); cur = StringBuilder() }
            var p = piece
            while (p.length > max) {
                val cut = p.lastIndexOf(' ', max).let { if (it <= 0) max else it }
                out.add(p.substring(0, cut)); p = p.substring(cut)
            }
            cur.append(p)
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    private fun myMemory(text: String, target: String, email: String): String {
        val tgt = if (target == "zh") "zh-CN" else target
        return chunks(text).joinToString("") { part ->
            val lead = part.takeWhile { it.isWhitespace() }; val trail = part.takeLastWhile { it.isWhitespace() }
            val q = part.trim()
            if (q.isEmpty()) return@joinToString part
            val url = "https://api.mymemory.translated.net/get?q=" + enc(q) + "&langpair=" + enc("Autodetect|$tgt") +
                (if (email.contains("@")) "&de=" + enc(email.trim()) else "")
            val (code, body) = http(url)
            val j = runCatching { JSONObject(body) }.getOrNull() ?: throw Failure("MyMemory returned an unreadable reply (HTTP $code)")
            val status = j.optInt("responseStatus", code)
            val res = j.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            if (status != 200 || res.isBlank() || res.startsWith("MYMEMORY WARNING", true))
                throw Failure(if (status == 429 || res.startsWith("MYMEMORY WARNING", true)) "MyMemory daily limit reached. Add an email in settings for a higher limit, or use Google/DeepL with your own key."
                else j.optString("responseDetails").ifBlank { "MyMemory error $status" })
            lead + Html.fromHtml(res, Html.FROM_HTML_MODE_LEGACY).toString() + trail
        }
    }

    // ---- Google Cloud Translation v2 (official)
    private fun google(text: String, target: String, key: String): String {
        if (key.isBlank()) throw Failure("Add your Google Cloud Translation API key in Settings > Translation")
        val tgt = if (target == "zh") "zh-CN" else target
        val (code, body) = http("https://translation.googleapis.com/language/translate/v2", "POST",
            "q=${enc(text)}&target=$tgt&format=text", mapOf("X-goog-api-key" to key.trim()))
        val j = runCatching { JSONObject(body) }.getOrNull() ?: throw Failure("Google returned an unreadable reply (HTTP $code)")
        if (code != 200) throw Failure(j.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "Google error $code" })
        return Html.fromHtml(j.getJSONObject("data").getJSONArray("translations").getJSONObject(0).getString("translatedText"), Html.FROM_HTML_MODE_LEGACY).toString()
    }

    // ---- DeepL API Free (official)
    private val deeplTargets = mapOf("en" to "EN-US", "pt" to "PT-BR", "zh" to "ZH", "ar" to "AR", "es" to "ES", "fr" to "FR", "de" to "DE",
        "ru" to "RU", "tr" to "TR", "it" to "IT", "ja" to "JA", "ko" to "KO", "id" to "ID")

    private fun deepl(text: String, target: String, key: String): String {
        if (key.isBlank()) throw Failure("Add your DeepL API Free key in Settings > Translation")
        val tgt = deeplTargets[target] ?: throw Failure("DeepL does not support this target language. Choose Google or MyMemory.")
        val (code, body) = http("https://api-free.deepl.com/v2/translate", "POST", "text=${enc(text)}&target_lang=$tgt",
            mapOf("Authorization" to "DeepL-Auth-Key ${key.trim()}"))
        val j = runCatching { JSONObject(body) }.getOrNull() ?: throw Failure("DeepL returned an unreadable reply (HTTP $code)")
        if (code != 200) throw Failure(when (code) { 403 -> "DeepL rejected the key"; 456 -> "DeepL monthly quota used up"; else -> j.optString("message").ifBlank { "DeepL error $code" } })
        return j.getJSONArray("translations").getJSONObject(0).getString("text")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun http(url: String, method: String = "GET", form: String? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 8000; c.readTimeout = 15000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (form != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            return code to body
        } catch (e: java.io.IOException) {
            throw Failure("No connection. Check your internet and try again.")
        } finally { c.disconnect() }
    }
}
