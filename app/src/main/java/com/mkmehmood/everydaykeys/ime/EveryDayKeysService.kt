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
import com.mkmehmood.everydaykeys.ui.VoiceActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

enum class Mode { LETTERS, NUMPAD, SYM1, SYM2 }
enum class Panel { NONE, CLIP, SNIPPETS, EMOJI, CURSOR }
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
    var alts by mutableStateOf<List<String>?>(null)
    var progress by mutableStateOf<Float?>(null)
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")

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
            val t = clip.getItemAt(0)?.coerceToText(this@EveryDayKeysService)?.toString() ?: return
            scope.launch { clips.add(t) }
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
        closePanel()
        progress = null
        alts = null
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        mode = if (numeric && prefs.bool("autoNumPad")) Mode.NUMPAD else Mode.LETTERS
        shift = Shift.OFF
        updateAutoCap()
        refreshSuggestions()
    }

    override fun onUpdateSelection(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) {
        super.onUpdateSelection(a, b, c, d, e, f)
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
        val c = if (shift != Shift.OFF) raw.uppercase() else raw
        val sep = c.length == 1 && (c[0].isWhitespace() || c[0] in ".,;:!?)")
        if (sep && smart) expandAndCorrect(ic)
        if (c == " " && smart && prefs.bool("doubleSpace")) {
            val now = SystemClock.uptimeMillis()
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (now - lastSpaceAt < 400 && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); lastSpaceAt = 0
                afterTyped(); return
            }
            lastSpaceAt = now
        }
        ic.commitText(c, 1)
        if (shift == Shift.ONCE) shift = Shift.OFF
        afterTyped()
    }

    fun onAlt(s: String) {
        if (searching) { query += s; alts = null; return }
        currentInputConnection?.commitText(s, 1)
        alts = null
        if (shift == Shift.ONCE) shift = Shift.OFF
        afterTyped()
    }

    fun commitText(t: String) { currentInputConnection?.commitText(t, 1); afterTyped() }

    private fun afterTyped() { updateAutoCap(); refreshSuggestions() }

    private fun expandAndCorrect(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        val tok = before.takeLastWhile { !it.isWhitespace() }
        snippetMap[tok]?.let { ic.deleteSurroundingText(tok.length, 0); ic.commitText(it, 1); return }
        val word = before.takeLastWhile { it.isLetter() || it == '\'' }
        val level = when (prefs.str("autocorrect")) { "mild" -> 1; "strong" -> 2; else -> 0 }
        if (level > 0 && lang == "en" && word.isNotEmpty()) {
            Suggest.correct(word, level)?.let { ic.deleteSurroundingText(word.length, 0); ic.commitText(it, 1) }
        }
    }

    fun onBackspace() {
        if (searching) { query = query.dropLast(1); return }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterTyped()
    }

    fun onEnter() {
        if (searching) { searching = false; return }
        val ic = currentInputConnection ?: return
        if (smart) expandAndCorrect(ic)
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
        if (!smart || !prefs.bool("suggestions") || lang != "en") { suggestions = emptyList(); return }
        val b = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val w = b.takeLastWhile { it.isLetter() || it == '\'' }
        suggestions = if (w.isEmpty()) emptyList() else Suggest.complete(w)
    }

    fun applySuggestion(s: String) {
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        val w = b.takeLastWhile { it.isLetter() || it == '\'' }
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        afterTyped()
    }

    fun nextLang() {
        if (langs.size > 1) langIdx = (langIdx + 1) % langs.size
        else (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
    }

    // ---- editing tools ----
    fun moveCursor(dir: Int) = sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    fun sendKey(code: Int) = sendDownUpKeyEvents(code)
    fun selectAll() { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
    fun copy() { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
    fun cut() { currentInputConnection?.performContextMenuAction(android.R.id.cut) }

    /** Paste the newest history item through the chunked path, so size never matters. */
    fun paste() {
        scope.launch {
            val latest = withContext(Dispatchers.IO) { AppDb.get(this@EveryDayKeysService).clips().all().maxByOrNull { it.createdAt } }
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
    fun undo() = ctrl(KeyEvent.KEYCODE_Z, false)
    fun redo() = ctrl(KeyEvent.KEYCODE_Z, true)

    fun pasteClips(list: List<Clip>) {
        if (list.isEmpty()) return
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
            afterTyped()
        }
    }

    fun cancelPaste() { pasteJob?.cancel(); progress = null }

    fun commitEmoji(e: String) {
        currentInputConnection?.commitText(e, 1)
        val recents = (listOf(e) + prefs.str("recentEmoji").split(" ").filter { it.isNotBlank() }).distinct().take(24)
        scope.launch { SettingsRepo.set(this@EveryDayKeysService, "recentEmoji", recents.joinToString(" ")) }
    }

    // ---- panels / launching ----
    fun openPanel(p: Panel) { panel = p; searching = false; query = "" }
    fun closePanel() { panel = Panel.NONE; searching = false; query = "" }

    private fun launch(i: Intent) = startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun openSettings() = launch(Intent(this, SettingsActivity::class.java))
    fun startVoice() = launch(Intent(this, VoiceActivity::class.java))
    fun translate() {
        val sel = currentInputConnection?.getSelectedText(0)?.toString().orEmpty().take(2000)
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com/?sl=auto&op=translate&text=" + Uri.encode(sel))))
    }
}
