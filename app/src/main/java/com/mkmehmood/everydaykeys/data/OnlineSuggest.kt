package com.mkmehmood.everydaykeys.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional live suggestions from the free Datamuse word API (no key needed).
 * Only the partial word being typed is sent, never surrounding text. Off by default.
 */
object OnlineSuggest {
    private val cache = LruCache<String, List<String>>(300)

    suspend fun fetch(prefix: String): List<String> {
        val key = prefix.lowercase()
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.datamuse.com/sug?max=6&s=" + URLEncoder.encode(key, "UTF-8"))
                val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 2000; readTimeout = 2000 }
                try {
                    if (c.responseCode != 200) return@withContext emptyList()
                    val arr = JSONArray(c.inputStream.bufferedReader().readText())
                    val words = (0 until arr.length()).map { arr.getJSONObject(it).getString("word") }
                        .filter { it.length > key.length && it.all { ch -> ch.isLetter() || ch == '\'' } }
                    cache.put(key, words); words
                } finally { c.disconnect() }
            } catch (e: Exception) { emptyList() }
        }
    }
}
