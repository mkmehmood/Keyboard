package com.mkmehmood.everydaykeys

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "clips")
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preview: String,
    val text: String?,
    val filePath: String?,
    val size: Long,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val hash: Int = 0
)

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val shortcut: String, val body: String
)

@Entity(tableName = "words")
data class Word(@PrimaryKey val word: String, val count: Int)

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY pinned DESC, createdAt DESC LIMIT 300") suspend fun all(): List<Clip>
    @Query("SELECT * FROM clips ORDER BY createdAt") suspend fun everything(): List<Clip>
    @Query("SELECT * FROM clips WHERE preview LIKE '%' || :q || '%' ORDER BY pinned DESC, createdAt DESC LIMIT 300") suspend fun search(q: String): List<Clip>
    @Query("SELECT * FROM clips WHERE hash = :h AND size = :s LIMIT 1") suspend fun dup(h: Int, s: Long): Clip?
    @Query("SELECT * FROM clips ORDER BY createdAt DESC LIMIT 1") suspend fun latest(): Clip?
    @Query("SELECT * FROM clips WHERE pinned = 0 AND createdAt < :t") suspend fun older(t: Long): List<Clip>
    @Query("SELECT * FROM clips WHERE pinned = 0") suspend fun unpinned(): List<Clip>
    @Query("SELECT COALESCE(SUM(size), 0) FROM clips") suspend fun totalSize(): Long
    @Query("DELETE FROM clips") suspend fun wipe()
    @Insert suspend fun insert(c: Clip): Long
    @Update suspend fun update(c: Clip)
    @Delete suspend fun delete(c: Clip)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY title") fun flow(): Flow<List<Snippet>>
    @Query("SELECT * FROM snippets") suspend fun all(): List<Snippet>
    @Query("DELETE FROM snippets") suspend fun wipe()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: Snippet)
    @Delete suspend fun delete(s: Snippet)
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY count DESC LIMIT 5000") suspend fun all(): List<Word>
    @Query("SELECT * FROM words ORDER BY word") fun flow(): Flow<List<Word>>
    @Query("DELETE FROM words") suspend fun wipe()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(w: Word)
    @Delete suspend fun delete(w: Word)
}

@Database(entities = [Clip::class, Snippet::class, Word::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun clips(): ClipDao
    abstract fun snippets(): SnippetDao
    abstract fun words(): WordDao
}

object Graph {
    @Volatile private var inst: AppDb? = null
    fun db(ctx: Context): AppDb = inst ?: synchronized(this) {
        inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "everyday_keys.db").build().also { inst = it }
    }
}

/** Small items live in SQLite; big ones are written to files and only referenced from the list. */
object ClipStore {
    const val FILE_THRESHOLD = 20_000

    private fun dir(ctx: Context) = File(ctx.filesDir, "clips").apply { mkdirs() }

    suspend fun add(ctx: Context, text: String, pinned: Boolean = false) = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext
        val dao = Graph.db(ctx).clips()
        val h = text.hashCode(); val size = text.length.toLong()
        val d = dao.dup(h, size)
        if (d != null) { dao.update(d.copy(createdAt = System.currentTimeMillis(), pinned = d.pinned || pinned)); return@withContext }
        val preview = text.take(300)
        if (text.length > FILE_THRESHOLD) {
            val f = File(dir(ctx), "${System.nanoTime()}.txt"); f.writeText(text)
            dao.insert(Clip(preview = preview, text = null, filePath = f.absolutePath, size = size, pinned = pinned, hash = h))
        } else dao.insert(Clip(preview = preview, text = text, filePath = null, size = size, pinned = pinned, hash = h))
    }

    suspend fun read(c: Clip): String = withContext(Dispatchers.IO) {
        c.text ?: c.filePath?.let { File(it).takeIf { f -> f.exists() }?.readText() } ?: c.preview
    }

    suspend fun remove(ctx: Context, c: Clip) = withContext(Dispatchers.IO) {
        c.filePath?.let { File(it).delete() }; Graph.db(ctx).clips().delete(c)
    }

    suspend fun clearAll(ctx: Context, keepPinned: Boolean) = withContext(Dispatchers.IO) {
        val dao = Graph.db(ctx).clips()
        if (keepPinned) dao.unpinned().forEach { remove(ctx, it) } else { dao.wipe(); dir(ctx).listFiles()?.forEach { it.delete() } }
    }

    suspend fun purge(ctx: Context, days: Int) = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext
        Graph.db(ctx).clips().older(System.currentTimeMillis() - days * 86_400_000L).forEach { remove(ctx, it) }
    }

    suspend fun usage(ctx: Context): Long = withContext(Dispatchers.IO) { Graph.db(ctx).clips().totalSize() }
}

