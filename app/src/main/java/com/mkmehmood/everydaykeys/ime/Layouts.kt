package com.mkmehmood.everydaykeys.ime

import com.mkmehmood.everydaykeys.R

enum class Act { NONE, SHIFT, DELETE, ENTER, SPACE, NUMPAD, LETTERS, SYMBOLS, SYMBOLS2, LANG, EMOJI, CONVERTER }

data class Key(
    val text: String = "",
    val act: Act = Act.NONE,
    val icon: Int = 0,
    val weight: Float = 1f,
    val style: Int = 0,          // 0 normal, 1 function, 2 accent
    val alts: String = "",
    val spacer: Boolean = false,
    val letter: Boolean = false,
    val longAct: Act = Act.NONE,
)

data class LayoutDef(val id: String, val name: String, val rows: List<String>, val cased: Boolean)

object Layouts {
    val all = listOf(
        LayoutDef("en_qwerty", "English (QWERTY)", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), true),
        LayoutDef("en_qwertz", "English (QWERTZ)", listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"), true),
        LayoutDef("en_azerty", "English (AZERTY)", listOf("azertyuiop", "qsdfghjklm", "wxcvbn"), true),
        LayoutDef("ur", "Urdu", listOf("قوعرتےءیہپ", "اسدفگحجکل", "زشچطبنم"), false),
        LayoutDef("ar", "Arabic", listOf("ضصثقفغعهخح", "شسيبلاتنمك", "ئءؤرىةوزظ"), false),
        LayoutDef("hi", "Hindi", listOf("ौैाीूबहगदज", "ोे्िुपरकतच", "ंमनवलसय"), false),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]

    private val ALTS: Map<Char, String> = mapOf(
        'a' to "àáâäæãåā", 'e' to "èéêëēėę", 'i' to "îïíīįì", 'o' to "ôöòóœøōõ", 'u' to "ûüùúū",
        'c' to "çćč", 'n' to "ñń", 's' to "ßśš", 'y' to "ÿý", 'z' to "žźż", 'l' to "ł", 'd' to "ð",
        '.' to "…,?!:;", ',' to ";:", '?' to "¿", '!' to "¡", '$' to "€£¥₹¢", '-' to "–—•",
        '\'' to "‘’‚", '"' to "“”«»", '=' to "≠≈", '+' to "±", '%' to "‰", '/' to "\\|",
        'ا' to "آأإ", 'ہ' to "ھۃ", 'ت' to "ٹ", 'د' to "ڈ", 'ر' to "ڑ", 'ن' to "ں", 'ی' to "ئ", 'ز' to "ژ", 'ک' to "ؤ",
        'ي' to "ى", 'ه' to "ة", 'ا' to "آأإ",
    )

    private val DIGITS = "1234567890"

    private fun ch(c: Char, cased: Boolean, digit: String = ""): Key =
        Key(text = c.toString(), letter = cased, alts = digit + (ALTS[c] ?: ""))

