package com.mkmehmood.everydaykeys.ime

/**
 * Decides which part of the text around the cursor counts as "the word being typed".
 * Autocorrect and suggestions only act on plain words, never on pieces of URLs, emails, numbers or hashtags.
 */
object WordContext {
    /** Characters a plain word may directly follow: quotes, brackets, dashes and sentence punctuation. */
    private const val OK_BEFORE = "([{<\"“‘'«-—,;!?)"

    /**
     * The plain word at the very end of [before], or "" when there isn't one. That includes a trailing apostrophe
     * ("dogs'", "don'"), and a word glued to digits or symbols ("3rd", "user@gma", "a.b", "#tag", "file_na").
     * Leading apostrophes (an opening quote) are not part of the word.
     */
    /** Combining marks (Urdu and Arabic diacritics, Hindi vowel signs) belong to the word they follow. */
    fun isMark(c: Char): Boolean = when (Character.getType(c)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || isMark(c)

    fun currentWord(before: String): String {
        val run = before.takeLastWhile { isWordChar(it) }
        if (run.isEmpty() || run.last() == '\'') return ""
        val word = run.trimStart('\'')
        if (word.isEmpty()) return ""
        val start = before.length - word.length
        if (start > 0) {
            val p = before[start - 1]
            if (!p.isWhitespace() && OK_BEFORE.indexOf(p) < 0) return ""
        }
        return word
    }

    /** True when the cursor sits inside a word, so the letters after it belong to the same word. */
    fun insideWord(after: String): Boolean = after.isNotEmpty() && (after[0].isLetterOrDigit() || after[0] == '\'' || isMark(after[0]))
}
