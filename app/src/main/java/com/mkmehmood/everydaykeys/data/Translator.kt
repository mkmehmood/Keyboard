package com.mkmehmood.everydaykeys.data

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
        "en" to "English", "ur" to "Urdu", "ar" to "Arabic", "hi" to "Hindi", "ps" to "Pashto", "es" to "Spanish", "fr" to "French",
        "de" to "German", "pt" to "Portuguese", "ru" to "Russian", "tr" to "Turkish", "it" to "Italian",
        "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "id" to "Indonesian", "fa" to "Persian", "bn" to "Bengali"
    )

    const val MAX_CHARS = 5000

    fun nameOf(code: String) = languages.firstOrNull { it.first == code }?.second ?: code

    private val urduOnly = "ٹڈڑںےھہۂۃ"
    // letters Pashto adds that Urdu does not use (ٹ ڈ ڑ ں are shared with Urdu, so they are not listed)
    private val pashtoOnly = "ډړږښڼڅځګۍېۀ"
    private val englishWords = setOf("the", "and", "is", "to", "of", "a", "in", "that", "it", "for", "you", "i", "are", "this", "with",
        "what", "how", "my", "me", "we", "be", "on", "have", "not", "your", "can", "do", "was", "but", "hello", "hi", "thanks", "please")

    /** Best-effort source language from the writing system; null when it cannot be told (e.g. Latin text that is not English). */
    fun detectSource(text: String): String? {
        var arabic = 0; var deva = 0; var beng = 0; var cjk = 0; var kana = 0; var hangul = 0; var cyr = 0; var latin = 0; var urdu = 0; var pashto = 0
        for (ch in text) when {
            ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' -> { arabic++; if (ch in urduOnly) urdu++; if (ch in pashtoOnly) pashto++ }
            ch in '\u0900'..'\u097F' -> deva++
            ch in '\u0980'..'\u09FF' -> beng++
            ch in '\u4E00'..'\u9FFF' -> cjk++
            ch in '\u3040'..'\u30FF' -> kana++
            ch in '\uAC00'..'\uD7AF' -> hangul++
            ch in '\u0400'..'\u04FF' -> cyr++
            ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F' -> latin++
        }
        val top = maxOf(arabic, deva, beng, cjk, kana, hangul, cyr, latin)
        if (top == 0) return null
        return when (top) {
            arabic -> if (pashto > 0) "ps" else if (urdu > 0) "ur" else "ar"
            deva -> "hi"
            beng -> "bn"
            kana -> "ja"
            cjk -> if (kana > 0) "ja" else "zh"
            hangul -> "ko"
            cyr -> "ru"
            else -> {
                val words = text.lowercase().split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
                if (text.all { it.code < 128 } && words.any { it in englishWords }) "en" else null
            }
        }
    }

    suspend fun translate(text: String, target: String, source: String?, provider: String, googleKey: String, deeplKey: String, email: String): String =
        withContext(Dispatchers.IO) {
            val t = text.take(MAX_CHARS)
            when (provider) {
                "google" -> google(t, target, googleKey)
                "deepl" -> deepl(t, target, deeplKey)
                else -> myMemory(t, target, source, email)
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

    private fun myMemory(text: String, target: String, source: String?, email: String): String {
        val tgt = mm(target); val src = source?.let { mm(it) } ?: "Autodetect"
        return chunks(text).joinToString("") { part ->
            val lead = part.takeWhile { it.isWhitespace() }; val trail = part.takeLastWhile { it.isWhitespace() }
            val q = part.trim()
            if (q.isEmpty()) return@joinToString part
            val url = "https://api.mymemory.translated.net/get?q=" + enc(q) + "&langpair=" + enc("$src|$tgt") +
                (if (email.contains("@")) "&de=" + enc(email.trim()) else "")
            val (code, body) = http(url)
            val j = runCatching { JSONObject(body) }.getOrNull() ?: throw Failure("MyMemory returned an unreadable reply (HTTP $code)")
            val status = j.optInt("responseStatus", code)
            val res = j.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            val warn = res.startsWith("MYMEMORY WARNING", true) || j.optString("responseDetails").startsWith("MYMEMORY WARNING", true)
            val same = res.startsWith("PLEASE SELECT TWO DISTINCT", true) || j.optString("responseDetails").startsWith("PLEASE SELECT TWO DISTINCT", true)
            if (same) throw Failure("The text is already in ${nameOf(target)}")
            if (status != 200 || res.isBlank() || warn)
                throw Failure(if (status == 429 || warn) "MyMemory daily limit reached. Add an email in settings for a higher limit, or use Google/DeepL with your own key."
                else j.optString("responseDetails").ifBlank { "MyMemory error $status" })
            lead + unescape(res) + trail
        }
    }

    private fun mm(code: String) = when (code) { "zh" -> "zh-CN"; else -> code }

    private fun unescape(s: String): String {
        if (!s.contains('&')) return s
        var r = s.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        r = Regex("&#(\\d+);").replace(r) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        return r
    }

    // ---- Google Cloud Translation v2 (official)
    private fun google(text: String, target: String, key: String): String {
        if (key.isBlank()) throw Failure("Add your Google Cloud Translation API key in Settings > Translation")
        val tgt = if (target == "zh") "zh-CN" else target
        val (code, body) = http("https://translation.googleapis.com/language/translate/v2", "POST",
            "q=${enc(text)}&target=$tgt&format=text", mapOf("X-goog-api-key" to key.trim()))
        val j = runCatching { JSONObject(body) }.getOrNull() ?: throw Failure("Google returned an unreadable reply (HTTP $code)")
        if (code != 200) throw Failure(j.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "Google error $code" })
        return unescape(j.getJSONObject("data").getJSONArray("translations").getJSONObject(0).getString("translatedText"))
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
