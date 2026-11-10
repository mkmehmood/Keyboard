package com.mkmehmood.everydaykeys.smart

import android.content.Context
import com.mkmehmood.everydaykeys.data.Bigram
import com.mkmehmood.everydaykeys.data.Db
import com.mkmehmood.everydaykeys.data.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.min

/** Built-in word list. Ordered by rough frequency; position is the rank. Everything runs on-device. */
object Dict {
    var words: List<String> = emptyList()
        private set
    private var rank: HashMap<String, Int> = HashMap()

    fun load(ctx: Context) {
        if (words.isNotEmpty()) return
        words = ctx.assets.open("en_words.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val m = HashMap<String, Int>()
        words.forEachIndexed { i, w -> m.putIfAbsent(w.lowercase(), i) }
        rank = m
    }

    fun has(w: String) = rank.containsKey(w.lowercase())
}

fun isWordChar(c: Char) = c.isLetter() || c == '\''

class SuggestionEngine(ctx: Context) {
    private val db = Db.get(ctx)

    private fun matchCase(typed: String, cand: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> cand.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> cand.replaceFirstChar { it.uppercase() }
        else -> cand
    }

    suspend fun suggest(prefix: String, prev: String?, nextWord: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (prefix.isEmpty()) {
            if (!nextWord) return@withContext emptyList()
            val learned = if (prev != null) db.bigrams().nexts(prev.lowercase(), 3) else emptyList()
            return@withContext (learned + listOf("I", "the", "and", "to")).distinct().take(3)
        }
        val lp = prefix.lowercase()
        val learned = db.words().top(lp, 3).map { it.word }
        val builtin = Dict.words.asSequence().filter { it.startsWith(lp) && it != lp }.take(6).toList()
        (learned + builtin).distinct().filter { it != lp }.take(3).map { matchCase(prefix, it) }
    }

    /** Returns a correction for [word] or null when it looks fine. strength: off, mild, strong. */
    suspend fun correct(word: String, strength: String): String? = withContext(Dispatchers.IO) {
        if (strength == "off" || word.length < 3) return@withContext null
        val lw = word.lowercase()
        if (Dict.has(lw) || db.words().get(lw) != null) return@withContext null
        val maxD = if (strength == "strong") 2 else 1
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for (cand in Dict.words) {
            if (abs(cand.length - lw.length) > maxD) continue
            if (strength == "mild" && cand[0] != lw[0]) continue
            val d = editDistance(lw, cand.lowercase(), maxD)
            if (d <= maxD && d < bestD) { best = cand; bestD = d; if (d == 1) break }
        }
        best?.let { matchCase(word, it) }
    }

    suspend fun learn(word: String, prev: String?) = withContext(Dispatchers.IO) {
        val w = word.lowercase()
        if (w.length < 2) return@withContext
        if (db.words().bump(w) == 0) db.words().upsert(Word(w, 1, false))
        if (prev != null && prev.length > 1) {
            val p = prev.lowercase()
            if (db.bigrams().bump(p, w) == 0) db.bigrams().put(Bigram(p, w, 1))
        }
    }

    suspend fun addPersonal(word: String) = withContext(Dispatchers.IO) {
        val w = word.trim().lowercase()
        if (w.isNotEmpty()) db.words().upsert(Word(w, 1, true))
    }

    private fun editDistance(a: String, b: String, cap: Int): Int {
        if (abs(a.length - b.length) > cap) return cap + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** Matches the letters a finger passed over to dictionary words (Latin layouts). */
    suspend fun decodeGlide(path: String): List<String> = withContext(Dispatchers.Default) {
        if (path.length < 2) return@withContext emptyList()
        val p = path.lowercase()
        val learned = db.words().top(p.take(1), 200).map { it.word }
        val hits = ArrayList<Pair<String, Int>>()
        for ((idx, w) in (learned + Dict.words).withIndex()) {
            val lw = w.lowercase()
            if (lw.length < 2 || lw[0] != p[0] || lw.last() != p.last()) continue
            if (isSubsequence(lw, p)) hits.add(w to (lw.length * 1000 - idx))
        }
        hits.sortedByDescending { it.second }.map { it.first }.distinct().take(3)
    }

    private fun isSubsequence(w: String, path: String): Boolean {
        var i = 0
        for (c in path) if (i < w.length && w[i] == c) i++
        // allow doubled letters (e.g. "hello" has "ll")
        return i == w.length || w.replace(Regex("(.)\\1+"), "$1").let { s ->
            var j = 0
            for (c in path) if (j < s.length && s[j] == c) j++
            j == s.length
        }
    }
}

/** Tiny recursive-descent calculator for "1,250 + 340 =" style typing. */
object Calc {
    fun extract(before: String): String? {
        val allowed = "0123456789.,+-−×÷*/%() "
        var i = before.length
        while (i > 0 && before[i - 1] in allowed) i--
        val tokens = before.substring(i).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (k in tokens.indices) {
            val expr = tokens.drop(k).joinToString(" ")
            if (expr.any { it in "+−×÷*/-%" } && eval(expr) != null) return expr
        }
        return null
    }

    fun eval(expr: String): Double? = try {
        val s = expr.replace(Regex("(?<=\\d),(?=\\d{3})"), "").replace("−", "-").replace("×", "*").replace("÷", "/").replace(" ", "")
        val p = Parser(s)
        val v = p.parseExpr()
        if (p.pos != s.length || v.isNaN() || v.isInfinite()) null else v
    } catch (e: Exception) { null }

    fun format(d: Double): String =
        if (d == Math.floor(d) && abs(d) < 1e15) String.format("%,d", d.toLong())
        else BigDecimal(d).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private class Parser(val s: String) {
        var pos = 0
        fun parseExpr(): Double {
            var v = parseTerm()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = parseTerm()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun parseTerm(): Double {
            var v = parseUnary()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val r = parseUnary()
                v = if (op == '*') v * r else v / r
            }
            return v
        }
        fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            var v = parsePrimary()
            while (pos < s.length && s[pos] == '%') { pos++; v /= 100.0 }
            return v
        }
        fun parsePrimary(): Double {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = parseExpr()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException()
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException()
            return s.substring(start, pos).toDouble()
        }
    }
}

object Conv {
    val categories: Map<String, List<Pair<String, Double>>> = mapOf(
        "Length" to listOf("mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0, "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344),
        "Weight" to listOf("g" to 0.001, "kg" to 1.0, "lb" to 0.45359237, "oz" to 0.028349523),
        "Volume" to listOf("ml" to 0.001, "l" to 1.0, "gal" to 3.785411784, "cup" to 0.2365882365),
        "Temp" to listOf("C" to 1.0, "F" to 1.0, "K" to 1.0),
    )

    fun convert(cat: String, from: String, to: String, v: Double): Double {
        if (cat == "Temp") {
            val c = when (from) { "F" -> (v - 32) * 5 / 9; "K" -> v - 273.15; else -> v }
            return when (to) { "F" -> c * 9 / 5 + 32; "K" -> c + 273.15; else -> c }
        }
        val units = categories[cat] ?: return v
        val f = units.first { it.first == from }.second
        val t = units.first { it.first == to }.second
        return v * f / t
    }
}
