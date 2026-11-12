package com.mkmehmood.everydaykeys

import com.mkmehmood.everydaykeys.ime.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolsLayoutTest {
    @Test fun backspaceWeightIsAlwaysPositive() {
        // Regression: a ten key row used to give weight 0.0, which crashes Compose ("invalid weight 0.0")
        for (keys in 0..14) assertTrue("keys=$keys", Layouts.backspaceWeight(keys) > 0f)
        assertEquals(1.5f, Layouts.backspaceWeight(10), 0f)
        assertEquals(2f, Layouts.backspaceWeight(8), 0f)
    }

    @Test fun everySymbolPageHasAPositiveBackspaceWidth() {
        for (pages in listOf(Layouts.symPages, Layouts.symPagesWithNumberRow)) {
            pages.forEachIndexed { p, rows ->
                assertTrue("page ${p + 1} has a third row", rows.size > 2)
                assertTrue("page ${p + 1}", Layouts.backspaceWeight(rows[2].size) > 0f)
            }
        }
    }
}
