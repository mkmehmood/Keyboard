package com.mkmehmood.everydaykeys.data

import android.content.Context
import java.io.File

/** Word-pair counts used for next-word suggestions. Pure logic, so it can be unit tested. */
class BigramModel(private val maxPairs: Int = 6000) {
    private val m = LinkedHashMap<String, MutableMap<String, Int>>()
    private var pairs = 0

    @Synchronized
    fun learn(prev: String, next: String) {
        val p = prev.lowercase()
        val n = next.lowercase()
        if (p.isBlank() || n.isBlank()) return
        // Re-insert so the most recently used previous word sits at the end and is trimmed last.
        val inner = m.remove(p) ?: mutableMapOf()
        if (!inner.containsKey(n)) pairs++
        inner[n] = (inner[n] ?: 0) + 1
        m[p] = inner
        if (pairs > maxPairs) trim()
    }

    private fun trim() {
        val it = m.entries.iterator()
        while (pairs > maxPairs * 9 / 10 && it.hasNext()) {
            val e = it.next()
            pairs -= e.value.size
            it.remove()
        }
    }

    @Synchronized
    fun next(prev: String, n: Int = 3): List<String> =
        m[prev.lowercase()]?.entries?.sortedByDescending { it.value }?.take(n)?.map { it.key } ?: emptyList()

    @Synchronized
    fun clear() { m.clear(); pairs = 0 }

    @Synchronized
    fun serialize(): String = buildString {
        m.forEach { (p, inner) -> inner.forEach { (n, c) -> append(p).append('\t').append(n).append('\t').append(c).append('\n') } }
    }

    @Synchronized
    fun load(text: String) {
        clear()
        text.lineSequence().forEach { line ->
            val f = line.split('\t')
            if (f.size == 3) {
                val c = f[2].toIntOrNull() ?: return@forEach
                m.getOrPut(f[0]) { mutableMapOf() }[f[1]] = c
                pairs++
            }
        }
    }
}

/** Learns which word tends to follow which, only from what is typed in normal text fields, and only on this phone. */
object Bigrams {
    private val model = BigramModel()
    @Volatile private var loaded = false
    @Volatile private var dirty = false

    private fun file(ctx: Context) = File(ctx.filesDir, "bigrams.txt")

    fun load(ctx: Context) {
        if (loaded) return
        val f = file(ctx)
        if (f.exists()) runCatching { model.load(f.readText()) }
        loaded = true
    }

    fun learn(prev: String, next: String) { model.learn(prev, next); dirty = true }
    fun next(prev: String, n: Int = 3): List<String> = model.next(prev, n)

    fun save(ctx: Context) {
        Suggest.saveUsage(ctx)
        if (!dirty) return
        dirty = false
        runCatching { file(ctx).writeText(model.serialize()) }
    }

    fun clear(ctx: Context) {
        Suggest.clearUsage(ctx)
        model.clear()
        dirty = false
        file(ctx).delete()
    }
}
