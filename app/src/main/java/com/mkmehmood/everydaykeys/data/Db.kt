package com.mkmehmood.everydaykeys.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clips")
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preview: String,
    val length: Int,
    val inlineText: String? = null,
    val filePath: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** SHA-256 of large (file-backed) text, so the same document copied twice is stored once. */
    val hash: String? = null
)

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val shortcut: String
)

@Entity(tableName = "words")
data class Word(
    @PrimaryKey val word: String,
    val personal: Boolean = true
)

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips WHERE (:q = '' OR preview LIKE '%' || :q || '%') ORDER BY pinned DESC, createdAt DESC")
    fun observe(q: String): Flow<List<Clip>>
    @Insert suspend fun insert(c: Clip): Long
    @Update suspend fun update(c: Clip)
    @Delete suspend fun delete(c: Clip)
    @Query("SELECT * FROM clips") suspend fun all(): List<Clip>
    @Query("SELECT * FROM clips ORDER BY createdAt DESC LIMIT 1") suspend fun latest(): Clip?
    @Query("SELECT * FROM clips WHERE pinned = 0") suspend fun unpinned(): List<Clip>
    @Query("DELETE FROM clips WHERE pinned = 0") suspend fun clearUnpinned()
    @Query("DELETE FROM clips") suspend fun clear()
    @Query("SELECT * FROM clips WHERE inlineText = :t LIMIT 1") suspend fun findInline(t: String): Clip?
    @Query("SELECT * FROM clips WHERE hash = :h LIMIT 1") suspend fun findByHash(h: String): Clip?
    @Query("SELECT * FROM clips WHERE pinned = 0 AND createdAt < :t") suspend fun older(t: Long): List<Clip>
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY title") fun observe(): Flow<List<Snippet>>
    @Query("SELECT * FROM snippets") suspend fun all(): List<Snippet>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: Snippet)
    @Delete suspend fun delete(s: Snippet)
    @Query("DELETE FROM snippets") suspend fun clear()
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE personal = 1 ORDER BY word") fun observePersonal(): Flow<List<Word>>
    @Query("SELECT * FROM words WHERE personal = 1") suspend fun personalList(): List<Word>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(w: Word)
    @Delete suspend fun delete(w: Word)
    @Query("DELETE FROM words") suspend fun clear()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clips ADD COLUMN hash TEXT")
    }
}

@Database(entities = [Clip::class, Snippet::class, Word::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun clips(): ClipDao
    abstract fun snippets(): SnippetDao
    abstract fun words(): WordDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "everyday.db").addMigrations(MIGRATION_1_2).build().also { inst = it }
        }
    }
}
