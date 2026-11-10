package com.mkmehmood.everydaykeys.data

import android.content.Context

/** Tiny on-device dictionary: frequency-ordered English words plus the user's own words. */
object Suggest {
    private val dict = java.util.concurrent.ConcurrentSkipListMap<String, Int>()
    @Volatile private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        ctx.assets.open("words_en.txt").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, l -> val w = l.trim().lowercase(); if (w.isNotEmpty()) dict.putIfAbsent(w, 200_000 - i) }
        }
        loaded = true
    }

    fun learn(word: String) { if (word.isNotBlank()) dict[word.lowercase()] = 1_000_000 }
    fun forget(word: String) { dict.remove(word.lowercase()) }

    private fun matchCase(src: String, w: String) =
        if (src.isNotEmpty() && src[0].isUpperCase()) w.replaceFirstChar { it.uppercase() } else w

    /** Prefix lookup over the sorted dictionary, best [n] by frequency. */
    fun complete(prefix: String, n: Int = 3): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        return dict.subMap(p, p + '\uffff').entries.asSequence().filter { it.key != p }
            .sortedByDescending { it.value }.take(n).map { matchCase(prefix, it.key) }.toList()
    }

    fun matchCaseOf(src: String, w: String) = matchCase(src, w)

    /** Returns a replacement for [word], or null to leave it alone. level 1 = mild, 2 = strong. */
    fun correct(word: String, level: Int): String? {
        if (word.length < 3 || word[0].isUpperCase()) return null
        val w = word.lowercase()
        if (dict.containsKey(w) || !w.all { it in 'a'..'z' || it == '\'' }) return null
        val maxDist = if (level >= 2 && w.length >= 5) 2 else 1
        var best: String? = null; var bestD = Int.MAX_VALUE; var bestScore = -1
        for ((cand, score) in dict) {
            if (kotlin.math.abs(cand.length - w.length) > maxDist || cand[0] != w[0]) continue
            val d = dist(w, cand, maxDist)
            if (d <= maxDist && (d < bestD || (d == bestD && score > bestScore))) { best = cand; bestD = d; bestScore = score }
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
}
