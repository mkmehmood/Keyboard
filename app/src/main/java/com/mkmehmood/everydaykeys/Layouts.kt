package com.mkmehmood.everydaykeys

class Lang(val code: String, val name: String, val rows: List<String>, val latin: Boolean, val alts: Map<String, String> = emptyMap())
class Tool(val id: String, val label: String, val icon: Int)

object Layouts {
    val ALL = listOf(
        Lang("en", "English (QWERTY)", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), true, mapOf(
            "a" to "àáâäãåā", "e" to "èéêëēė", "i" to "ìíîïī", "o" to "òóôöõō", "u" to "ùúûüū",
            "c" to "çćč", "n" to "ñń", "s" to "śšß", "y" to "ýÿ", "z" to "žźż", "l" to "ł", "d" to "đ")),
        Lang("ur", "Urdu", listOf("قوعرتےءیہپ", "اسدفگحجکل", "زشچطبنم"), false, mapOf(
            "ا" to "آأإ", "ہ" to "ھۃة", "ی" to "ئۓ", "ن" to "ں", "ت" to "ٹۃ", "د" to "ڈذ", "ر" to "ڑ",
            "ز" to "ژذظ", "س" to "ثص", "ح" to "خ", "ع" to "غ", "ط" to "ظ", "ک" to "ك", "و" to "ؤ", "ب" to "ٻ", "ق" to "ض")),
        Lang("ar", "Arabic", listOf("ضصثقفغعهخح", "شسيبلاتنمك", "ئءؤرىةوز"), false, mapOf(
            "ا" to "أإآ", "ي" to "ىئ", "و" to "ؤ", "ه" to "ة")),
        Lang("hi", "Hindi (Inscript)", listOf("ौैाीूबहगदज", "ोे्िुपरकतच", "ंमनवलसय"), false, mapOf(
            "क" to "ख", "ग" to "घ", "च" to "छ", "त" to "थ", "द" to "ध", "प" to "फ", "ब" to "भ", "ज" to "झ", "र" to "ऋ", "न" to "ण"))
    )
    fun enabled(csv: String): List<Lang> =
        csv.split(",").mapNotNull { c -> ALL.firstOrNull { it.code == c.trim() } }.ifEmpty { listOf(ALL[0]) }

    val SYM1 = listOf("@#$%&-+()/", "*\"':;!?_=", "~`|•√π÷×")
    val SYM2 = listOf("€£¥¢₹₨©®™°", "[]{}<>^\\¡¿", "≈≠±≤≥∞§¶…")
    val DIGITS = "1234567890"

    val TOOLS = listOf(
        Tool("clip", "Clipboard", R.drawable.ic_clipboard), Tool("snip", "Snippets", R.drawable.ic_snippets),
        Tool("emoji", "Emoji", R.drawable.ic_emoji), Tool("voice", "Voice typing", R.drawable.ic_mic),
        Tool("selall", "Select all", R.drawable.ic_select_all), Tool("cut", "Cut", R.drawable.ic_cut),
        Tool("copy", "Copy", R.drawable.ic_copy), Tool("paste", "Paste latest", R.drawable.ic_paste),
        Tool("undo", "Undo", R.drawable.ic_undo), Tool("redo", "Redo", R.drawable.ic_redo),
        Tool("left", "Cursor left", R.drawable.ic_arrow_left), Tool("right", "Cursor right", R.drawable.ic_arrow_right),
        Tool("search", "Quick search", R.drawable.ic_search), Tool("translate", "Translate", R.drawable.ic_language),
        Tool("settings", "Settings", R.drawable.ic_settings)
    )

    val EMOJI = listOf(
        "😀😃😄😁😆😅😂🤣😊😇🙂😉😍🥰😘😋😎🤩🥳😏😒😞😔😢😭😤😡🤯😱🤔🙄😴",
        "👍👎👌🤞🤝👏🙌🙏💪👋🤚🖐👈👉👆👇🤙🤘🤟",
        "🧡💛💚💙💜🖤🤍🤎💔💕💖💗💘💝🔥⭐🌟✨💯💢💥💫🎉🎊🎁🏆",
        "🍎🍌🍇🍓🍕🍔🍟🌭🍿🍩🍪🎂☕🍺🍷🚗🚀🏠📱💻📷🎵🎮⚽🏀🌍🌞🌙"
    )
}
