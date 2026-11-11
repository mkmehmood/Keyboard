package com.mkmehmood.everydaykeys

import com.mkmehmood.everydaykeys.ime.WordContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordContextTest {
    private fun w(s: String) = WordContext.currentWord(s)

    @Test fun plainWords() {
        assertEquals("wor", w("hello wor"))
        assertEquals("teh", w("(teh"))
        assertEquals("b", w("a,b"))
        assertEquals("hel", w("say 'hel"))
        assertEquals("don't", w("I don't"))
    }

    @Test fun nothingWhenNoWord() {
        assertEquals("", w(""))
        assertEquals("", w("hello "))
        assertEquals("", w("don'"))
        assertEquals("", w("the dogs'"))
    }

    @Test fun neverInsideOtherThings() {
        assertEquals("", w("3rd"))
        assertEquals("", w("mail user@gma"))
        assertEquals("", w("end.the"))
        assertEquals("", w("www.exa"))
        assertEquals("", w("#tag"))
        assertEquals("", w("file_na"))
    }

    @Test fun cursorInsideWord() {
        assertTrue(WordContext.insideWord("lo"))
        assertTrue(WordContext.insideWord("5"))
        assertFalse(WordContext.insideWord(""))
        assertFalse(WordContext.insideWord(" more"))
        assertFalse(WordContext.insideWord(".x"))
    }
}
