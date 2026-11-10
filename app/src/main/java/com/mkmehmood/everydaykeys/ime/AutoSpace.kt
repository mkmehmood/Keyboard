package com.mkmehmood.everydaykeys.ime

/** Decides when typing a symbol should also add a space after it. Pure logic, so it can be unit tested. */
object AutoSpace {
    /**
     * Symbols that join onto what follows, or that open something: brackets, quotes, currency, operators and
     * characters used in emails, links, hashtags and file paths. These never get an automatic space.
     */
    private const val NO_SPACE_AFTER = "([{<\"'`@#/\\_-~^|&=+*$€£¥₹"

    fun isSymbol(ch: Char) =
        !ch.isLetterOrDigit() && !ch.isWhitespace() && !ch.isISOControl() && !Character.isSurrogate(ch)

    /**
     * @param before the character just before the cursor, or null at the start of the text
     * @param after the character just after the cursor, or null at the end of the text
     */
    fun shouldSpace(symbol: Char, before: Char?, after: Char?): Boolean {
        if (!isSymbol(symbol) || symbol in NO_SPACE_AFTER) return false
        if (after != null) return false                                   // editing in the middle of text
        if (symbol in ".,:;" && before?.isDigit() == true) return false  // 3.14, 1,000, 10:30
        if (before == symbol) return false                                // "..." and "!!"
        return true
    }
}
