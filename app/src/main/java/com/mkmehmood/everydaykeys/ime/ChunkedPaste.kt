package com.mkmehmood.everydaykeys.ime

import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.Reader

/** Concatenates several readers, putting a newline between them (used to merge clipboard items). */
class JoinReader(private val openers: List<() -> Reader>) : Reader() {
    private var idx = -1
    private var cur: Reader? = null

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        while (true) {
            if (cur == null) {
                if (idx + 1 >= openers.size) return -1
                idx++
                cur = openers[idx]()
                if (idx > 0) { cbuf[off] = '\n'; return 1 }
            }
            val n = cur!!.read(cbuf, off, len)
            if (n < 0) { cur!!.close(); cur = null } else return n
        }
    }

    override fun close() { cur?.close() }
}

object ChunkedPaste {
    /**
     * Streams [reader] into the editor in pieces of at most [chunk] chars.
     * Android limits how much data one IPC call can carry, so huge text is never sent in one commitText.
     */
    suspend fun run(
        reader: Reader, total: Int, chunk: Int, delayMs: Long,
        ic: () -> InputConnection?, onProgress: (Float?) -> Unit
    ) = withContext(Dispatchers.IO) {
        reader.use { r ->
            val buf = CharArray(chunk + 1)
            var done = 0
            while (isActive) {
                var n = r.read(buf, 0, chunk)
                if (n <= 0) break
                if (Character.isHighSurrogate(buf[n - 1])) {
                    val nx = r.read()
                    if (nx >= 0) buf[n++] = nx.toChar()
                }
                val conn = ic() ?: break
                conn.commitText(String(buf, 0, n), 1)
                done += n
                onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else null)
                delay(delayMs)
            }
        }
        onProgress(null)
    }
}