// ---------------- Settings (DataStore) ----------------
val Context.ds: DataStore<Preferences> by preferencesDataStore(name = "settings")

object K {
    val theme = intPreferencesKey("theme"); val keyHeight = intPreferencesKey("keyHeight")
    val fontSp = intPreferencesKey("fontSp"); val numRow = booleanPreferencesKey("numRow")
    val oneHanded = intPreferencesKey("oneHanded"); val vibrate = booleanPreferencesKey("vibrate")
    val vibMs = intPreferencesKey("vibMs"); val sound = booleanPreferencesKey("sound")
    val volume = intPreferencesKey("volume"); val longMs = intPreferencesKey("longMs")
    val dragSens = intPreferencesKey("dragSens"); val langs = stringPreferencesKey("langs")
    val accents = booleanPreferencesKey("accents"); val langKey = booleanPreferencesKey("langKey")
    val numpadStyle = intPreferencesKey("numpadStyle"); val ops = booleanPreferencesKey("ops")
    val autoNum = booleanPreferencesKey("autoNum"); val clipOn = booleanPreferencesKey("clipOn")
    val retention = intPreferencesKey("retention"); val skipSensitive = booleanPreferencesKey("skipSensitive")
    val bioLock = booleanPreferencesKey("bioLock"); val chunkSpeed = intPreferencesKey("chunkSpeed")
    val showProgress = booleanPreferencesKey("showProgress"); val autocorrect = intPreferencesKey("autocorrect")
    val suggest = booleanPreferencesKey("suggest"); val autoCap = booleanPreferencesKey("autoCap")
    val dblSpace = booleanPreferencesKey("dblSpace"); val tools = stringPreferencesKey("tools")
}

data class Prefs(
    val theme: Int = 0, val keyHeight: Int = 50, val fontSp: Int = 20, val numRow: Boolean = false,
    val oneHanded: Int = 0, val vibrate: Boolean = true, val vibMs: Int = 15, val sound: Boolean = false,
    val volume: Int = 50, val longMs: Int = 350, val dragSens: Int = 18, val langs: String = "en",
    val accents: Boolean = true, val langKey: Boolean = true, val numpadStyle: Int = 0, val ops: Boolean = true,
    val autoNum: Boolean = true, val clipOn: Boolean = true, val retention: Int = 0, val skipSensitive: Boolean = true,
    val bioLock: Boolean = false, val chunkSpeed: Int = 1, val showProgress: Boolean = true, val autocorrect: Int = 1,
    val suggest: Boolean = true, val autoCap: Boolean = true, val dblSpace: Boolean = true,
    val tools: String = "clip,snip,emoji,voice,selall,copy,paste,undo,redo,left,right,search,settings"
)

fun Preferences.toPrefs(): Prefs {
    val d = Prefs()
    return Prefs(
        theme = this[K.theme] ?: d.theme, keyHeight = this[K.keyHeight] ?: d.keyHeight, fontSp = this[K.fontSp] ?: d.fontSp,
        numRow = this[K.numRow] ?: d.numRow, oneHanded = this[K.oneHanded] ?: d.oneHanded, vibrate = this[K.vibrate] ?: d.vibrate,
        vibMs = this[K.vibMs] ?: d.vibMs, sound = this[K.sound] ?: d.sound, volume = this[K.volume] ?: d.volume,
        longMs = this[K.longMs] ?: d.longMs, dragSens = this[K.dragSens] ?: d.dragSens, langs = this[K.langs] ?: d.langs,
        accents = this[K.accents] ?: d.accents, langKey = this[K.langKey] ?: d.langKey, numpadStyle = this[K.numpadStyle] ?: d.numpadStyle,
        ops = this[K.ops] ?: d.ops, autoNum = this[K.autoNum] ?: d.autoNum, clipOn = this[K.clipOn] ?: d.clipOn,
        retention = this[K.retention] ?: d.retention, skipSensitive = this[K.skipSensitive] ?: d.skipSensitive,
        bioLock = this[K.bioLock] ?: d.bioLock, chunkSpeed = this[K.chunkSpeed] ?: d.chunkSpeed,
        showProgress = this[K.showProgress] ?: d.showProgress, autocorrect = this[K.autocorrect] ?: d.autocorrect,
        suggest = this[K.suggest] ?: d.suggest, autoCap = this[K.autoCap] ?: d.autoCap, dblSpace = this[K.dblSpace] ?: d.dblSpace,
        tools = this[K.tools] ?: d.tools
    )
}

object Store { fun flow(ctx: Context): Flow<Prefs> = ctx.applicationContext.ds.data.map { it.toPrefs() } }
