package com.mkmehmood.everydaykeys.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Entity(tableName = "clips")
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String?,
    val filePath: String?,
    val preview: String,
    val size: Long,
    val pinned: Boolean = false,
    val createdAt: Long,
    val kind: String,
    val hash: Int,
)

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val shortcut: String,
    val body: String,
)

@Entity(tableName = "words")
data class Word(@PrimaryKey val word: String, val count: Int, val personal: Boolean)

@Entity(tableName = "bigrams", primaryKeys = ["prev", "next"])
data class Bigram(val prev: String, val next: String, val count: Int)

@Dao
interface ClipDao {
    @Query("SELECT id, NULL AS text, filePath, preview, size, pinned, createdAt, kind, hash FROM clips WHERE (:q = '' OR preview LIKE '%' || :q || '%' OR text LIKE '%' || :q || '%') ORDER BY pinned DESC, createdAt DESC LIMIT 500")
    fun observe(q: String): Flow<List<Clip>>
    @Query("SELECT * FROM clips WHERE id = :id") suspend fun get(id: Long): Clip?
    @Query("SELECT id, NULL AS text, filePath, preview, size, pinned, createdAt, kind, hash FROM clips ORDER BY createdAt DESC LIMIT :n")
    suspend fun recent(n: Int): List<Clip>
    @Query("SELECT id FROM clips WHERE hash = :h AND size = :size LIMIT 1") suspend fun findDup(h: Int, size: Long): Long?
    @Insert suspend fun insert(c: Clip): Long
    @Query("UPDATE clips SET createdAt = :t WHERE id = :id") suspend fun touch(id: Long, t: Long)
    @Query("UPDATE clips SET pinned = NOT pinned WHERE id = :id") suspend fun togglePin(id: Long)
    @Query("DELETE FROM clips WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT filePath FROM clips WHERE id = :id") suspend fun fileOf(id: Long): String?
    @Query("SELECT filePath FROM clips WHERE pinned = 0 AND filePath IS NOT NULL") suspend fun unpinnedFiles(): List<String>
    @Query("DELETE FROM clips WHERE pinned = 0") suspend fun clearUnpinned()
    @Query("SELECT filePath FROM clips WHERE pinned = 0 AND createdAt < :t AND filePath IS NOT NULL") suspend fun oldFiles(t: Long): List<String>
    @Query("DELETE FROM clips WHERE pinned = 0 AND createdAt < :t") suspend fun deleteOlder(t: Long)
    @Query("SELECT * FROM clips") suspend fun all(): List<Clip>
    @Query("DELETE FROM clips") suspend fun deleteAll()
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY title COLLATE NOCASE") fun observe(): Flow<List<Snippet>>
    @Query("SELECT * FROM snippets") suspend fun all(): List<Snippet>
    @Query("SELECT * FROM snippets WHERE shortcut = :s LIMIT 1") suspend fun byShortcut(s: String): Snippet?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: Snippet): Long
    @Query("DELETE FROM snippets WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM snippets") suspend fun deleteAll()
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE word LIKE :prefix || '%' ORDER BY personal DESC, count DESC LIMIT :n")
    suspend fun top(prefix: String, n: Int): List<Word>
    @Query("SELECT * FROM words ORDER BY personal DESC, word COLLATE NOCASE") fun observe(): Flow<List<Word>>
    @Query("SELECT * FROM words") suspend fun all(): List<Word>
    @Query("SELECT * FROM words WHERE word = :w") suspend fun get(w: String): Word?
    @Query("UPDATE words SET count = count + 1 WHERE word = :w") suspend fun bump(w: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(w: Word)
    @Query("DELETE FROM words WHERE word = :w") suspend fun delete(w: String)
    @Query("DELETE FROM words WHERE personal = 0") suspend fun clearLearned()
    @Query("DELETE FROM words") suspend fun deleteAll()
}

@Dao
interface BigramDao {
    @Query("SELECT next FROM bigrams WHERE prev = :p ORDER BY count DESC LIMIT :n") suspend fun nexts(p: String, n: Int): List<String>
    @Query("UPDATE bigrams SET count = count + 1 WHERE prev = :p AND next = :n") suspend fun bump(p: String, n: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(b: Bigram)
    @Query("DELETE FROM bigrams") suspend fun clear()
}

@Database(entities = [Clip::class, Snippet::class, Word::class, Bigram::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun clips(): ClipDao
    abstract fun snippets(): SnippetDao
    abstract fun words(): WordDao
    abstract fun bigrams(): BigramDao

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "everyday_keys.db").build().also { inst = it }
        }
    }
}

/** Small items live in SQLite. Very large items are written to files; only a reference stays in the table. */
class ClipStore(private val ctx: Context) {
    private val db = Db.get(ctx)
    private val dir: File get() = File(ctx.filesDir, "clips").apply { mkdirs() }

    companion object {
        const val LARGE = 100_000
        private val URL = Regex("^https?://\\S+$")
    }

    suspend fun add(text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        val h = text.hashCode()
        val dup = db.clips().findDup(h, text.length.toLong())
        if (dup != null) { db.clips().touch(dup, System.currentTimeMillis()); return@withContext }
        val big = text.length > LARGE
        var path: String? = null
        if (big) {
            val f = File(dir, "${UUID.randomUUID()}.txt")
            f.writeText(text)
            path = f.absolutePath
        }
        val kind = if (big) "file" else if (URL.matches(text.trim())) "link" else "text"
        db.clips().insert(
            Clip(text = if (big) null else text, filePath = path, preview = text.take(300),
                size = text.length.toLong(), createdAt = System.currentTimeMillis(), kind = kind, hash = h)
        )
    }

    suspend fun read(c: Clip): String = withContext(Dispatchers.IO) {
        val p = c.filePath
        if (p != null) File(p).readText() else (db.clips().get(c.id)?.text ?: c.preview)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.clips().fileOf(id)?.let { File(it).delete() }
        db.clips().delete(id)
    }

    suspend fun clearUnpinned() = withContext(Dispatchers.IO) {
        db.clips().unpinnedFiles().forEach { File(it).delete() }
        db.clips().clearUnpinned()
    }

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        db.clips().deleteAll()
    }

    suspend fun cleanup(retention: String) = withContext(Dispatchers.IO) {
        val day = 86_400_000L
        val age = when (retention) { "day" -> day; "week" -> 7 * day; "month" -> 30 * day; else -> return@withContext }
        val cutoff = System.currentTimeMillis() - age
        db.clips().oldFiles(cutoff).forEach { File(it).delete() }
        db.clips().deleteOlder(cutoff)
    }

    suspend fun usageBytes(): Long = withContext(Dispatchers.IO) {
        (dir.listFiles()?.sumOf { it.length() } ?: 0L) + ctx.getDatabasePath("everyday_keys.db").length()
    }
}
