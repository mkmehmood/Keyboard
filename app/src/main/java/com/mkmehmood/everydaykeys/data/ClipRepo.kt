package com.mkmehmood.everydaykeys.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.io.StringReader
import java.util.UUID

/** Small clips live in Room. Anything above INLINE_MAX chars is written to a file and only referenced. */
class ClipRepo(ctx: Context) {
    private val dao = AppDb.get(ctx).clips()
    private val dir = File(ctx.filesDir, "clips").apply { mkdirs() }

    companion object { const val INLINE_MAX = 20_000 }

    fun observe(q: String) = dao.observe(q)

    suspend fun add(text: String, pinned: Boolean = false) = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext
        val preview = text.take(200)
        if (text.length <= INLINE_MAX) {
            val existing = dao.findInline(text)
            if (existing != null) {
                dao.update(existing.copy(createdAt = System.currentTimeMillis(), pinned = existing.pinned || pinned))
            } else {
                dao.insert(Clip(preview = preview, length = text.length, inlineText = text, pinned = pinned))
            }
        } else {
            val f = File(dir, UUID.randomUUID().toString() + ".txt")
            f.writeText(text)
            dao.insert(Clip(preview = preview, length = text.length, filePath = f.absolutePath, pinned = pinned))
        }
    }

    suspend fun text(c: Clip): String = withContext(Dispatchers.IO) { c.inlineText ?: File(c.filePath!!).readText() }

    suspend fun setPinned(c: Clip, pinned: Boolean) = withContext(Dispatchers.IO) { dao.update(c.copy(pinned = pinned)) }

    suspend fun delete(c: Clip) = withContext(Dispatchers.IO) {
        c.filePath?.let { File(it).delete() }
        dao.delete(c)
    }

    suspend fun clearUnpinned() = withContext(Dispatchers.IO) {
        dao.unpinned().forEach { c -> c.filePath?.let { File(it).delete() } }
        dao.clearUnpinned()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        dao.clear()
    }

    suspend fun purgeOlderThanDays(days: Int) = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        dao.older(limit).forEach { c ->
            c.filePath?.let { File(it).delete() }
            dao.delete(c)
        }
    }

    fun usageBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}

fun Clip.open(): Reader = if (inlineText != null) StringReader(inlineText) else FileReader(filePath!!)
