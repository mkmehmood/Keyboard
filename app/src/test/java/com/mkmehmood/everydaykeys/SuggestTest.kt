package com.mkmehmood.everydaykeys

import com.mkmehmood.everydaykeys.data.Suggest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SuggestTest {
    @Before fun setUp() {
        Suggest.reset()
        Suggest.seed(listOf("the", "this", "that", "hello", "help", "helmet", "world", "would", "apple", "apricot"))
    }

    @Test fun completesByFrequency() {
        assertEquals(listOf("the", "this", "that"), Suggest.complete("th", 3))
    }

    @Test fun keepsTypedCapital() {
        assertEquals("The", Suggest.complete("Th", 1).first())
    }

    @Test fun previousWordContextComesFirst() {
        assertEquals(listOf("help", "hello", "helmet"), Suggest.suggest("h", listOf("help"), 3, true))
    }

    @Test fun swappedLettersAreSuggestedAndCorrected() {
        assertEquals(listOf("the"), Suggest.suggest("teh", emptyList(), 3, true))
        assertEquals("the", Suggest.correct("teh", 1))
    }

    @Test fun typoSuggestionsCanBeTurnedOff() {
        assertTrue(Suggest.suggest("teh", emptyList(), 3, false).isEmpty())
    }

    @Test fun oneWrongLetterIsSuggested() {
        assertTrue("hello" in Suggest.suggest("helli", emptyList(), 3, true))
    }

    @Test fun wordsYouUseOftenRankHigher() {
        assertEquals("apple", Suggest.complete("ap", 1).first())
        Suggest.noteTyped("apricot"); Suggest.noteTyped("apricot")
        assertEquals("apricot", Suggest.complete("ap", 1).first())
    }

    @Test fun newWordIsLearnedAfterSeveralUses() {
        assertTrue(Suggest.complete("zor", 3).isEmpty())
        repeat(3) { Suggest.noteTyped("zorbo") }
        assertEquals(listOf("zorbo"), Suggest.complete("zor", 3))
    }

    @Test fun lowercaseIBecomesCapital() {
        assertEquals("I", Suggest.correct("i", 1))
    }

    @Test fun contractionsGetTheirApostrophe() {
        assertEquals("don't", Suggest.correct("dont", 1))
        assertEquals("Don't", Suggest.correct("Dont", 1))
        assertEquals("I'm", Suggest.correct("im", 1))
        assertEquals("can't", Suggest.correct("cant", 2))
    }

    @Test fun namesAcronymsAndMixedCaseAreLeftAlone() {
        assertEquals(null, Suggest.correct("TEH", 1))
        assertEquals(null, Suggest.correct("teH", 1))
        assertEquals(null, Suggest.correct("Teh", 1))
        assertEquals(null, Suggest.correct("DONT", 1))
    }

    @Test fun revertedWordIsNeverCorrectedAgain() {
        assertEquals("the", Suggest.correct("teh", 1))
        Suggest.keep("teh")
        assertEquals(null, Suggest.correct("teh", 1))
        assertEquals(null, Suggest.correct("dont", 1).takeIf { Suggest.keep("dont"); true }.let { Suggest.correct("dont", 1) })
    }

    @Test fun suggestionsFollowTheCaseBeingTyped() {
        assertEquals("hello", Suggest.complete("hel", 1).first())
        assertEquals("Hello", Suggest.complete("Hel", 1).first())
        assertEquals("HELLO", Suggest.complete("HEL", 1).first())
    }
}
