package com.mkmehmood.everydaykeys

import android.app.SearchManager
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.*

class EverydayKeysService : InputMethodService() {
    companion object { var instance: EverydayKeysService? = null }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var view: KeyboardView
    @Volatile var s = Prefs()
    var snippets: List<Snippet> = emptyList()
    var sensitive = false
    var inputType = 0
    private var pasteJob: Job? = null
    private lateinit var cm: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClip() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
        scope.launch { Store.flow(this@EverydayKeysService).collect { s = it; if (::view.isInitialized) view.render() } }
        scope.launch { Graph.db(this@EverydayKeysService).snippets().flow().collect { snippets = it } }
        scope.launch { Dict.load(this@EverydayKeysService) }
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        scope.cancel(); instance = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        view = KeyboardView(this, this)
        view.render()
        return view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputType = info.inputType
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val v = inputType and InputType.TYPE_MASK_VARIATION
        sensitive = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
                (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        view.startInput(cls)
        if (s.retention > 0) scope.launch { ClipStore.purge(this@EverydayKeysService, s.retention) }
    }

    fun refreshView() { if (::view.isInitialized) view.render() }

    // ---------- clipboard capture ----------
    private fun onClip() {
        if (!s.clipOn) return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        if (s.skipSensitive && (sensitive || clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)) return
        val t = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        scope.launch {
            ClipStore.add(this@EverydayKeysService, t)
            if (::view.isInitialized && view.mode == KeyboardView.Mode.CLIP) view.loadClips()
        }
    }

    // ---------- typing ----------
    fun type(t: String) {
        if (view.searching) { view.query += t; view.loadClips(); return }
        val ic = currentInputConnection ?: return
        if (t == " " || (t.length == 1 && t[0] in ".,;:!?)")) boundary(ic, t) else ic.commitText(t, 1)
        view.afterType()
    }

    private fun boundary(ic: InputConnection, t: String) {
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val word = before.takeLastWhile { it.isLetter() || it == '\'' }
        if (t == " ") {
            val tok = before.takeLastWhile { !it.isWhitespace() }
            if (tok.length > 1 && tok.startsWith(";")) {
                val sn = snippets.firstOrNull { it.shortcut == tok }
                if (sn != null) { ic.deleteSurroundingText(tok.length, 0); pasteLarge(sn.body); return }
            }
            if (s.dblSpace && before.length >= 2 && before.last() == ' ' && before[before.length - 2].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); return
            }
        }
        if (word.isNotEmpty() && !sensitive) {
            val fix = if (s.autocorrect > 0) Dict.correct(word, s.autocorrect) else null
            if (fix != null) { ic.deleteSurroundingText(word.length, 0); ic.commitText(fix, 1) } else Dict.learn(this, scope, word)
        }
        ic.commitText(t, 1)
    }

    fun backspace() {
        if (view.searching) { view.query = view.query.dropLast(1); view.loadClips(); return }
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) else ic.commitText("", 1)
        view.afterType()
    }

    fun enter() {
        if (view.searching) { view.searching = false; view.render(); return }
        val ic = currentInputConnection ?: return
        val a = currentInputEditorInfo?.imeOptions ?: 0
        val act = a and EditorInfo.IME_MASK_ACTION
        if (act != EditorInfo.IME_ACTION_NONE && act != EditorInfo.IME_ACTION_UNSPECIFIED && (a and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
            ic.performEditorAction(act) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        view.afterType()
    }

    fun moveCursor(dir: Int) = sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    fun menu(id: Int) { currentInputConnection?.performContextMenuAction(id) }

    fun ctrlKey(code: Int, withShift: Boolean = false) {
        val ic = currentInputConnection ?: return
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or (if (withShift) KeyEvent.META_SHIFT_ON else 0)
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    fun commitExternal(text: String) { currentInputConnection?.commitText(text, 1) }
    fun selected(): String = currentInputConnection?.getSelectedText(0)?.toString() ?: ""

    fun currentWord(): String = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString()?.takeLastWhile { it.isLetter() || it == '\'' } ?: ""

    fun acceptSuggestion(w: String) {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(currentWord().length, 0); ic.commitText("$w ", 1); view.afterType()
    }

    fun calc() {
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""
        val m = Regex("[0-9+\\-×÷.%()*/ ]+$").find(b)?.value?.trim().orEmpty()
        val r = if (m.isNotEmpty()) Calc.eval(m) else null
        ic.commitText(if (r != null) "=$r" else "=", 1)
    }

    // ---------- large paste ----------
    /** Sends big text in 50-100 KB slices so a single binder call never overflows or truncates. */
    fun pasteLarge(text: String) {
        val chunk = when (s.chunkSpeed) { 0 -> 100_000; 1 -> 75_000; else -> 50_000 }
        val gap = when (s.chunkSpeed) { 0 -> 0L; 1 -> 20L; else -> 60L }
        if (text.length <= chunk) { currentInputConnection?.commitText(text, 1); view.afterType(); return }
        pasteJob?.cancel()
        pasteJob = scope.launch {
            var i = 0
            view.progress(0)
            while (i < text.length) {
                var end = minOf(i + chunk, text.length)
                if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
                val ic = currentInputConnection ?: break
                ic.beginBatchEdit(); ic.commitText(text.substring(i, end), 1); ic.endBatchEdit()
                i = end
                view.progress((i * 100L / text.length).toInt())
                delay(gap)
            }
            view.progress(-1)
        }
    }

    fun pasteClip(c: Clip) { scope.launch { pasteLarge(ClipStore.read(c)) } }
    fun pasteMany(list: List<Clip>) { scope.launch { val parts = ArrayList<String>(); for (c in list) parts.add(ClipStore.read(c)); pasteLarge(parts.joinToString("\n")) } }
    fun pasteLatest() { scope.launch { Graph.db(this@EverydayKeysService).clips().latest()?.let { pasteClip(it) } } }

    // ---------- shortcuts ----------
    fun voice() = startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun unlock() = startActivity(Intent(this, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun openSettings() = startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun pickKeyboard() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

    fun search() {
        try { startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, selected()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    fun translate() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, selected())
        try { startActivity(Intent(i).setPackage("com.google.android.apps.translate").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { try { startActivity(Intent.createChooser(i, "Translate").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
    }

    @Suppress("DEPRECATION")
    fun feedback() {
        if (s.vibrate) {
            val vb = getSystemService(Vibrator::class.java)
            val ms = s.vibMs.toLong().coerceAtLeast(1)
            if (Build.VERSION.SDK_INT >= 26) vb?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else vb?.vibrate(ms)
        }
        if (s.sound) (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, s.volume / 100f)
    }
}
