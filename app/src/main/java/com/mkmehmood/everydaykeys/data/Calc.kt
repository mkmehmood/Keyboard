package com.mkmehmood.everydaykeys.data

import kotlin.math.abs
import kotlin.math.floor

/** Minimal local expression evaluator for the number pad "=" key. */
object Calc {
    fun eval(expr: String): Double? = try {
        val p = Parser(expr.replace("×", "*").replace("÷", "/").replace("−", "-").replace(",", "").replace(" ", ""))
        val v = p.expr()
        if (p.i == p.s.length && !v.isNaN() && !v.isInfinite()) v else null
    } catch (e: Exception) { null }

    fun format(r: Double): String =
        if (r == floor(r) && abs(r) < 1e15) r.toLong().toString()
        else "%.8f".format(r).trimEnd('0').trimEnd('.')

    private class Parser(val s: String) {
        var i = 0
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val t = term(); v = if (op == '+') v + t else v - t }
            return v
        }
        fun term(): Double {
            var v = unary()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) { val op = s[i++]; val t = unary(); v = if (op == '*') v * t else v / t }
            return v
        }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            var v = atom()
            while (i < s.length && s[i] == '%') { i++; v /= 100 }
            return v
        }
        fun atom(): Double {
            if (i < s.length && s[i] == '(') { i++; val v = expr(); if (i < s.length && s[i] == ')') i++; return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }
}
