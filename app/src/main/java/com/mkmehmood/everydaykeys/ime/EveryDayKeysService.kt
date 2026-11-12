package com.mkmehmood.everydaykeys.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mkmehmood.everydaykeys.data.*
import com.mkmehmood.everydaykeys.ui.SettingsActivity
import com.mkmehmood.everydaykeys.ui.UnlockActivity
import com.mkmehmood.everydaykeys.ui.VoiceActivity
import com.mkmehmood.everydaykeys.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

enum class Mode { LETTERS, NUMPAD, SYM1, SYM2, SYM3, SYM4 }
enum class Panel { NONE, CLIP, SNIPPETS, EMOJI, CURSOR, CONVERT, QUICK }
enum class Shift { OFF, ONCE, LOCK }

class EveryDayKeysService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object { @Volatile var instance: EveryDayKeysService? = null }

    private val reg = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var clips: ClipRepo
    private var pasteJob: Job? = null
    private var snippetMap: Map<String, String> = emptyMap()
    private var sensitive = false
    private var smart = true
    private var info: EditorInfo? = null
    private var lastSpaceAt = 0L
    private var lastShiftAt = 0L

    // ---- UI state ----
    var prefs by mutableStateOf(Prefs(emptyPreferences()))
    var mode by mutableStateOf(Mode.LETTERS)
    var panel by mutableStateOf(Panel.NONE)
    var shift by mutableStateOf(Shift.OFF)
    var langIdx by mutableStateOf(0)
    var suggestions by mutableStateOf(listOf<String>())
    /** true while the user has opened the toolbox. Tools also show on their own when there are no suggestions. */
    var toolsOpen by mutableStateOf(false)
    /** The last autocorrect or applied suggestion, so one Backspace can bring back exactly what was typed. */
    private class Corr(val original: String, val replaced: String, val tail: String, val autocorrect: Boolean)
    private var lastCorr: Corr? = null
    private var keepAsTyped: String? = null
    private var autoSpaced = false
    /** true right after a suggestion added its trailing space, so punctuation can take that space's place */
    private var suggestSpaced = false
    /** When the keyboard itself last changed the text; a cursor move long after that came from the user. */
    private var ownEditAt = 0L
    private fun touch() { ownEditAt = SystemClock.uptimeMillis() }

    /** Forget everything that only makes sense right after our own edit (undo, soft spaces, double-space timing). */
    private fun resetTypingState() {
        lastCorr = null; keepAsTyped = null; autoSpaced = false; suggestSpaced = false; lastSpaceAt = 0L
    }
    /** Most recently copied item that has not been pasted yet; shown in the suggestion bar. */
    var pendingClip by mutableStateOf<Clip?>(null)
    val showClipChip: Boolean get() = pendingClip != null && !sensitive && clipUnlocked
    class Trans(val result: String? = null, val message: String? = null, val target: String = "", val raw: String = "", val selected: Boolean = false)
    var trans by mutableStateOf<Trans?>(null)
    private var transJob: kotlinx.coroutines.Job? = null
    private var onlineJob: kotlinx.coroutines.Job? = null
    var alts by mutableStateOf<List<String>?>(null)
    var progress by mutableStateOf<Float?>(null)

    /** A short confirmation (Copied, Pasted, Undo...) shown over the keyboard for a moment. */
    class Notice(val icon: Int, val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)
    var notice by mutableStateOf<Notice?>(null)
    /** True while text is selected: the toolbar shows select all, copy, cut, paste, undo and redo. */
    var hasSelection by mutableStateOf(false)
    private var noticeJob: Job? = null
    fun showNotice(icon: Int, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        notice = Notice(icon, text, action, onAction)
        noticeJob?.cancel()
        noticeJob = scope.launch { kotlinx.coroutines.delay(if (action != null) 2800L else 1600L); notice = null }
    }
    var searching by mutableStateOf(false)
    /** Height in px of the key area when no panel is open; panels such as the clip preview use it as their upper limit. */
    var keysHeightPx by mutableStateOf(0)
    var query by mutableStateOf("")

    // ---- clipboard lock ----
    private var clipUnlockedAt by mutableStateOf(-1_000_000L)
    private var reopenClip = false
    val clipLockOn: Boolean get() = prefs.bool("clipLock")
    /** True when the lock is off, or the user passed fingerprint / screen lock in the last minute. */
    val clipUnlocked: Boolean get() = !clipLockOn || SystemClock.uptimeMillis() - clipUnlockedAt < 60_000
    fun requestUnlock() = launch(Intent(this, UnlockActivity::class.java))
    fun unlockClips() { clipUnlockedAt = SystemClock.uptimeMillis(); reopenClip = true }

    // ---- unit converter ----
    var convCat by mutableStateOf(0)
    var convFrom by mutableStateOf(0)
    var convTo by mutableStateOf(1)
    var convText by mutableStateOf("")
    private var convLen = 0
    private var learned = 0

    val langs: List<String>
        get() = prefs.str("languages").split(",").filter { it in Layouts.letters.keys }.ifEmpty { listOf("en") }
    val lang: String get() = langs[langIdx.coerceIn(0, langs.lastIndex)]

    private val cm by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val vib by lazy { getSystemService(Vibrator::class.java) }
    private val am by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val clipListener = object : ClipboardManager.OnPrimaryClipChangedListener {
        override fun onPrimaryClipChanged() {
            val p = prefs
            if (!p.bool("clipEnabled")) return
            val clip = cm.primaryClip ?: return
            if (p.bool("skipSensitive")) {
                if (sensitive) return
                if (clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            }
            if (clip.itemCount == 0) return
            scope.launch {
                // Reading the clip (it may be a large text or a content URI) happens off the main thread.
                val t = withContext(Dispatchers.IO) { clip.getItemAt(0)?.coerceToText(this@EveryDayKeysService)?.toString() } ?: return@launch
                clips.add(t)
                pendingClip = withContext(Dispatchers.IO) { AppDb.get(this@EveryDayKeysService).clips().latest() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ssc.performAttach()
        ssc.performRestore(null)
        reg.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        reg.handleLifecycleEvent(Lifecycle.Event.ON_START)
        reg.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        clips = ClipRepo(this)
        val db = AppDb.get(this)
        scope.launch(Dispatchers.IO) {
            Suggest.load(this@EveryDayKeysService)
            db.words().personalList().forEach { Suggest.learn(it.word) }
            Bigrams.load(this@EveryDayKeysService)
        }
        scope.launch { SettingsRepo.flow(this@EveryDayKeysService).collect { prefs = it } }
        scope.launch {
            db.snippets().observe().collect { list ->
                snippetMap = list.filter { it.shortcut.isNotBlank() }.associate { it.shortcut to it.body }
            }
        }
        scope.launch {
            val days = SettingsRepo.flow(this@EveryDayKeysService).first().str("retention").toIntOrNull()
            if (days != null) clips.purgeOlderThanDays(days)
        }
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        scope.cancel()
        reg.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = ComposeView(this)
        window?.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        v.setContent { KeyboardRoot(this) }
        return v
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        info = editorInfo
        val cls = editorInfo.inputType and InputType.TYPE_MASK_CLASS
        val v = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        sensitive = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        smart = !sensitive && cls == InputType.TYPE_CLASS_TEXT &&
                v != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS && v != InputType.TYPE_TEXT_VARIATION_URI &&
                (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        resetTypingState()
        Launcher.sync(this, prefs.bool("hideLauncher"))
        closePanel()
        if (reopenClip) { reopenClip = false; openPanel(Panel.CLIP) }
        progress = null
        alts = null
        trans = null
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        mode = if (numeric && prefs.bool("autoNumPad")) Mode.NUMPAD else Mode.LETTERS
        shift = Shift.OFF
        updateAutoCap()
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        scope.launch(Dispatchers.IO) { Bigrams.save(this@EveryDayKeysService) }
    }

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f)
        hasSelection = c != d
        // the cursor moved and it was not our typing: old undo and spacing state no longer applies
        if (SystemClock.uptimeMillis() - ownEditAt > 500) resetTypingState()
        refreshSuggestions()
    }

    // ---- feedback ----
    fun feedback() {
        val p = prefs
        if (p.bool("vibrate")) vib?.vibrate(VibrationEffect.createOneShot(p.int("vibMs").toLong().coerceAtLeast(1), VibrationEffect.DEFAULT_AMPLITUDE))
        if (p.bool("sound")) am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, p.int("soundVol") / 100f)
    }

    // ---- typing ----
    fun onChar(raw: String) {
        if (searching) { query += if (shift != Shift.OFF) raw.uppercase() else raw; if (shift == Shift.ONCE) shift = Shift.OFF; return }
        val ic = currentInputConnection ?: return
        alts = null
        touch()
        lastCorr = null
        val c = if (shift != Shift.OFF) raw.uppercase() else raw
        // A space we added ourselves (after a symbol or a tapped suggestion) may sit right before the cursor.
        val fromSymbol = autoSpaced
        val fromSuggestion = suggestSpaced
        autoSpaced = false
        suggestSpaced = false
        if (smart && (fromSymbol || fromSuggestion) && c.length == 1) {
            val b = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val spaceBefore = b.length == 2 && b[1] == ' ' && !b[0].isWhitespace()
            if (spaceBefore) {
                if (c == " ") {
                    // the space is already there; a quick second tap still turns it into ". "
                    lastSpaceAt = SystemClock.uptimeMillis()
                    afterTyped(); return
                }
                val ch = c[0]
                val pullBack = (fromSuggestion && ch in ".,;:!?)") ||
                    (fromSymbol && ch in ".!?" && b[0] in ".!?)]}…") ||        // "..." and "?!" stay together
                    (fromSymbol && ch in ".,;:!?" && b[0] in ")]}")          // ")." instead of ") ."
                if (pullBack) ic.deleteSurroundingText(1, 0)
            }
        }
        val sep = c.length == 1 && (c[0].isWhitespace() || c[0] in ".,;:!?)")
        val corr = if (sep && smart) expandAndCorrect(ic) else null
        if (c == " " && smart && prefs.bool("doubleSpace")) {
            val now = SystemClock.uptimeMillis()
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (now - lastSpaceAt < 400 && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); lastSpaceAt = 0
                afterTyped(); return
            }
            lastSpaceAt = now
        }
        val out = if (wantsSpaceAfter(ic, c)) "$c " else c
        ic.commitText(out, 1)
        autoSpaced = out.length > c.length
        if (corr != null) lastCorr = Corr(corr.first, corr.second, out, autocorrect = true)
        if (shift == Shift.ONCE) shift = Shift.OFF
        afterTyped()
    }

    /** Opening brackets and quotes stay attached to the word that follows them. */
    private val noSpaceAfter = "([{<«“‘‹¿¡\"'`@#$&/\\_~^|"

    /** True when [c] is a symbol that should be followed by a space. */
    private fun wantsSpaceAfter(ic: InputConnection, c: String): Boolean {
        if (!smart || mode == Mode.NUMPAD || c.length != 1) return false
        val ch = c[0]
        if (ch.isLetterOrDigit() || ch.isWhitespace() || ch in noSpaceAfter) return false
        val around = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        // keep numbers like 3.14 or 1,000 together, and don't double up a space that is already there
        if ((ch == '.' || ch == ',') && around.isNotEmpty() && around[0].isDigit()) return false
        val next = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        return !(next.isNotEmpty() && next[0].isWhitespace())
    }

    /** Symbol keys: type the symbol, then a space after it. */
    fun typeSymbol(k: String) {
        val ic = currentInputConnection ?: return
        if (searching) { query += k; return }
        touch()
        lastCorr = null
        val fromSuggestion = suggestSpaced
        autoSpaced = false; suggestSpaced = false
        if (fromSuggestion && smart && k.length == 1 && k[0] in ".,;:!?)") {
            val b = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (b.length == 2 && b[1] == ' ' && !b[0].isWhitespace()) ic.deleteSurroundingText(1, 0)
        }
        val spaced = wantsSpaceAfter(ic, k)
        ic.commitText(if (spaced) "$k " else k, 1)
        autoSpaced = spaced
        afterTyped()
    }

    fun onAlt(s: String) {
        if (searching) { query += s; alts = null; return }
        touch(); resetTypingState()
        currentInputConnection?.commitText(s, 1)
        alts = null
        if (shift == Shift.ONCE) shift = Shift.OFF
        afterTyped()
    }

    fun commitText(t: String) { touch(); resetTypingState(); currentInputConnection?.commitText(t, 1); afterTyped() }

    private fun afterTyped() { updateAutoCap(); refreshSuggestions() }

    /** Returns (typed word, replacement) when autocorrect changed the word. */
    private fun expandAndCorrect(ic: InputConnection): Pair<String, String>? {
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return null
        val tok = before.takeLastWhile { !it.isWhitespace() }
        snippetMap[tok]?.let { ic.deleteSurroundingText(tok.length, 0); ic.commitText(it, 1); return null }
        // only a plain word right before the cursor is touched: not part of a URL, email or number, and not the left half of a word
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        val word = if (WordContext.insideWord(after)) "" else WordContext.currentWord(before)
        val keep = keepAsTyped
        keepAsTyped = null
        if (word.isEmpty()) return null
        val level = when (prefs.str("autocorrect")) { "mild" -> 1; "strong" -> 2; else -> 0 }
        var finalWord = word
        var result: Pair<String, String>? = null
        if (level > 0 && lang == "en" && !word.equals(keep, ignoreCase = true)) {
            Suggest.correct(word, level)?.let { ic.deleteSurroundingText(word.length, 0); ic.commitText(it, 1); finalWord = it; result = word to it }
        }
        if (lang == "en") {
            learnPair(before.dropLast(word.length), finalWord)
            if (result == null && prefs.bool("learnWords")) Suggest.noteTyped(finalWord)
        }
        return result
    }

    /** Remembers that [word] followed the word before it, for next-word suggestions. Only runs in normal text fields. */
    private fun learnPair(beforeWord: String, word: String) {
        if (!prefs.bool("nextWord") || word.length < 2) return
        val gap = beforeWord.takeLastWhile { it == ' ' }
        if (gap.isEmpty()) return
        val prev = beforeWord.dropLast(gap.length).takeLastWhile { it.isLetter() || it == '\'' }
        if (prev.isEmpty()) return
        Bigrams.learn(prev, word)
        if (++learned % 25 == 0) scope.launch(Dispatchers.IO) { Bigrams.save(this@EveryDayKeysService) }
    }

    fun onBackspace() {
        if (searching) { query = query.dropLast(1); return }
        touch()
        if (restoreTyped()) return
        autoSpaced = false; suggestSpaced = false
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterTyped()
    }

    /** If the last thing done was an autocorrect or a tapped suggestion, undo it and bring back the original text. */
    private fun restoreTyped(): Boolean {
        val lc = lastCorr ?: return false
        lastCorr = null
        val ic = currentInputConnection ?: return false
        // with text selected, Backspace must delete the selection, not undo a correction
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val want = lc.replaced + lc.tail
        if (ic.getTextBeforeCursor(want.length, 0)?.toString() != want) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(want.length, 0)
        ic.commitText(lc.original, 1)
        ic.endBatchEdit()
        autoSpaced = false; suggestSpaced = false
        keepAsTyped = lc.original
        // undoing an autocorrect teaches the keyboard that this word is fine; undoing a suggestion does not
        if (lc.autocorrect) Suggest.keep(lc.original)
        afterTyped()
        return true
    }

    fun onEnter() {
        if (searching) { searching = false; return }
        val ic = currentInputConnection ?: return
        touch()
        if (smart) expandAndCorrect(ic)
        resetTypingState()
        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnter = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        suggestions = emptyList()
        updateAutoCap()
    }

    fun onShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            Shift.OFF -> Shift.ONCE
            Shift.ONCE -> if (now - lastShiftAt < 400) Shift.LOCK else Shift.OFF
            Shift.LOCK -> Shift.OFF
        }
        lastShiftAt = now
    }

    fun onEquals() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val expr = before.takeLastWhile { it in "0123456789.,+-×÷*/%() " }.trim()
        val r = if (expr.any { it in "+-×÷*/%" }) Calc.eval(expr) else null
        if (r == null) ic.commitText("=", 1)
        else ic.commitText((if (before.endsWith(" ")) "" else " ") + "= " + Calc.format(r), 1)
    }

    private fun updateAutoCap() {
        if (!prefs.bool("autoCap") || shift == Shift.LOCK || !smart) return
        val i = info ?: return
        val caps = currentInputConnection?.getCursorCapsMode(i.inputType) ?: 0
        if (caps != 0 && shift == Shift.OFF) shift = Shift.ONCE
    }

    fun refreshSuggestions() {
        onlineJob?.cancel()
        if (!smart || !prefs.bool("suggestions") || lang != "en") { suggestions = emptyList(); return }
        val ic = currentInputConnection
        val n = prefs.int("suggestCount").coerceIn(2, 4)
        val b = ic?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val after = ic?.getTextAfterCursor(1, 0)?.toString().orEmpty()
        // no suggestions while the cursor is inside a word, since a tapped word could not replace all of it
        if (WordContext.insideWord(after)) { suggestions = emptyList(); return }
        val w = WordContext.currentWord(b)
        if (w.isEmpty()) {
            val prev = if (b.endsWith(" ")) WordContext.currentWord(b.trimEnd()) else ""
            var next = if (prefs.bool("nextWord") && prev.isNotEmpty()) Bigrams.next(prev, n) else emptyList()
            // at the start of a sentence the suggested word needs a capital too
            if (shift != Shift.OFF) next = next.map { it.replaceFirstChar { c -> c.uppercase() } }
            suggestions = next
            if (suggestions.isNotEmpty() && alts == null) toolsOpen = false
            return
        }
        val head = b.dropLast(w.length)
        val prev = if (head.endsWith(" ")) WordContext.currentWord(head.trimEnd()) else ""
        // words that usually follow the previous word are tried first, then the ones you use most
        val ctx = if (prefs.bool("contextRank") && prev.isNotEmpty()) Bigrams.next(prev, 40) else emptyList()
        val local = Suggest.suggest(w, ctx, n + 2, prefs.bool("typoSuggest"))
        suggestions = local.take(n)
        if (suggestions.isNotEmpty()) toolsOpen = false
        if (w.length >= 2 && prefs.bool("onlineSuggestions")) {
            onlineJob = scope.launch {
                kotlinx.coroutines.delay(120)
                val online = OnlineSuggest.fetch(w).map { Suggest.matchCaseOf(w, it) }.filter { it.lowercase() != w.lowercase() }
                if (online.isEmpty()) return@launch
                suggestions = (local.take(n - 1) + online + local.drop(n - 1)).distinctBy { it.lowercase() }.take(n)
            }
        }
    }

    fun applySuggestion(s: String) {
        val ic = currentInputConnection ?: return
        touch()
        val b = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        val w = WordContext.currentWord(b)
        // add a space unless one (or punctuation) already follows the cursor
        val space = after.isEmpty() || !(after[0].isWhitespace() || after[0] in ".,;:!?)")
        val tail = if (space) " " else ""
        ic.beginBatchEdit()
        if (w.isNotEmpty()) ic.deleteSurroundingText(w.length, 0)
        ic.commitText(s + tail, 1)
        ic.endBatchEdit()
        if (lang == "en") {
            learnPair(b.dropLast(w.length), s)
            if (prefs.bool("learnWords")) Suggest.noteTyped(s)
        }
        resetTypingState()
        afterTyped()
        lastCorr = if (w.isNotEmpty()) Corr(w, s, tail, autocorrect = false) else null
        suggestSpaced = space
    }

    fun nextLang() {
        if (langs.size > 1) langIdx = (langIdx + 1) % langs.size
        else (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
    }

    // ---- editing tools ----
    fun moveCursor(dir: Int) = sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    fun sendKey(code: Int) = sendDownUpKeyEvents(code)
    fun selectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        showNotice(R.drawable.ic_select_all, "Selected all", "Copy") { copy() }
    }
    fun copy() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) { showNotice(R.drawable.ic_info, "Select some text first"); return }
        ic.performContextMenuAction(android.R.id.copy)
        showNotice(R.drawable.ic_copy, "Copied", "Paste") { paste() }
    }
    fun cut() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) { showNotice(R.drawable.ic_info, "Select some text first"); return }
        ic.performContextMenuAction(android.R.id.cut)
        showNotice(R.drawable.ic_cut, "Cut", "Paste") { paste() }
    }

    /** Paste the newest history item through the chunked path, so size never matters. */
    fun paste() {
        if (!clipUnlocked) { requestUnlock(); return }
        scope.launch {
            val latest = withContext(Dispatchers.IO) { AppDb.get(this@EveryDayKeysService).clips().latest() }
            if (latest != null) pasteClips(listOf(latest)) else currentInputConnection?.performContextMenuAction(android.R.id.paste)
        }
    }

    private fun ctrl(code: Int, shiftOn: Boolean) {
        val ic = currentInputConnection ?: return
        val meta = KeyEvent.META_CTRL_ON or (if (shiftOn) KeyEvent.META_SHIFT_ON else 0)
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }
    fun undo() { ctrl(KeyEvent.KEYCODE_Z, false); showNotice(R.drawable.ic_undo, "Undo", "Redo") { redo() } }
    fun redo() { ctrl(KeyEvent.KEYCODE_Z, true); showNotice(R.drawable.ic_redo, "Redo", "Undo") { undo() } }

    fun pasteClips(list: List<Clip>) {
        if (list.isEmpty()) return
        if (list.any { it.id == pendingClip?.id }) pendingClip = null
        closePanel()
        pasteJob?.cancel()
        val total = list.sumOf { it.length } + list.size - 1
        val (chunk, delayMs) = when (prefs.str("pasteSpeed")) { "fast" -> 100_000 to 5L; "safe" -> 50_000 to 60L; else -> 64_000 to 20L }
        val show = prefs.bool("showProgress") && total > chunk
        pasteJob = scope.launch {
            if (show) progress = 0f
            try {
                ChunkedPaste.run(JoinReader(list.map { c -> { c.open() } }), total, chunk, delayMs, { currentInputConnection }) { p -> if (show) progress = p }
            } finally { progress = null }
            showNotice(R.drawable.ic_clipboard, if (total > 200) "Pasted %,d characters".format(total) else "Pasted")
            afterTyped()
        }
    }

    fun cancelPaste() { pasteJob?.cancel(); progress = null }

    /** Writes a setting from the quick settings panel; the keyboard picks it up from the settings flow straight away. */
    fun setPref(key: String, value: String) { scope.launch { SettingsRepo.set(this@EveryDayKeysService, key, value) } }

    fun commitEmoji(e: String) {
        touch(); resetTypingState()
        currentInputConnection?.commitText(e, 1)
        val recents = (listOf(e) + prefs.str("recentEmoji").split(" ").filter { it.isNotBlank() }).distinct().take(24)
        scope.launch { SettingsRepo.set(this@EveryDayKeysService, "recentEmoji", recents.joinToString(" ")) }
    }

    // ---- panels / launching ----
    fun openPanel(p: Panel) {
        panel = p; searching = false; query = ""
        if (p == Panel.CONVERT) captureConvInput()
    }

    /** Picks the number to convert: the selection, or the number just before the cursor. */
    private fun captureConvInput() {
        val ic = currentInputConnection ?: run { convText = ""; convLen = 0; return }
        val sel = ic.getSelectedText(0)?.toString().orEmpty().trim()
        if (sel.replace(",", "").toDoubleOrNull() != null) { convText = sel; convLen = 0; return }
        val digits = ic.getTextBeforeCursor(40, 0)?.toString().orEmpty().takeLastWhile { it.isDigit() || it == '.' || it == ',' || it == '-' }
        convText = digits
        convLen = digits.length
    }

    fun convStep(d: Int) {
        val n = Units.cats.size
        convCat = (convCat + d + n) % n
        convFrom = 0
        convTo = 1
    }

    /** Replaces the number with the converted value and its unit, for example "5 km" becomes "3.10685596 mi". */
    fun convInsert() {
        val cat = Units.cats[convCat]
        val v = convText.replace(",", "").toDoubleOrNull() ?: return
        val to = cat.units[convTo.coerceIn(0, cat.units.lastIndex)]
        val from = cat.units[convFrom.coerceIn(0, cat.units.lastIndex)]
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (convLen > 0) ic.deleteSurroundingText(convLen, 0)
        ic.commitText(Calc.format(Units.convert(cat, v, from, to)) + " " + to.id, 1)
        ic.endBatchEdit()
        closePanel()
        afterTyped()
    }
    fun closePanel() { panel = Panel.NONE; searching = false; query = "" }
    /** A tool icon opens its panel, and tapping the same icon again closes it, since panels have no back arrow. */
    fun togglePanel(p: Panel) { if (panel == p) closePanel() else openPanel(p) }

    private fun launch(i: Intent) = startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun openSettings() = launch(Intent(this, SettingsActivity::class.java))
    fun startVoice() = launch(Intent(this, VoiceActivity::class.java))
    /** Translate the selection, or else the text typed since the last line break, and offer the result in the bar. */
    fun translate(forceTarget: String? = null) {
        val ic = currentInputConnection ?: return
        if (sensitive) { trans = Trans(message = "Translation is disabled in password fields"); return }
        val sel = ic.getSelectedText(0)?.toString().orEmpty()
        var before = ""
        if (sel.isEmpty()) {
            before = ic.getTextBeforeCursor(1000, 0)?.toString().orEmpty().takeLastWhile { it != '\n' }
            // a long run of text was cut mid-word at the 1000 character limit: start at the next whole word
            if (before.length >= 1000) before = before.substring(before.indexOfFirst { it.isWhitespace() }.let { if (it < 0) 0 else it })
        }
        val raw = sel.ifEmpty { before }
        val src = raw.trim()
        if (src.isEmpty()) { trans = Trans(message = "Select or type some text first"); return }
        val lead = raw.takeWhile { it.isWhitespace() }
        val trail = if (src.isEmpty()) "" else raw.takeLastWhile { it.isWhitespace() }

        val p = prefs
        val detected = Translator.detectSource(src)
        val configured = p.str("translateTarget")
        var target = forceTarget ?: configured
        if (forceTarget == null && detected == target) {
            // text is already in the chosen language: translate into the first other keyboard language instead
            target = (p.str("languages").split(",") + Translator.languages.map { it.first }).firstOrNull { it != detected && it.isNotBlank() } ?: "en"
        }
        if (detected == target) { trans = Trans(message = "Already in ${Translator.nameOf(target)}", target = target, raw = raw, selected = sel.isNotEmpty()); return }

        trans = Trans(message = "Translating…", target = target, raw = raw, selected = sel.isNotEmpty())
        transJob?.cancel()
        transJob = scope.launch {
            val next = try {
                val r = Translator.translate(src, target, detected, p.str("translateProvider"), p.str("googleApiKey"), p.str("deeplApiKey"), p.str("translateEmail"))
                Trans(result = lead + r.trim() + trail, target = target, raw = raw, selected = sel.isNotEmpty())
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Translator.Failure) { Trans(message = e.message, target = target, raw = raw, selected = sel.isNotEmpty()) }
            catch (e: Exception) { Trans(message = "Translation failed (${e.javaClass.simpleName})", target = target, raw = raw, selected = sel.isNotEmpty()) }
            trans = next
        }
    }

    /** Tapping the language chip: translate the same text into the next language and remember the choice. */
    fun nextTranslateTarget() {
        val cur = trans?.target ?: prefs.str("translateTarget")
        val known = Translator.languages.map { it.first }
        val mine = prefs.str("languages").split(",").filter { it in known }
        val cycle = if (mine.size >= 2) mine else known
        val next = cycle[(cycle.indexOf(cur) + 1) % cycle.size]
        scope.launch { SettingsRepo.set(this@EveryDayKeysService, "translateTarget", next) }
        translate(next)
    }

    fun applyTranslation() {
        val t = trans ?: return
        val res = t.result ?: return
        val ic = currentInputConnection ?: return
        val unchanged = if (t.selected) ic.getSelectedText(0)?.toString() == t.raw
            else ic.getTextBeforeCursor(t.raw.length, 0)?.toString() == t.raw
        if (!unchanged) { trans = Trans(message = "The text changed. Tap Translate again.", target = t.target); return }
        ic.beginBatchEdit()
        if (!t.selected) ic.deleteSurroundingText(t.raw.length, 0)
        ic.commitText(res, 1)
        ic.endBatchEdit()
        trans = null
        afterTyped()
    }
    /** Cycle Current, Design 1, 2 and 3 so the layout can be compared without opening settings. */
    fun cycleLayoutStyle() {
        val order = listOf("current", "raised", "flat", "pro")
        val next = order[(order.indexOf(prefs.str("layoutStyle")) + 1) % order.size]
        scope.launch { SettingsRepo.set(this@EveryDayKeysService, "layoutStyle", next) }
    }

    fun dismissTranslation() { transJob?.cancel(); trans = null }
}
