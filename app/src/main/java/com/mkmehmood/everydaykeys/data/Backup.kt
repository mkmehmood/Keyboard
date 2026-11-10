package com.mkmehmood.everydaykeys.data

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One JSON file holding settings, snippets, personal dictionary and clipboard history.
 * Clips are streamed in both directions, so a very large clip never has to fit in memory together with the rest.
 */
object Backup {
    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        val settings = JSONObject()
        ctx.store.data.first().asMap().forEach { (k, v) -> if (!Secrets.isSecretKey(k.name)) settings.put(k.name, v.toString()) }
        val snippets = JSONArray()
        db.snippets().all().forEach { snippets.put(JSONObject().put("title", it.title).put("body", it.body).put("shortcut", it.shortcut)) }
        val words = JSONArray()
        db.words().personalList().forEach { words.put(it.word) }
        val out = ctx.contentResolver.openOutputStream(uri) ?: return@withContext
        out.bufferedWriter().use { w ->
            w.write("{\"version\":1,\"settings\":"); w.write(settings.toString())
            w.write(",\"snippets\":"); w.write(snippets.toString())
            w.write(",\"words\":"); w.write(words.toString())
            w.write(",\"clips\":[")
            db.clips().all().forEachIndexed { i, c ->
                if (i > 0) w.write(",")
                w.write("{\"pinned\":${c.pinned},\"text\":\"")
                c.open().use { r ->
                    val buf = CharArray(32_768)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        w.write(escape(buf, n))
                    }
                }
                w.write("\"}")
            }
            w.write("]}")
        }
    }

    private fun escape(buf: CharArray, n: Int): String {
        val sb = StringBuilder(n + 16)
        for (i in 0 until n) {
            val ch = buf[i]
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch < ' ' -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun readStrings(r: JsonReader): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        r.beginObject()
        while (r.hasNext()) {
            val k = r.nextName()
            if (r.peek() == JsonToken.NULL) r.skipValue() else m[k] = r.nextString()
        }
        r.endObject()
        return m
    }

    suspend fun import(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        val repo = ClipRepo(ctx)
        val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext
        JsonReader(input.bufferedReader()).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "settings" -> {
                        val s = readStrings(r).filterKeys { !Secrets.isSecretKey(it) }
                        ctx.store.edit { e -> s.forEach { (k, v) -> e[stringPreferencesKey(k)] = v } }
                        s["hideLauncher"]?.let { Launcher.request(ctx, it == "true") }
                    }
                    "snippets" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            val o = readStrings(r)
                            db.snippets().upsert(Snippet(title = o["title"].orEmpty(), body = o["body"].orEmpty(), shortcut = o["shortcut"].orEmpty()))
                        }
                        r.endArray()
                    }
                    "words" -> {
                        r.beginArray()
                        while (r.hasNext()) { val w = r.nextString(); db.words().insert(Word(w)); Suggest.learn(w) }
                        r.endArray()
                    }
                    "clips" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var text = ""
                            var pinned = false
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "text" -> text = r.nextString()
                                    "pinned" -> pinned = r.nextBoolean()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            repo.add(text, pinned)
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
    }
}
