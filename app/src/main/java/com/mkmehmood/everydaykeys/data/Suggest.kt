package com.mkmehmood.everydaykeys.data

import android.content.Context
import java.io.File
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * On-device word engine: frequency-ordered English words, the user's own words, and what the user actually types.
 *
 * Suggestions are ranked by (1) words that usually follow the previous word, (2) how often this user types or picks the word,
 * (3) general frequency, and fall back to typo fixes (swapped letters, one wrong, missing or extra letter) when too few completions exist.
 */
object Suggest {
    private val dict = ConcurrentSkipListMap<String, Int>()
    /** How often the user typed or picked each word. Persisted, so the keyboard keeps adapting. */
    private val used = ConcurrentHashMap<String, Int>()
    /** Words added to the dictionary automatically after the user typed them several times. */
    private val autoLearned = ConcurrentHashMap.newKeySet<String>()
    /** Top results for one and two letter prefixes, which are the most expensive lookups. Cleared whenever scores change. */
    private val cache = ConcurrentHashMap<String, List<String>>()
    @Volatile private var loaded = false
    @Volatile private var usageDirty = false

    private const val USE_BONUS = 20_000
    private const val USE_CAP = 30
    private const val AUTO_LEARN_AFTER = 3
    private const val AUTO_LEARN_SCORE = 150_000

