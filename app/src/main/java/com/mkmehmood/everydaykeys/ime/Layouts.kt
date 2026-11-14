package com.mkmehmood.everydaykeys.ime

object Layouts {
    private fun r(s: String) = s.split(" ")

    /** Normal layer. Rows are 10, 10 and 7 keys (Hindi uses 12, 11 and 7 narrower keys). */
    val letters: Map<String, List<List<String>>> = mapOf(
        "en" to listOf(r("q w e r t y u i o p"), r("a s d f g h j k l"), r("z x c v b n m")),
        "ur" to listOf(r("ق و ع ر ت ے ء ی ہ پ"), r("ا س د ف گ ھ ج ک ل"), r("ز ش چ ط ب ن م")),
        "ar" to listOf(r("ض ص ث ق ف غ ع ه خ ح"), r("ش س ي ب ل ا ت ن م ك"), r("ج د ذ ر ز و ط")),
        // Hindi follows the standard InScript layout
        "hi" to listOf(r("ौ ै ा ी ू ब ह ग द ज ड ़"), r("ो े ् ि ु प र क त च ट"), r("ं म न व ल स य"))
    )

    /**
     * Second layer, shown while Shift is on, for scripts that have no upper case. It holds the rest of each alphabet,
     * each key sitting over a related key of the normal layer, plus the diacritics. Same row shape as [letters].
     */
    val shifted: Map<String, List<List<String>>> = mapOf(
        "ur" to listOf(r("ث ؤ غ ڑ ٹ ۓ ً ئ ۃ ّ"), r("آ ص ڈ َ ِ ُ ح خ ں"), r("ژ ض ذ ظ ْ ٰ ٍ")),
        "ar" to listOf(r("ً ٌ ٍ َ ُ ِ ّ ْ ٰ ـ"), r("ئ ء ؤ ة ى آ أ إ لا ،"), r("ظ ؟ ؛ ٪ ٫ ٬ ﷼")),
        "hi" to listOf(r("औ ऐ आ ई ऊ भ ङ घ ध झ ढ ञ"), r("ओ ए अ इ उ फ ऱ ख थ छ ठ"), r("ँ ण ऩ ऴ ळ श ष"))
    )

    /** Languages whose Shift key switches to [shifted] instead of making capitals. */
    fun hasShiftLayer(lang: String) = shifted.containsKey(lang)

    /** Combining marks are drawn on a dotted circle so they are visible on their own key. */
    fun display(k: String): String {
        if (k.length != 1) return k
        return when (Character.getType(k[0])) {
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> "\u25CC" + k
            else -> k
        }
    }

    val langNames = mapOf("en" to "English", "ur" to "Urdu", "ar" to "Arabic", "hi" to "Hindi")

    val accents: Map<String, List<String>> = mapOf(
        "a" to r("à á â ä æ ã å ā"), "e" to r("è é ê ë ē ė ę"), "i" to r("î ï í ī į ì"),
        "o" to r("ô ö ò ó œ ø ō õ"), "u" to r("û ü ù ú ū"), "c" to r("ç ć č"), "n" to r("ñ ń"),
        "s" to r("ß ś š"), "y" to r("ÿ ý"), "z" to r("ž ź ż"), "l" to r("ł"), "d" to r("ð"),
        "ا" to r("آ أ إ"), "ي" to r("ئ ى"), "ه" to r("ة"), "و" to r("ؤ"), "ی" to r("ئ ے"),
        "ل" to r("لا"), "ہ" to r("ۃ ھ"), "ن" to r("ں"), "ے" to r("ۓ"),
        // Hindi: conjuncts, nukta forms and the signs that have no key of their own
        "क" to r("क्ष क़"), "ज" to r("ज्ञ ज़"), "त" to r("त्र"), "र" to r("ऋ ृ"), "ं" to r("ः ँ"),
        "ह" to r("ॐ ः ।"), "अ" to r("ऑ ऍ"), "ा" to r("ॉ"), "े" to r("ॅ"), "्" to r("। ॥"),
        "ग" to r("ग़"), "ख" to r("ख़"), "फ" to r("फ़"), "ड" to r("ड़"), "ढ" to r("ढ़")
    )

    val periodAlts = r("! ? ; : ' \" - …")

    val sym1 = listOf(r("1 2 3 4 5 6 7 8 9 0"), r("@ # $ _ & - + ( ) /"), r("* \" ' : ; ! ? ,"))
    val sym2 = listOf(r("~ ` | ^ { } [ ] < >"), r("€ £ ¥ ₹ ¢ ° = % \\ •"), r("© ® ™ § … ¿ ¡ ."))
    /** math and science */
    val sym3 = listOf(r("± × ÷ ≠ ≈ ≤ ≥ ∞ √ π"), r("∑ ∏ ∆ ∫ ∂ ∈ ∉ ∪ ∩ ∅"), r("² ³ ¼ ½ ¾ ‰ µ Ω ∴ ∵"))
    /** arrows, marks, quotes and currency */
    val sym4 = listOf(r("← → ↑ ↓ ↔ ⇒ ⇐ ⇔ ↩ ↪"), r("✓ ✗ ★ ☆ ♥ ♦ ♣ ♠ ♪ ☺"), r("« » “ ” ‘ ’ ‹ › ₽ ₩"))

    val symPages = listOf(sym1, sym2, sym3, sym4)

    /**
     * Width (as a layout weight) of the backspace key that closes the third symbol row: the space left of a ten key row.
     * Compose throws on a weight of zero or less, and pages 3 and 4 have ten keys in that row, so it never goes below 1.5.
     */
    fun backspaceWeight(keysInRow: Int): Float = maxOf(10f - keysInRow, 1.5f)

    /** Same pages for when the digit row is shown above them; page 1 swaps its own digits for a row of extra symbols. */
    val symPagesWithNumberRow = listOf(listOf(sym1[1], sym1[2], r("% = \\ | < > [ ]")), sym2, sym3, sym4)

    /** label shown on key, text committed */
    val ops = listOf("+" to "+", "−" to "−", "×" to "×", "÷" to "÷", "=" to "=", "%" to "%", "/" to "/")
}