    fun letters(def: LayoutDef, numberRow: Boolean, langKey: Boolean): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        if (numberRow) rows.add(DIGITS.map { Key(text = it.toString()) })
        val r1 = def.rows[0].mapIndexed { i, c -> ch(c, def.cased, if (!numberRow && i < 10) DIGITS[i].toString() else "") }
        rows.add(r1)
        val r2 = def.rows[1].map { ch(it, def.cased) }
        rows.add(if (r2.size < r1.size) listOf(Key(spacer = true, weight = (r1.size - r2.size) / 2f)) + r2 + listOf(Key(spacer = true, weight = (r1.size - r2.size) / 2f)) else r2)
        rows.add(listOf(Key(act = Act.SHIFT, icon = R.drawable.ic_shift, weight = 1.5f, style = 1)) +
            def.rows[2].map { ch(it, def.cased) } +
            listOf(Key(act = Act.DELETE, icon = R.drawable.ic_backspace, weight = 1.5f, style = 1)))
        val bottom = ArrayList<Key>()
        bottom.add(Key(act = Act.NUMPAD, icon = R.drawable.ic_grid, weight = 1.2f, style = 1, longAct = Act.SYMBOLS))
        if (langKey) bottom.add(Key(act = Act.LANG, icon = R.drawable.ic_globe, style = 1))
        bottom.add(Key(act = Act.EMOJI, icon = R.drawable.ic_emoji, style = 1))
        bottom.add(Key(act = Act.SPACE, icon = R.drawable.ic_space, weight = 4f))
        bottom.add(Key(text = ",", style = 1, alts = ALTS[',']!!))
        bottom.add(Key(text = ".", style = 1, alts = ALTS['.']!!))
        bottom.add(Key(act = Act.ENTER, icon = R.drawable.ic_enter, weight = 1.4f, style = 2))
        rows.add(bottom)
        return rows
    }

    fun numpad(style: String, mathRow: Boolean, converter: Boolean = true): List<List<Key>> {
        val n = { c: String -> Key(text = c) }
        val rows = ArrayList<List<Key>>()
        if (mathRow) {
            val ops = "+−×÷=%/".map { Key(text = it.toString(), style = 1, alts = ALTS[if (it == '−') '-' else it] ?: "") }
            rows.add(if (converter) ops + Key(act = Act.CONVERTER, icon = R.drawable.ic_ruler, style = 1) else ops)
        }
        val top = if (style == "calc") listOf("7", "8", "9", "4", "5", "6", "1", "2", "3") else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        rows.add(listOf(n(top[0]), n(top[1]), n(top[2]), Key(act = Act.DELETE, icon = R.drawable.ic_backspace, style = 1)))
        rows.add(listOf(n(top[3]), n(top[4]), n(top[5]), Key(text = ",", style = 1)))
        rows.add(listOf(n(top[6]), n(top[7]), n(top[8]), Key(act = Act.SPACE, icon = R.drawable.ic_space, style = 1)))
        rows.add(listOf(Key(text = "A", act = Act.LETTERS, style = 1, longAct = Act.SYMBOLS), n("0"), Key(text = ".", style = 1, alts = ALTS['.']!!), Key(act = Act.ENTER, icon = R.drawable.ic_enter, style = 2)))
        return rows
    }

    fun symbols(page: Int): List<List<Key>> {
        val data = if (page == 1) listOf("1234567890", "@#\$_&-+()/", "*\"':;!?")
        else listOf("[]{}<>^%=\\", "$€£¥₹¢₨~|•", "©®™°±×÷")
        val k = { s: String -> s.map { Key(text = it.toString(), alts = ALTS[it] ?: "") } }
        return listOf(
            k(data[0]), k(data[1]),
            listOf(Key(act = Act.SYMBOLS2, icon = R.drawable.ic_swap, weight = 1.5f, style = 1)) + k(data[2]) +
                listOf(Key(act = Act.DELETE, icon = R.drawable.ic_backspace, weight = 1.5f, style = 1)),
            listOf(
                Key(text = "A", act = Act.LETTERS, weight = 1.3f, style = 1),
                Key(act = Act.NUMPAD, icon = R.drawable.ic_grid, style = 1),
                Key(act = Act.SPACE, icon = R.drawable.ic_space, weight = 4f),
                Key(text = ",", style = 1), Key(text = ".", style = 1, alts = ALTS['.']!!),
                Key(act = Act.ENTER, icon = R.drawable.ic_enter, weight = 1.4f, style = 2),
            ),
        )
    }
}

class Tool(val id: String, val icon: Int, val name: String)

object Tools {
    val all = listOf(
        Tool("clipboard", R.drawable.ic_clipboard, "Clipboard"),
        Tool("snippets", R.drawable.ic_doc, "Text snippets"),
        Tool("voice", R.drawable.ic_mic, "Voice typing"),
        Tool("translate", R.drawable.ic_translate, "Translate"),
        Tool("undo", R.drawable.ic_undo, "Undo"),
        Tool("redo", R.drawable.ic_redo, "Redo"),
        Tool("cursor", R.drawable.ic_code, "Cursor and edit keys"),
        Tool("search", R.drawable.ic_search, "Quick search"),
        Tool("emoji", R.drawable.ic_emoji, "Emoji"),
        Tool("settings", R.drawable.ic_settings, "Settings"),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
