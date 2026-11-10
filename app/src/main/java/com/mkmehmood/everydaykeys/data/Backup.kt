package com.mkmehmood.everydaykeys.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Backup {
    suspend fun exportAll(ctx: Context, settings: Boolean = true, snippets: Boolean = true, dict: Boolean = true, clips: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val db = Db.get(ctx)
            val root = JSONObject().put("app", "everyday-keys").put("version", 1)
            if (settings) {
                val o = JSONObject()
                ctx.dataStore.data.first().asMap().forEach { (k, v) ->
                    o.put(k.name, JSONObject().put("t", when (v) { is Boolean -> "b"; is Float -> "f"; else -> "s" }).put("v", v))
                }
                root.put("settings", o)
            }
            if (snippets) root.put("snippets", JSONArray().also { a ->
                db.snippets().all().forEach { a.put(JSONObject().put("title", it.title).put("shortcut", it.shortcut).put("body", it.body)) }
            })
            if (dict) root.put("words", JSONArray().also { a ->
                db.words().all().forEach { a.put(JSONObject().put("w", it.word).put("c", it.count).put("p", it.personal)) }
            })
            if (clips) {
                val store = ClipStore(ctx)
                root.put("clips", JSONArray().also { a ->
                    db.clips().all().forEach { c ->
                        a.put(JSONObject().put("text", store.read(c)).put("pinned", c.pinned).put("at", c.createdAt))
                    }
                })
            }
            root.toString()
        }

    suspend fun importAll(ctx: Context, json: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.optString("app") == "everyday-keys") { "Not an Everyday Keys backup" }
        val db = Db.get(ctx)
        root.optJSONObject("settings")?.let { o ->
            ctx.dataStore.edit { e ->
                o.keys().forEach { name ->
                    val item = o.getJSONObject(name)
                    when (item.getString("t")) {
                        "b" -> e[booleanPreferencesKey(name)] = item.getBoolean("v")
                        "f" -> e[floatPreferencesKey(name)] = item.getDouble("v").toFloat()
                        else -> e[stringPreferencesKey(name)] = item.getString("v")
                    }
                }
            }
        }
        root.optJSONArray("snippets")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                db.snippets().upsert(Snippet(title = it.getString("title"), shortcut = it.getString("shortcut"), body = it.getString("body")))
            }
        }
        root.optJSONArray("words")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                db.words().upsert(Word(it.getString("w"), it.getInt("c"), it.getBoolean("p")))
            }
        }
        root.optJSONArray("clips")?.let { a ->
            val store = ClipStore(ctx)
            for (i in 0 until a.length()) store.add(a.getJSONObject(i).getString("text"))
        }
    }
}