    fun load(ctx: Context) {
        if (loaded) return
        ctx.assets.open("words_en.txt").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, l -> val w = l.trim().lowercase(); if (w.isNotEmpty()) dict.putIfAbsent(w, 200_000 - i) }
        }
        loadUsage(ctx)
        loaded = true
    }

    fun learn(word: String) { if (word.isNotBlank()) { dict[word.lowercase()] = 1_000_000; cache.clear() } }
    fun forget(word: String) {
        val w = word.lowercase()
        dict.remove(w); used.remove(w); autoLearned.remove(w); cache.clear(); usageDirty = true
    }

    private fun matchCase(src: String, w: String) =
        if (src.isNotEmpty() && src[0].isUpperCase()) w.replaceFirstChar { it.uppercase() } else w

    fun matchCaseOf(src: String, w: String) = matchCase(src, w)

    private fun score(w: String, base: Int) = base + minOf(used[w] ?: 0, USE_CAP) * USE_BONUS

    // ---- learning from use ----

    /** Call when the user finishes a word or picks a suggestion. A new word typed a few times joins the dictionary. */
    fun noteTyped(word: String) {
        val w = word.lowercase()
        if (w.length < 3 || w.length > 24 || !w.all { it.isLetter() || it == '\'' }) return
        val n = (used[w] ?: 0) + 1
        used[w] = n
        usageDirty = true
        if (n >= AUTO_LEARN_AFTER && !dict.containsKey(w)) { dict[w] = AUTO_LEARN_SCORE; autoLearned.add(w) }
        if (used.size > 6000) used.entries.removeIf { it.value <= 1 }
        cache.clear()
    }

    private fun usageFile(ctx: Context) = File(ctx.filesDir, "word_usage.txt")

    private fun loadUsage(ctx: Context) {
        val f = usageFile(ctx)
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val p = line.split('\t')
                val c = p.getOrNull(1)?.toIntOrNull() ?: return@forEachLine
                if (p[0].isBlank()) return@forEachLine
                used[p[0]] = c
                if (c >= AUTO_LEARN_AFTER && !dict.containsKey(p[0])) { dict[p[0]] = AUTO_LEARN_SCORE; autoLearned.add(p[0]) }
            }
        }
    }

    fun saveUsage(ctx: Context) {
        if (!usageDirty) return
        usageDirty = false
        runCatching { usageFile(ctx).writeText(used.entries.joinToString("\n") { it.key + "\t" + it.value }) }
    }

    /** Forgets what was learned from typing, including words that were added automatically. */
    fun clearUsage(ctx: Context) {
        autoLearned.forEach { dict.remove(it) }
        autoLearned.clear(); used.clear(); cache.clear(); usageDirty = false
        usageFile(ctx).delete()
    }

    // ---- lookups ----

    /** Prefix lookup over the sorted dictionary, best [n] by score. Keeps only [n] candidates in memory while scanning. */
    fun complete(prefix: String, n: Int = 3): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty() || n <= 0) return emptyList()
        return top(p, n).map { matchCase(prefix, it) }
    }

    private fun top(p: String, k: Int): List<String> {
        val key = "$p#$k"
        if (p.length <= 2) cache[key]?.let { return it }
        val heap = PriorityQueue<Pair<Int, String>>(k + 1, compareBy { it.first })
        for (e in dict.subMap(p, p + '\uffff').entries) {
            if (e.key == p) continue
            heap.add(score(e.key, e.value) to e.key)
            if (heap.size > k) heap.poll()
        }
        val res = heap.sortedByDescending { it.first }.map { it.second }
        if (p.length <= 2) cache[key] = res
        return res
    }

    /**
     * The words to show while typing [prefix]. [ctx] is what usually follows the previous word, best first.
     * With [typo] on, close misspellings fill any spare slots.
     */
    fun suggest(prefix: String, ctx: List<String>, n: Int, typo: Boolean): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty() || n <= 0) return emptyList()
        val out = LinkedHashSet<String>()
        for (w in ctx) { if (out.size >= n) break; if (w.length > p.length && w.startsWith(p)) out.add(w) }
        for (w in top(p, n)) { if (out.size >= n) break; out.add(w) }
        if (typo && p.length >= 2 && out.size < n && !dict.containsKey(p)) {
            for (w in fuzzy(p, n - out.size + 1)) { if (out.size >= n) break; out.add(w) }
        }
        return out.map { matchCase(prefix, it) }
    }

    /** Every word you get by swapping two neighbouring letters, the most common typing slip. */
    private fun swaps(w: String): List<String> {
        if (w.length < 2) return emptyList()
        val r = ArrayList<String>(w.length - 1)
        for (i in 0 until w.length - 1) {
            if (w[i] == w[i + 1]) continue
            val c = w.toCharArray(); val t = c[i]; c[i] = c[i + 1]; c[i + 1] = t
            r.add(String(c))
        }
        return r
    }

    private fun fuzzy(p: String, k: Int): List<String> {
        val res = ArrayList<Pair<Int, String>>()
        for (v in swaps(p)) dict[v]?.let { res.add(score(v, it) + 500_000 to v) }
        val first = p[0].toString()
        for (e in dict.subMap(first, first + '\uffff').entries) {
            val c = e.key
            if (c == p || kotlin.math.abs(c.length - p.length) > 1) continue
            if (dist(p, c, 1) <= 1) res.add(score(c, e.value) to c)
        }
        return res.sortedByDescending { it.first }.map { it.second }.distinct().take(k)
    }

    /** Returns a replacement for [word], or null to leave it alone. level 1 = mild, 2 = strong. */
    fun correct(word: String, level: Int): String? {
        if (word.length < 3 || word[0].isUpperCase()) return null
        val w = word.lowercase()
        if (dict.containsKey(w) || !w.all { it in 'a'..'z' || it == '\'' }) return null
        swaps(w).filter { dict.containsKey(it) }.maxByOrNull { score(it, dict.getValue(it)) }?.let { return it }
        val maxDist = if (level >= 2 && w.length >= 5) 2 else 1
        var best: String? = null; var bestD = Int.MAX_VALUE; var bestScore = -1
        val first = w[0].toString()
        for ((cand, base) in dict.subMap(first, first + '\uffff')) {
            if (kotlin.math.abs(cand.length - w.length) > maxDist) continue
            val d = dist(w, cand, maxDist)
            val s = score(cand, base)
            if (d <= maxDist && (d < bestD || (d == bestD && s > bestScore))) { best = cand; bestD = d; bestScore = s }
        }
        return best
    }

    private fun dist(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }

    // ---- test hooks ----
    internal fun reset() { dict.clear(); used.clear(); autoLearned.clear(); cache.clear(); usageDirty = false }
    internal fun seed(words: List<String>) { words.forEachIndexed { i, w -> dict[w] = 200_000 - i }; cache.clear() }
}
