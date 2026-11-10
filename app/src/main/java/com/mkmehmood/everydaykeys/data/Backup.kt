package com.mkmehmood.everydaykeys.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One JSON file holding settings, snippets, personal dictionary and clipboard history. */
object Backup {
    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        val repo = ClipRepo(ctx)
        val root = JSONObject().put("version", 1)
        val s = JSONObject()
        ctx.store.data.first().asMap().forEach { (k, v) -> s.put(k.name, v.toString()) }
        root.put("settings", s)
        val sn = JSONArray()
        db.snippets().all().forEach { sn.put(JSONObject().put("title", it.title).put("body", it.body).put("shortcut", it.shortcut)) }
        root.put("snippets", sn)
        val w = JSONArray()
        db.words().personalList().forEach { w.put(it.word) }
        root.put("words", w)
        val c = JSONArray()
        db.clips().all().forEach { c.put(JSONObject().put("text", repo.text(it)).put("pinned", it.pinned)) }
        root.put("clips", c)
        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString()) }
        Unit
    }

    suspend fun import(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@withContext
        val root = JSONObject(text)
        val db = AppDb.get(ctx)
        val repo = ClipRepo(ctx)
        root.optJSONObject("settings")?.let { s ->
            ctx.store.edit { e -> s.keys().forEach { k -> e[stringPreferencesKey(k)] = s.getString(k) } }
            if (s.has("hideLauncher")) Launcher.apply(ctx, s.getString("hideLauncher") == "true")
        }
        root.optJSONArray("snippets")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                db.snippets().upsert(Snippet(title = it.getString("title"), body = it.getString("body"), shortcut = it.getString("shortcut")))
            }
        }
        root.optJSONArray("words")?.let { a ->
            for (i in 0 until a.length()) { db.words().insert(Word(a.getString(i))); Suggest.learn(a.getString(i)) }
        }
        root.optJSONArray("clips")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { repo.add(it.getString("text"), it.optBoolean("pinned")) }
        }
    }
}
