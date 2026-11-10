package com.mkmehmood.everydaykeys.ime

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.datastore.preferences.core.emptyPreferences
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.Clip
import com.mkmehmood.everydaykeys.data.ClipStore
import com.mkmehmood.everydaykeys.data.Db
import com.mkmehmood.everydaykeys.data.P
import com.mkmehmood.everydaykeys.data.Snap
import com.mkmehmood.everydaykeys.data.setPref
import com.mkmehmood.everydaykeys.data.snapFlow
import com.mkmehmood.everydaykeys.data.snapNow
import com.mkmehmood.everydaykeys.smart.Calc
import com.mkmehmood.everydaykeys.smart.Dict
import com.mkmehmood.everydaykeys.smart.SuggestionEngine
import com.mkmehmood.everydaykeys.smart.isWordChar
import com.mkmehmood.everydaykeys.ui.LockActivity
import com.mkmehmood.everydaykeys.ui.SettingsActivity
import com.mkmehmood.everydaykeys.ui.VoiceActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class SearchState(var q: String, val onChange: (String) -> Unit)

class EverydayKeysService : InputMethodService() {
    companion object {
        @Volatile var instance: EverydayKeysService? = null
        @Volatile var unlockedUntil = 0L
    }

    private val rootJob = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.Main + rootJob)
    lateinit var db: Db
    lateinit var store: ClipStore
    private lateinit var engine: SuggestionEngine
    private lateinit var clipboard: ClipboardManager

    var snap: Snap = Snap(emptyPreferences())
    var view: KeyboardView? = null
        private set
    private var inner: View? = null
    private var floatingMode = false

    private var ei: EditorInfo? = null
    private var sensitive = false
    private var shift = 0
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    private var pendingCorr: Pair<String, String>? = null
    private var lastCorrection: Pair<String, String>? = null
    private var lastCorrectionAt = 0L
    private var refreshJob: Job? = null
    private var pasteJob: Job? = null
    private var lastPaste: Pair<Int, Long>? = null
    private var swapIdx = 0
    var search: SearchState? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Dict.load(this)
        db = Db.get(this)
        store = ClipStore(this)
        engine = SuggestionEngine(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        snap = snapNow()
        clipboard.addPrimaryClipChangedListener { onClipChanged() }
        scope.launch {
            var first = true
            snapFlow().collect {
                snap = it
                if (first) { first = false; return@collect }
                setInputView(buildInput())
            }
        }
    }

    override fun onDestroy() {
        instance = null
        rootJob.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View = buildInput()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildInput(): View {
        val s = snap
        val kv = KeyboardView(this)
        kv.applySettings(s)
        view = kv
        val dm = resources.displayMetrics
        val root = FrameLayout(this)
        val pal = Palettes.from(this, s)
        floatingMode = s[P.floating]
        val oneHanded = s[P.oneHanded]
        when {
            floatingMode -> {
                root.minimumHeight = (dm.heightPixels * 0.55f).toInt()
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val handle = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
                handle.addView(ImageView(this).apply { setImageResource(R.drawable.ic_float); setColorFilter(pal.sub); contentDescription = "Drag to move keyboard" },
                    FrameLayout.LayoutParams(dpi(22f), dpi(22f), Gravity.CENTER))
                box.addView(handle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(30f)))
                box.addView(kv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                var lx = 0f; var ly = 0f
                handle.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { lx = e.rawX; ly = e.rawY }
                        MotionEvent.ACTION_MOVE -> {
                            val maxUp = -(root.height - box.height).toFloat().coerceAtLeast(0f)
                            val maxX = (dm.widthPixels - box.width) / 2f
                            box.translationX = (box.translationX + e.rawX - lx).coerceIn(-maxX, maxX)
                            box.translationY = (box.translationY + e.rawY - ly).coerceIn(maxUp, 0f)
                            lx = e.rawX; ly = e.rawY
                        }
                    }
                    true
                }
                root.addView(box, FrameLayout.LayoutParams((dm.widthPixels * 0.82f).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
                inner = box
            }
            oneHanded != "off" -> {
                root.setBackgroundColor(pal.bg)
                val left = oneHanded == "left"
                root.addView(kv, FrameLayout.LayoutParams((dm.widthPixels * 0.80f).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or (if (left) Gravity.START else Gravity.END)))
                val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                side.addView(iconBtn(R.drawable.ic_one_hand, pal.text, "Full width keyboard", 44) { scope.launch { setPref(P.oneHanded, "off") } })
                side.addView(iconBtn(R.drawable.ic_swap, pal.text, "Move keyboard to the other side", 44) { scope.launch { setPref(P.oneHanded, if (left) "right" else "left") } })
                root.addView(side, FrameLayout.LayoutParams((dm.widthPixels * 0.20f).toInt(), FrameLayout.LayoutParams.MATCH_PARENT,
                    if (left) Gravity.END else Gravity.START))
                inner = null
            }
            else -> {
                root.setBackgroundColor(pal.bg)
                root.addView(kv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
                inner = null
            }
        }
        return root
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val box = inner
        if (floatingMode && box != null && box.isShown) {
            val top = window?.window?.decorView?.height ?: return
            outInsets.contentTopInsets = top
            outInsets.visibleTopInsets = top
            val loc = IntArray(2)
            box.getLocationInWindow(loc)
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(loc[0], loc[1], loc[0] + box.width, loc[1] + box.height)
        }
    }

    // ---------- lifecycle ----------
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ei = info
        sensitive = isSensitive(info.inputType)
        shift = 0
        lastCorrection = null; pendingCorr = null
        endSearch()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val kv = view ?: return
        kv.phoneField = cls == InputType.TYPE_CLASS_PHONE
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        kv.setMode(if (numeric && snap[P.autoNumpad]) KeyboardView.Mode.NUMPAD else KeyboardView.Mode.LETTERS)
        kv.setEnterIcon(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        kv.setShift(0)
        updateAutoCap()
        refreshSuggestions()
        onClipChanged()
        scope.launch { store.cleanup(snap[P.clipRetention]) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        sensitive = false
        endSearch()
        view?.hidePanel()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)
        refreshSuggestions()
    }

    private fun isSensitive(t: Int): Boolean {
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        return (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    // ---------- clipboard history ----------
    private fun onClipChanged() {
        if (!snap[P.clipEnabled]) return
        try {
            val clip = clipboard.primaryClip ?: return
            if (snap[P.clipSkipSensitive]) {
                if (sensitive) return
                if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
            }
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            scope.launch { store.add(text) }
        } catch (_: Exception) { }
    }

    fun clipLocked() = snap[P.clipBiometric] && SystemClock.elapsedRealtime() > unlockedUntil

    fun requestUnlock() {
        startActivity(Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun pasteClip(c: Clip) = pasteMerged(listOf(c))

    fun pasteMerged(list: List<Clip>) {
        pasteJob?.cancel()
        pasteJob = scope.launch {
            val text = list.map { store.read(it) }.joinToString("\n")
            val chunk = when (snap[P.pasteSpeed]) { "fast" -> 100_000; "safe" -> 50_000; else -> 75_000 }
            val gap = when (snap[P.pasteSpeed]) { "fast" -> 0L; "safe" -> 60L; else -> 20L }
            val bar = text.length > chunk && snap[P.pasteProgress]
            currentInputConnection?.finishComposingText()
            try {
                ChunkedPaste.paste(text, chunk, gap, { currentInputConnection }) { if (bar) view?.showProgress(it) }
                lastPaste = text.length to SystemClock.uptimeMillis()
            } finally {
                view?.showProgress(null)
            }
            refreshSuggestions()
        }
    }

    fun swapLastPaste() {
        scope.launch {
            val recent = db.clips().recent(5)
            if (recent.size < 2) return@launch
            val lp = lastPaste
            if (lp != null && SystemClock.uptimeMillis() - lp.second < 60_000) {
                swapIdx = (swapIdx + 1) % recent.size
                currentInputConnection?.deleteSurroundingText(lp.first, 0)
            } else swapIdx = 0
            pasteMerged(listOf(recent[swapIdx]))
        }
    }

    // ---------- search inside panels ----------
    fun beginSearch(initial: String, onChange: (String) -> Unit) {
        search = SearchState(initial, onChange)
        view?.setSearching(true)
    }

    fun endSearch() {
        if (search == null) return
        search = null
        view?.setSearching(false)
    }

    fun showLetters() { view?.setMode(KeyboardView.Mode.LETTERS) }

    // ---------- key handling ----------
    fun feedback() {
        if (snap[P.vibrate]) {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val amp = (snap[P.vibIntensity] * 255).toInt().coerceIn(1, 255)
            vib?.vibrate(VibrationEffect.createOneShot(12, amp))
        }
        if (snap[P.sound]) {
            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, snap[P.soundVolume])
        }
    }

    fun onKey(k: Key, text: String) {
        val s = search
        if (s != null && handleSearchKey(s, k, text)) return
        when (k.act) {
            Act.NONE -> typeText(text)
            Act.SHIFT -> tapShift()
            Act.DELETE -> doDelete()
            Act.ENTER -> doEnter()
            Act.SPACE -> doSpace()
            Act.NUMPAD -> view?.setMode(KeyboardView.Mode.NUMPAD)
            Act.LETTERS -> view?.setMode(KeyboardView.Mode.LETTERS)
            Act.SYMBOLS -> view?.setMode(KeyboardView.Mode.SYMBOLS1)
            Act.SYMBOLS2 -> view?.setMode(if (view?.mode == KeyboardView.Mode.SYMBOLS1) KeyboardView.Mode.SYMBOLS2 else KeyboardView.Mode.SYMBOLS1)
            Act.LANG -> view?.nextLang()
            Act.EMOJI -> view?.showEmoji()
            Act.CONVERTER -> view?.showConvert()
        }
    }

    private fun handleSearchKey(s: SearchState, k: Key, text: String): Boolean {
        when (k.act) {
            Act.DELETE -> s.q = s.q.dropLast(1)
            Act.SPACE -> s.q += " "
            Act.ENTER -> { endSearch(); return true }
            Act.SHIFT -> { tapShift(); return true }
            Act.NONE -> { s.q += text; if (shift == 1) setShift(0) }
            Act.LANG -> return false
            else -> return true
        }
        s.onChange(s.q)
        return true
    }

    private fun setShift(n: Int) { shift = n; view?.setShift(n) }

    private fun tapShift() {
        val now = SystemClock.uptimeMillis()
        when {
            shift == 2 -> setShift(0)
            shift == 1 && now - lastShiftTap < 350 -> setShift(2)
            shift == 1 -> setShift(0)
            else -> setShift(1)
        }
        lastShiftTap = now
    }

    private fun updateAutoCap() {
        if (!snap[P.autoCap] || shift == 2 || sensitive) return
        if (view?.mode != KeyboardView.Mode.LETTERS || view?.currentLayout?.cased != true) return
        val info = ei ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        if (caps != 0 && shift == 0) setShift(1)
    }

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        if (text == "=" && view?.mode == KeyboardView.Mode.NUMPAD) {
            val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: ""
            ic.commitText("=", 1)
            Calc.extract(before)?.let { expr -> Calc.eval(expr)?.let { ic.commitText(" " + Calc.format(it), 1) } }
            return
        }
        val isSep = text.length == 1 && !isWordChar(text[0]) && !text[0].isDigit()
        if (isSep && !sensitive) applyPendingCorrection(ic)
        ic.commitText(text, 1)
        lastSpaceTime = 0
        if (shift == 1) setShift(0)
        updateAutoCap()
        refreshSuggestions()
    }

    fun insertText(s: String) {
        currentInputConnection?.commitText(s, 1)
        lastSpaceTime = 0
        refreshSuggestions()
    }

    fun commitVoice(text: String) { currentInputConnection?.commitText("$text ", 1) }

    private fun applyPendingCorrection(ic: InputConnection): Boolean {
        val pc = pendingCorr ?: return false
        val before = ic.getTextBeforeCursor(pc.first.length, 0)?.toString()
        if (before != pc.first) return false
        ic.deleteSurroundingText(pc.first.length, 0)
        ic.commitText(pc.second, 1)
        lastCorrection = pc; lastCorrectionAt = SystemClock.uptimeMillis()
        pendingCorr = null
        return true
    }

    private fun parseContext(before: String): Pair<String, String?> {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        val prefix = before.substring(i)
        var j = i
        while (j > 0 && !isWordChar(before[j - 1])) j--
        var k = j
        while (k > 0 && isWordChar(before[k - 1])) k--
        return prefix to before.substring(k, j).ifEmpty { null }
    }

    private fun doSpace() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val token = Regex(";\\S+$").find(before)?.value
        if (token != null && !sensitive) {
            scope.launch {
                val sn = db.snippets().byShortcut(token)
                val c = currentInputConnection ?: return@launch
                if (sn != null) { c.deleteSurroundingText(token.length, 0); c.commitText(sn.body, 1) } else c.commitText(" ", 1)
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        if (snap[P.doubleSpace] && now - lastSpaceTime < 500 && before.length >= 2 && before.endsWith(" ") && before[before.length - 2].isLetterOrDigit()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTime = 0
            updateAutoCap(); refreshSuggestions()
            return
        }
        var corrected = false
        if (!sensitive) {
            val (prefix, prev) = parseContext(before)
            corrected = applyPendingCorrection(ic)
            if (!corrected && prefix.length > 1 && view?.mode == KeyboardView.Mode.LETTERS) {
                scope.launch { engine.learn(prefix, prev) }
            }
        }
        ic.commitText(" ", 1)
        lastSpaceTime = now
        if (shift == 1) setShift(0)
        updateAutoCap(); refreshSuggestions()
    }

    private fun doDelete() {
        val ic = currentInputConnection ?: return
        val lc = lastCorrection
        if (lc != null && SystemClock.uptimeMillis() - lastCorrectionAt < 8000) {
            val before = ic.getTextBeforeCursor(lc.second.length + 1, 0)?.toString()
            if (before != null && before.length == lc.second.length + 1 && before.startsWith(lc.second)) {
                ic.deleteSurroundingText(before.length, 0)
                ic.commitText(lc.first, 1)
                lastCorrection = null
                refreshSuggestions()
                return
            }
        }
        lastCorrection = null
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refreshSuggestions()
    }

    private fun doEnter() {
        val info = ei
        val ic = currentInputConnection ?: return
        if (info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            val a = info.imeOptions and EditorInfo.IME_MASK_ACTION
            if (a == EditorInfo.IME_ACTION_GO || a == EditorInfo.IME_ACTION_SEARCH || a == EditorInfo.IME_ACTION_SEND ||
                a == EditorInfo.IME_ACTION_NEXT || a == EditorInfo.IME_ACTION_DONE) {
                ic.performEditorAction(a); return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        if (!sensitive) updateAutoCap()
        refreshSuggestions()
    }

    fun onSpaceDrag(steps: Int) {
        repeat(abs(steps)) { sendDownUpKeyEvents(if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT) }
    }

    fun moveCursor(code: Int) = sendDownUpKeyEvents(code)
    fun edit(id: Int) { currentInputConnection?.performContextMenuAction(id) }

    fun undoRedo(redo: Boolean) {
        val ic = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (redo) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
    }

    fun numberBeforeCursor(): Double? {
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: return null
        val m = Regex("(-?\\d[\\d,]*(?:\\.\\d+)?)\\s*$").find(before) ?: return null
        return m.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    // ---------- suggestions and glide ----------
    fun refreshSuggestions() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(25)
            val ic = currentInputConnection ?: return@launch
            if (sensitive || view?.mode != KeyboardView.Mode.LETTERS || search != null) {
                pendingCorr = null; view?.setSuggestions(emptyList()); return@launch
            }
            val before = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""
            val (prefix, prev) = parseContext(before)
            pendingCorr = null
            val sg = engine.suggest(prefix, prev, snap[P.nextWord])
            if (prefix.isNotEmpty()) engine.correct(prefix, snap[P.autocorrect])?.let { pendingCorr = prefix to it }
            view?.setSuggestions((listOfNotNull(pendingCorr?.second) + sg).distinct().take(3))
        }
    }

    fun pickSuggestion(s: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""
        val (prefix, prev) = parseContext(before)
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$s ", 1)
        pendingCorr = null
        lastSpaceTime = SystemClock.uptimeMillis()
        if (!sensitive) scope.launch { engine.learn(s, prev) }
        if (shift == 1) setShift(0)
        updateAutoCap(); refreshSuggestions()
    }

    fun onGlide(path: String) {
        scope.launch {
            val c = engine.decodeGlide(path)
            val ic = currentInputConnection ?: return@launch
            if (c.isEmpty()) return@launch
            val prev = ic.getTextBeforeCursor(1, 0)?.toString()
            if (!prev.isNullOrEmpty() && !prev[0].isWhitespace()) ic.commitText(" ", 1)
            var w = c[0]
            if (shift > 0) w = w.replaceFirstChar { it.uppercase() }
            ic.commitText("$w ", 1)
            if (shift == 1) setShift(0)
            lastSpaceTime = SystemClock.uptimeMillis()
            updateAutoCap()
            view?.setSuggestions(c.drop(1))
        }
    }

    // ---------- toolbar ----------
    private fun contextText(): String {
        val ic = currentInputConnection ?: return ""
        return ic.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() } ?: ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }

    fun openSettings(route: String?) {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { if (route != null) putExtra("route", route) })
    }

    fun onTool(id: String) {
        when (id) {
            "clipboard" -> view?.showClip()
            "snippets" -> view?.showSnippets()
            "voice" -> startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "translate" -> openUrl("https://translate.google.com/?sl=auto&tl=en&op=translate&text=" + Uri.encode(contextText()))
            "search" -> openUrl("https://www.google.com/search?q=" + Uri.encode(contextText()))
            "undo" -> undoRedo(false)
            "redo" -> undoRedo(true)
            "cursor" -> view?.showCursor()
            "emoji" -> view?.showEmoji()
            "settings" -> openSettings(null)
        }
    }
}
