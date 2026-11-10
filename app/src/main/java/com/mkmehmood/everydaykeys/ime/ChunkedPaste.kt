package com.mkmehmood.everydaykeys.ime

import android.view.inputmethod.InputConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/**
 * Android limits how much data can cross the app boundary in one call, so a single huge
 * commitText can crash or silently truncate. Large text is sent in small pieces instead.
 */
object ChunkedPaste {
    suspend fun paste(text: String, chunk: Int, gapMs: Long, ic: () -> InputConnection?, onProgress: (Int) -> Unit) {
        val n = text.length
        var i = 0
        val multi = n > chunk
        while (i < n) {
            coroutineContext.ensureActive()
            var end = minOf(n, i + chunk)
            if (end < n && Character.isHighSurrogate(text[end - 1])) end--
            val conn = ic() ?: return
            conn.commitText(text.substring(i, end), 1)
            i = end
            if (multi) {
                onProgress((i * 100L / n).toInt())
                if (gapMs > 0) delay(gapMs) else yield()
            }
        }
    }
}
