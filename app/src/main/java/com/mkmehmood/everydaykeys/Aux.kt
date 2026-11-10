package com.mkmehmood.everydaykeys

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.*
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Local-only word model: bundled common words + words you type. Nothing leaves the device. */
object Dict {
    var words: List<String> = emptyList()
    private var set: HashSet<String> = HashSet()
    val learned = HashMap<String, Int>()

    suspend fun load(ctx: Context) {
        if (words.isNotEmpty()) return
        val (w, l) = withContext(Dispatchers.IO) {
            ctx.resources.openRawResource(R.raw.words_en).bufferedReader().readLines().filter { it.isNotBlank() } to Graph.db(ctx).words().all()
        }
        words = w; set = w.toHashSet(); l.forEach { learned[it.word] = it.count }
    }

    fun reset() = learned.clear()

    fun learn(ctx: Context, scope: CoroutineScope, w: String) {
        val lw = w.lowercase()
        if (lw.length < 2 || lw in set) return
        val n = (learned[lw] ?: 0) + 1
        learned[lw] = n
        scope.launch(Dispatchers.IO) { Graph.db(ctx).words().put(Word(lw, n)) }
    }

    fun suggest(prefix: String): List<String> {
        val p = prefix.lowercase()
        val l = learned.entries.filter { it.key.startsWith(p) && it.key != p }.sortedByDescending { it.value }.map { it.key }
        if (p.isEmpty()) return l.take(3)
        val b = words.filter { it.startsWith(p) && it != p }
        return (l + b).distinct().take(3).map { if (prefix.firstOrNull()?.isUpperCase() == true) it.replaceFirstChar { c -> c.uppercase() } else it }
    }

    fun correct(w: String, level: Int): String? {
        val lw = w.lowercase()
        if (lw.length < 3 || lw in set || learned.containsKey(lw) || !lw.all { it in 'a'..'z' }) return null
        val maxD = if (level >= 2) 2 else 1
        var best: String? = null; var bd = 99
        for (c in words) {
            if (abs(c.length - lw.length) > maxD) continue
            if (level < 2 && c[0] != lw[0]) continue
            val d = lev(lw, c)
            if (d < bd) { bd = d; best = c; if (d == 1) break }
        }
        if (best == null || bd > maxD) return null
        return if (w[0].isUpperCase()) best.replaceFirstChar { it.uppercase() } else best
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}

/** Tiny calculator used by the "=" key on the number pad. */
object Calc {
    fun eval(s: String): String? = try {
        val v = P(s.replace("×", "*").replace("÷", "/").replace(" ", "")).run { val r = expr(); if (i != t.length) throw IllegalStateException(); r }
        if (v.isNaN() || v.isInfinite()) null else BigDecimal(v).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    } catch (e: Exception) { null }

    private class P(val t: String) {
        var i = 0
        fun peek() = if (i < t.length) t[i] else '\u0000'
        fun expr(): Double { var v = term(); while (true) { when (peek()) { '+' -> { i++; v += term() } '-' -> { i++; v -= term() } else -> return v } } }
        fun term(): Double { var v = factor(); while (true) { when (peek()) { '*' -> { i++; v *= factor() } '/' -> { i++; v /= factor() } else -> return v } } }
        fun factor(): Double {
            if (peek() == '-') { i++; return -factor() }
            if (peek() == '(') { i++; val v = expr(); if (peek() == ')') i++; return v }
            val st = i
            while (peek().isDigit() || peek() == '.') i++
            if (st == i) throw IllegalStateException()
            var v = t.substring(st, i).toDouble()
            if (peek() == '%') { i++; v /= 100 }
            return v
        }
    }
}

object ClipLock {
    var until = 0L
    fun open() = SystemClock.elapsedRealtime() < until
}

class VoiceActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { EverydayKeysService.instance?.commitExternal(it) }
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        } catch (e: Exception) { Toast.makeText(this, "Voice typing is not available on this device", Toast.LENGTH_SHORT).show(); finish() }
    }
}

class UnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = if (android.os.Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock clipboard history").setAllowedAuthenticators(auth).apply {
            if (android.os.Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancel")
        }.build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                ClipLock.until = SystemClock.elapsedRealtime() + 60_000
                EverydayKeysService.instance?.refreshView(); finish()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
        }).authenticate(info)
    }
}

/** Streams settings, snippets, dictionary and clipboard to one JSON file and back. */
object Backup {
    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = Graph.db(ctx)
        ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { w ->
            JsonWriter(w).use { j ->
                j.beginObject(); j.name("version").value(1L)
                j.name("snippets").beginArray()
                db.snippets().all().forEach { j.beginObject().name("title").value(it.title).name("shortcut").value(it.shortcut).name("body").value(it.body).endObject() }
                j.endArray()
                j.name("words").beginArray()
                db.words().all().forEach { j.beginObject().name("word").value(it.word).name("count").value(it.count.toLong()).endObject() }
                j.endArray()
                j.name("clips").beginArray()
                db.clips().everything().forEach { c -> j.beginObject().name("text").value(ClipStore.read(c)).name("pinned").value(c.pinned).endObject() }
                j.endArray()
                j.name("prefs").beginObject()
                ctx.ds.data.first().asMap().forEach { (k, v) ->
                    when (v) { is Boolean -> j.name(k.name).value(v); is Int -> j.name(k.name).value(v.toLong()); is String -> j.name(k.name).value(v); else -> {} }
                }
                j.endObject(); j.endObject()
            }
        }
    }

    suspend fun import(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = Graph.db(ctx)
        ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { r ->
            JsonReader(r).use { j ->
                j.beginObject()
                while (j.hasNext()) {
                    when (j.nextName()) {
                        "snippets" -> { j.beginArray(); while (j.hasNext()) { var t = ""; var s = ""; var b = ""
                            j.beginObject(); while (j.hasNext()) when (j.nextName()) { "title" -> t = j.nextString(); "shortcut" -> s = j.nextString(); "body" -> b = j.nextString(); else -> j.skipValue() }; j.endObject()
                            db.snippets().put(Snippet(title = t, shortcut = s, body = b)) }; j.endArray() }
                        "words" -> { j.beginArray(); while (j.hasNext()) { var w = ""; var c = 1
                            j.beginObject(); while (j.hasNext()) when (j.nextName()) { "word" -> w = j.nextString(); "count" -> c = j.nextInt(); else -> j.skipValue() }; j.endObject()
                            if (w.isNotEmpty()) db.words().put(Word(w, c)) }; j.endArray() }
                        "clips" -> { j.beginArray(); while (j.hasNext()) { var t = ""; var p = false
                            j.beginObject(); while (j.hasNext()) when (j.nextName()) { "text" -> t = j.nextString(); "pinned" -> p = j.nextBoolean(); else -> j.skipValue() }; j.endObject()
                            ClipStore.add(ctx, t, p) }; j.endArray() }
                        "prefs" -> { j.beginObject(); while (j.hasNext()) { val n = j.nextName()
                            when (j.peek()) {
                                JsonToken.BOOLEAN -> { val v = j.nextBoolean(); ctx.ds.edit { it[booleanPreferencesKey(n)] = v } }
                                JsonToken.NUMBER -> { val v = j.nextInt(); ctx.ds.edit { it[intPreferencesKey(n)] = v } }
                                JsonToken.STRING -> { val v = j.nextString(); ctx.ds.edit { it[stringPreferencesKey(n)] = v } }
                                else -> j.skipValue() } }; j.endObject() }
                        else -> j.skipValue()
                    }
                }
                j.endObject()
            }
        }
        Dict.reset(); Dict.load(ctx)
    }

    suspend fun wipeAll(ctx: Context) = withContext(Dispatchers.IO) {
        val db = Graph.db(ctx)
        ClipStore.clearAll(ctx, false); db.snippets().wipe(); db.words().wipe(); Dict.reset()
        ctx.ds.edit { it.clear() }
    }
}
