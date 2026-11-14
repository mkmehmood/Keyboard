package com.mkmehmood.everydaykeys

import com.mkmehmood.everydaykeys.ime.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every letter of Urdu, Arabic and Hindi must be typeable: on the normal layer, the Shift layer, or a long-press popup. */
class AlphabetsTest {
    private fun reachable(lang: String): Set<String> {
        val keys = (Layouts.letters.getValue(lang) + (Layouts.shifted[lang] ?: emptyList())).flatten().toSet()
        return keys + keys.flatMap { Layouts.accents[it] ?: emptyList() }
    }

    private fun missing(lang: String, chars: String) = chars.map { it.toString() }.filter { it !in reachable(lang) }

    @Test fun urduIsComplete() = assertEquals(emptyList<String>(), missing("ur", "ابپتٹثجچحخدڈذرڑزژسشصضطظعغفقکگلمنںوہھءیےآئؤۃ"))

    @Test fun arabicIsComplete() = assertEquals(emptyList<String>(), missing("ar", "ابتثجحخدذرزسشصضطظعغفقكلمنهويةىءئؤأإآ"))

    @Test fun hindiVowelsAndConsonantsAreComplete() {
        assertEquals(emptyList<String>(), missing("hi", "अआइईउऊऋएऐओऔ"))
        assertEquals(emptyList<String>(), missing("hi", "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळ"))
        assertEquals(emptyList<String>(), missing("hi", "ािीुूृेैोौंँः़्"))
    }

    @Test fun hindiConjunctsAreOnLongPress() {
        assertTrue("क्ष" in Layouts.accents.getValue("क"))
        assertTrue("त्र" in Layouts.accents.getValue("त"))
        assertTrue("ज्ञ" in Layouts.accents.getValue("ज"))
    }

    @Test fun layersHaveTheSameRowShapeAndNoDuplicatesPerLayer() {
        for ((lang, base) in Layouts.letters) {
            for (layer in listOfNotNull(base, Layouts.shifted[lang])) {
                assertEquals(3, layer.size)
                layer.forEach { assertTrue("$lang row too long: ${it.size}", it.size <= 12) }
                assertTrue("$lang bottom row must leave room for Shift and Backspace", layer[2].size <= 8)
                val all = layer.flatten()
                assertEquals("$lang has a repeated key", all.size, all.toSet().size)
            }
        }
        for ((lang, sh) in Layouts.shifted) assertEquals(lang, Layouts.letters.getValue(lang).map { it.size }, sh.map { it.size })
    }

    @Test fun marksAreShownOnADottedCircle() {
        assertEquals("\u25CC\u064E", Layouts.display("\u064E"))   // fatha
        assertEquals("ا", Layouts.display("ا"))
        assertEquals("لا", Layouts.display("لا"))
    }
}
