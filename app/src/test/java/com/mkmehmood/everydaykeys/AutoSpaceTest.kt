package com.mkmehmood.everydaykeys

import com.mkmehmood.everydaykeys.ime.AutoSpace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSpaceTest {
    @Test fun sentencePunctuationGetsASpace() {
        for (ch in ".,;:!?)%…") assertTrue("$ch", AutoSpace.shouldSpace(ch, 'd', null))
    }

    @Test fun openersJoinersAndOperatorsDoNot() {
        for (ch in "([{<\"'@#/_-+*=&\$€£") assertFalse("$ch", AutoSpace.shouldSpace(ch, 'd', null))
    }

    @Test fun numbersAndTimesAreLeftAlone() {
        assertFalse(AutoSpace.shouldSpace('.', '3', null))
        assertFalse(AutoSpace.shouldSpace(',', '1', null))
        assertFalse(AutoSpace.shouldSpace(':', '0', null))
        assertTrue(AutoSpace.shouldSpace('%', '5', null))
    }

    @Test fun repeatedSymbolsDoNotSplit() {
        assertFalse(AutoSpace.shouldSpace('.', '.', null))
        assertFalse(AutoSpace.shouldSpace('!', '!', null))
    }

    @Test fun noSpaceWhenTypingInTheMiddleOfText() {
        assertFalse(AutoSpace.shouldSpace('.', 'd', ' '))
        assertFalse(AutoSpace.shouldSpace('.', 'd', 'w'))
    }

    @Test fun lettersDigitsAndSpacesAreNotSymbols() {
        for (ch in "aZ5 \n") assertFalse("$ch", AutoSpace.isSymbol(ch))
        assertTrue(AutoSpace.isSymbol('!'))
    }
}
