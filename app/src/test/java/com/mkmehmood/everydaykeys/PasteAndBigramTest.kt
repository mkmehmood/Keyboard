package com.mkmehmood.everydaykeys

import android.view.inputmethod.InputConnection
import com.mkmehmood.everydaykeys.data.BigramModel
import com.mkmehmood.everydaykeys.ime.ChunkedPaste
import com.mkmehmood.everydaykeys.ime.JoinReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.lang.reflect.Proxy

class PasteAndBigramTest {
    private fun fakeConnection(sink: MutableList<String>): InputConnection =
        Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, m, a ->
            if (m.name == "commitText") sink.add(a[0].toString())
            if (m.returnType == java.lang.Boolean.TYPE) true else null
        } as InputConnection

    private fun paste(text: String, chunk: Int): List<String> {
        val pieces = mutableListOf<String>()
        val ic = fakeConnection(pieces)
        runBlocking { ChunkedPaste.run(StringReader(text), text.length, chunk, 0L, { ic }) { } }
        return pieces
    }

    @Test fun largeTextArrivesIntactInSmallPieces() {
        val text = "abcdefghij".repeat(25_000)
        val pieces = paste(text, 100_000)
        assertEquals(text, pieces.joinToString(""))
        assertEquals(3, pieces.size)
        assertTrue(pieces.all { it.length <= 100_001 })
    }

    @Test fun surrogatePairsAreNeverSplit() {
        val text = "😀".repeat(10)
        val pieces = paste(text, 3)
        assertEquals(text, pieces.joinToString(""))
        assertFalse(pieces.any { Character.isHighSurrogate(it.last()) })
    }

    @Test fun emptyTextCommitsNothing() = assertTrue(paste("", 10).isEmpty())

    @Test fun mergedClipsAreJoinedWithNewlines() {
        val r = JoinReader(listOf({ StringReader("one") }, { StringReader("two") }, { StringReader("three") }))
        assertEquals("one\ntwo\nthree", r.readText())
    }

    @Test fun bigramsRankByCount() {
        val m = BigramModel()
        repeat(3) { m.learn("good", "morning") }
        m.learn("good", "night")
        assertEquals(listOf("morning", "night"), m.next("Good", 3))
        assertTrue(m.next("unknown").isEmpty())
    }

    @Test fun bigramsSurviveSaveAndLoad() {
        val a = BigramModel()
        repeat(2) { a.learn("see", "you") }
        a.learn("see", "later")
        val b = BigramModel()
        b.load(a.serialize())
        assertEquals(listOf("you", "later"), b.next("see", 3))
    }

    @Test fun bigramsStayUnderTheirLimit() {
        val m = BigramModel(maxPairs = 50)
        for (i in 0 until 500) m.learn("w$i", "x$i")
        val kept = (0 until 500).count { m.next("w$it").isNotEmpty() }
        assertTrue("kept $kept", kept in 1..50)
        assertTrue(m.next("w499").isNotEmpty())
    }

    @Test fun clearForgetsEverything() {
        val m = BigramModel()
        m.learn("a", "b")
        m.clear()
        assertTrue(m.next("a").isEmpty())
    }
}
