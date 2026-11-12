package com.mkmehmood.everydaykeys.ime

object Layouts {
    private fun r(s: String) = s.split(" ")

    val letters: Map<String, List<List<String>>> = mapOf(
        "en" to listOf(r("q w e r t y u i o p"), r("a s d f g h j k l"), r("z x c v b n m")),
        "ur" to listOf(r("ق و ع ر ت ے ء ی ہ پ"), r("ا س د ف گ ھ ج ک ل"), r("ز ش چ ط ب ن م")),
        "ar" to listOf(r("ض ص ث ق ف غ ع ه خ ح"), r("ش س ي ب ل ا ت ن م ك"), r("ئ ء ؤ ر ى ة و ز")),
        "hi" to listOf(r("ौ ै ा ी ू ब ह ग द ज"), r("ो े ् ि ु प र क त च"), r("ं म न व ल स य"))
    )

    val langNames = mapOf("en" to "English", "ur" to "Urdu", "ar" to "Arabic", "hi" to "Hindi")

    val accents: Map<String, List<String>> = mapOf(
        "a" to r("à á â ä æ ã å ā"), "e" to r("è é ê ë ē ė ę"), "i" to r("î ï í ī į ì"),
        "o" to r("ô ö ò ó œ ø ō õ"), "u" to r("û ü ù ú ū"), "c" to r("ç ć č"), "n" to r("ñ ń"),
        "s" to r("ß ś š"), "y" to r("ÿ ý"), "z" to r("ž ź ż"), "l" to r("ł"), "d" to r("ð"),
        "ا" to r("آ أ إ"), "ي" to r("ئ ى"), "ه" to r("ة"), "و" to r("ؤ"), "ی" to r("ئ ے")
    )

    val periodAlts = r("! ? ; : ' \" - …")

    val sym1 = listOf(r("1 2 3 4 5 6 7 8 9 0"), r("@ # $ _ & - + ( ) /"), r("* \" ' : ; ! ? ,"))
    val sym2 = listOf(r("~ ` | ^ { } [ ] < >"), r("€ £ ¥ ₹ ¢ ° = % \\ •"), r("© ® ™ § … ¿ ¡ ."))
    /** math and science */
    val sym3 = listOf(r("± × ÷ ≠ ≈ ≤ ≥ ∞ √ π"), r("∑ ∏ ∆ ∫ ∂ ∈ ∉ ∪ ∩ ∅"), r("² ³ ¼ ½ ¾ ‰ µ Ω ∴ ∵"))
    /** arrows, marks, quotes and currency */
    val sym4 = listOf(r("← → ↑ ↓ ↔ ⇒ ⇐ ⇔ ↩ ↪"), r("✓ ✗ ★ ☆ ♥ ♦ ♣ ♠ ♪ ☺"), r("« » “ ” ‘ ’ ‹ › ₽ ₩"))

    val symPages = listOf(sym1, sym2, sym3, sym4)

    /** Same pages for when the digit row is shown above them; page 1 swaps its own digits for a row of extra symbols. */
    val symPagesWithNumberRow = listOf(listOf(sym1[1], sym1[2], r("% = \\ | < > [ ]")), sym2, sym3, sym4)

    /** label shown on key, text committed */
    val ops = listOf("+" to "+", "−" to "-", "×" to "×", "÷" to "÷", "=" to "=", "%" to "%", "/" to "/")
}
