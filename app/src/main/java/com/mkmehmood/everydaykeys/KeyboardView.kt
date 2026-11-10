package com.mkmehmood.everydaykeys

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class KeySpec(
    val label: String? = null, val icon: Int = 0, val desc: String = label ?: "",
    val weight: Float = 1f, val style: Int = 0, val alts: String = "",
    val repeat: Boolean = false, val space: Boolean = false, val spacer: Boolean = false,
    val onLong: (() -> Unit)? = null, val tap: () -> Unit = {}
)

class Pal(val bg: Int, val key: Int, val fn: Int, val text: Int, val accent: Int, val onAccent: Int)

class KeyCell(ctx: Context, private val kv: KeyboardView, private val spec: KeySpec, p: Pal, fontSp: Float) : FrameLayout(ctx) {
    private val h = Handler(Looper.getMainLooper())
    private var fired = false
    private var dragged = false
    private var repeating = false
    private var lastX = 0f
    private val bgd = GradientDrawable().apply {
        cornerRadius = kv.dp(8).toFloat()
        setColor(when (spec.style) { 1 -> p.fn; 2 -> p.accent; else -> p.key })
    }
    private val longRun = Runnable {
        if (spec.alts.isNotEmpty()) { fired = true; kv.showAlts(this, spec.alts) }
        else spec.onLong?.let { fired = true; it() }
    }
    private val repRun = object : Runnable {
        override fun run() { repeating = true; spec.tap(); h.postDelayed(this, 45) }
    }

    init {
        background = bgd
        contentDescription = spec.desc
        val fg = if (spec.style == 2) p.onAccent else p.text
        if (spec.icon != 0) {
            addView(ImageView(ctx).apply {
                setImageResource(spec.icon); imageTintList = ColorStateList.valueOf(fg)
                scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(kv.dp(11), kv.dp(11), kv.dp(11), kv.dp(11))
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else if (spec.label != null) {
            addView(TextView(ctx).apply {
                text = spec.label; setTextColor(fg); setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp); gravity = Gravity.CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                bgd.alpha = 150; fired = false; dragged = false; repeating = false; lastX = e.rawX
                kv.ime.feedback()
                if (spec.repeat) h.postDelayed(repRun, 400)
                else if (spec.alts.isNotEmpty() || spec.onLong != null) h.postDelayed(longRun, kv.ime.s.longMs.toLong())
            }
            MotionEvent.ACTION_MOVE -> if (spec.space) {
                val step = kv.dp(kv.ime.s.dragSens).toFloat()
                while (abs(e.rawX - lastX) >= step) {
                    val d = if (e.rawX > lastX) 1 else -1
                    kv.ime.moveCursor(d); lastX += d * step; dragged = true; h.removeCallbacks(longRun)
                }
            }
            MotionEvent.ACTION_UP -> { end(); if (!fired && !dragged && !repeating) spec.tap() }
            MotionEvent.ACTION_CANCEL -> end()
        }
        return true
    }

    private fun end() { h.removeCallbacks(longRun); h.removeCallbacks(repRun); bgd.alpha = 255 }
}

class KeyboardView(ctx: Context, val ime: EverydayKeysService) : FrameLayout(ctx) {
    enum class Mode { LETTERS, NUMPAD, SYM1, SYM2, CLIP, SNIPPETS, EMOJI }

    var mode = Mode.LETTERS
    var shift = 0
    var langIdx = 0
    var searching = false
    var query = ""
    private var lastShift = 0L
    private var emojiCat = 0
    private var clips: List<Clip> = emptyList()
    private val selected = HashSet<Long>()
    private var confirmClear = false
    private var popup: PopupWindow? = null
    private val sugViews = ArrayList<TextView>()
    private lateinit var pal: Pal
    private val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    private val s get() = ime.s

    init { addView(inner) }

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun palette(): Pal {
        val dark = when (s.theme) {
            1 -> false; 2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        return if (dark) Pal(0xFF1B1B1F.toInt(), 0xFF2E2F36.toInt(), 0xFF3B3D46.toInt(), 0xFFEDEDF2.toInt(), 0xFF7AA2FF.toInt(), 0xFF10131A.toInt())
        else Pal(0xFFE8EAF0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCDD1DB.toInt(), 0xFF1B1B1F.toInt(), 0xFF2F5BEA.toInt(), 0xFFFFFFFF.toInt())
    }

    private fun lang(): Lang { val l = Layouts.enabled(s.langs); return l[langIdx % l.size] }

    // ---------- state hooks ----------
    fun startInput(cls: Int) {
        mode = if (s.autoNum && (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME)) Mode.NUMPAD else Mode.LETTERS
        searching = false; query = ""; selected.clear()
        shift = if (s.autoCap && lang().latin && capsNeeded()) 1 else 0
        render()
    }

    private fun capsNeeded() = (ime.currentInputConnection?.getCursorCapsMode(ime.inputType) ?: 0) != 0

    fun afterType() {
        val old = shift
        if (shift == 1) shift = 0
        if (s.autoCap && shift == 0 && lang().latin && mode == Mode.LETTERS && capsNeeded()) shift = 1
        if (shift != old) render() else updateSuggestions()
    }

    fun progress(p: Int) {
        if (p < 0 || !s.showProgress) { bar.visibility = GONE; return }
        bar.visibility = VISIBLE; bar.progress = p
    }

    private fun updateSuggestions() {
        if (sugViews.isEmpty()) return
        val l = if (s.suggest && !ime.sensitive) Dict.suggest(ime.currentWord()) else emptyList()
        sugViews.forEachIndexed { i, tv -> tv.text = l.getOrNull(i) ?: ""; tv.isEnabled = i < l.size }
    }

    fun loadClips() {
        ime.scope.launch {
            val dao = Graph.db(ime).clips()
            clips = withContext(Dispatchers.IO) { if (query.isEmpty()) dao.all() else dao.search(query) }
            if (mode == Mode.CLIP) render()
        }
    }

    // ---------- rendering ----------
    fun render() {
        popup?.dismiss()
        pal = palette()
        setBackgroundColor(pal.bg)
        sugViews.clear()
        inner.removeAllViews()
        val w = resources.displayMetrics.widthPixels
        inner.layoutParams = LayoutParams(
            if (s.oneHanded == 0) LayoutParams.MATCH_PARENT else (w * 0.78f).toInt(), LayoutParams.WRAP_CONTENT,
            if (s.oneHanded == 2) Gravity.END else Gravity.START
        )
        inner.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)))
        when (mode) {
            Mode.LETTERS -> { suggestionStrip(); toolbar(); lettersRows() }
            Mode.NUMPAD -> numpad()
            Mode.SYM1, Mode.SYM2 -> symbols()
            Mode.CLIP -> clipPanel()
            Mode.SNIPPETS -> snippetPanel()
            Mode.EMOJI -> emojiPanel()
        }
        updateSuggestions()
    }

    private fun addRow(specs: List<KeySpec>, hDp: Int = s.keyHeight) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        specs.forEach { sp ->
            val lp = LinearLayout.LayoutParams(0, dp(hDp), sp.weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
            row.addView(if (sp.spacer) View(context) else KeyCell(context, this, sp, pal, s.fontSp.toFloat()), lp)
        }
        inner.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun icon(res: Int, desc: String, sizeDp: Int, tint: Int = pal.text, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(res); imageTintList = ColorStateList.valueOf(tint); contentDescription = desc
        setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { ime.feedback(); onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun suggestionStrip() {
        if (!s.suggest) return
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) {
            val tv = TextView(context).apply {
                setTextColor(pal.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); gravity = Gravity.CENTER
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setOnClickListener { if (text.isNotEmpty()) ime.acceptSuggestion(text.toString()) }
            }
            sugViews.add(tv); row.addView(tv, LinearLayout.LayoutParams(0, dp(34), 1f))
        }
        inner.addView(row)
    }

    private fun toolbar() {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val ids = s.tools.split(",").filter { it.isNotBlank() }
        Layouts.TOOLS.filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }.forEach { t ->
            row.addView(icon(t.icon, t.label, 42) { toolAction(t.id) })
        }
        inner.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    private fun toolAction(id: String) {
        when (id) {
            "clip" -> { mode = Mode.CLIP; loadClips(); render() }
            "snip" -> { mode = Mode.SNIPPETS; render() }
            "emoji" -> { mode = Mode.EMOJI; render() }
            "voice" -> ime.voice()
            "selall" -> ime.menu(android.R.id.selectAll)
            "cut" -> ime.menu(android.R.id.cut)
            "copy" -> ime.menu(android.R.id.copy)
            "paste" -> ime.pasteLatest()
            "undo" -> ime.ctrlKey(KeyEvent.KEYCODE_Z)
            "redo" -> ime.ctrlKey(KeyEvent.KEYCODE_Z, true)
            "left" -> ime.moveCursor(-1)
            "right" -> ime.moveCursor(1)
            "search" -> ime.search()
            "translate" -> ime.translate()
            "settings" -> ime.openSettings()
        }
    }

    // ---------- letters ----------
    private fun lettersRows() {
        val l = lang()
        val up = shift > 0 && l.latin
        fun ch(c: String) = if (up) c.uppercase() else c
        fun key(c: String): KeySpec {
            val a = if (s.accents) l.alts[c] ?: "" else ""
            return KeySpec(label = ch(c), alts = if (up) a.uppercase() else a, tap = { ime.type(ch(c)) })
        }
        if (s.numRow) addRow(Layouts.DIGITS.map { key(it.toString()).let { k -> KeySpec(label = k.label, tap = k.tap) } }, (s.keyHeight * 0.8f).toInt())
        val r = l.rows.map { row -> row.map { it.toString() } }
        addRow(r[0].map { key(it) })
        val diff = (r[0].size - r[1].size) / 2f
        addRow(listOf(KeySpec(spacer = true, weight = diff)) + r[1].map { key(it) } + KeySpec(spacer = true, weight = diff))
        val shiftKey = if (l.latin) KeySpec(
            icon = if (shift == 2) R.drawable.ic_shift_lock else R.drawable.ic_shift, desc = "Shift", weight = 1.5f, style = if (shift > 0) 2 else 1,
            tap = {
                val now = SystemClock.uptimeMillis()
                shift = if (shift == 2) 0 else if (shift == 1 && now - lastShift < 400) 2 else if (shift == 0) 1 else 0
                lastShift = now; render()
            })
        else KeySpec(icon = R.drawable.ic_symbols, desc = "Symbols", weight = 1.5f, style = 1, tap = { mode = Mode.SYM1; render() })
        addRow(listOf(shiftKey) + r[2].map { key(it) } + backspaceKey())
        bottomRow(true)
    }

    private fun backspaceKey() = KeySpec(icon = R.drawable.ic_backspace, desc = "Backspace", weight = 1.5f, style = 1, repeat = true, tap = { ime.backspace() })
    private fun enterKey() = KeySpec(icon = R.drawable.ic_enter, desc = "Enter", weight = 1.5f, style = 2, tap = { ime.enter() })

    private fun bottomRow(letters: Boolean) {
        val list = ArrayList<KeySpec>()
        if (letters) list.add(KeySpec(icon = R.drawable.ic_symbols, desc = "Symbols", weight = 1.2f, style = 1, tap = { mode = Mode.SYM1; render() }))
        else list.add(KeySpec(icon = R.drawable.ic_keyboard, desc = "Letters", weight = 1.2f, style = 1, tap = { mode = Mode.LETTERS; render() }))
        list.add(KeySpec(icon = R.drawable.ic_dialpad, desc = "Number pad", weight = 1.2f, style = 1, tap = { mode = Mode.NUMPAD; render() }))
        if (letters && s.langKey && Layouts.enabled(s.langs).size > 1)
            list.add(KeySpec(icon = R.drawable.ic_language, desc = "Switch language", style = 1, tap = { langIdx++; shift = 0; render() }))
        list.add(KeySpec(label = ",", tap = { ime.type(",") }))
        list.add(KeySpec(label = "", desc = "Space", weight = 4f, space = true, onLong = { ime.pickKeyboard() }, tap = { ime.type(" ") }))
        list.add(KeySpec(label = ".", alts = ",?!;:'\"", tap = { ime.type(".") }))
        list.add(enterKey())
        addRow(list)
    }

    // ---------- number pad ----------
    private fun numpad() {
        fun k(c: String, st: Int = 0) = KeySpec(label = c, style = st, tap = { ime.type(c) })
        val digits = if (s.numpadStyle == 0) listOf("123", "456", "789") else listOf("789", "456", "123")
        val ops = listOf("+", "-", "×", "÷")
        val right = listOf(backspaceKey().let { KeySpec(icon = it.icon, desc = it.desc, style = 1, repeat = true, tap = it.tap) },
            k("%", 1), KeySpec(label = "=", style = 1, tap = { ime.calc() }), k(",", 1))
        val rows = listOf(digits[0], digits[1], digits[2], ",0.")
        rows.forEachIndexed { i, d ->
            val list = ArrayList<KeySpec>()
            if (s.ops) list.add(k(ops[i], 1))
            d.forEach { list.add(k(it.toString())) }
            if (i < 3) list.add(right[i]) else list.add(KeySpec(icon = R.drawable.ic_enter, desc = "Enter", style = 2, tap = { ime.enter() }))
            addRow(list)
        }
        addRow(listOf(
            KeySpec(icon = R.drawable.ic_keyboard, desc = "Letters", weight = 1.5f, style = 1, tap = { mode = Mode.LETTERS; render() }),
            KeySpec(label = "", desc = "Space", weight = 3f, space = true, tap = { ime.type(" ") }),
            KeySpec(icon = R.drawable.ic_backspace, desc = "Backspace", weight = 1.5f, style = 1, repeat = true, tap = { ime.backspace() })
        ))
    }

    // ---------- symbols ----------
    private fun symbols() {
        val rows = if (mode == Mode.SYM1) Layouts.SYM1 else Layouts.SYM2
        fun k(c: String) = KeySpec(label = c, tap = { ime.type(c) })
        addRow(Layouts.DIGITS.map { k(it.toString()) }, (s.keyHeight * 0.8f).toInt())
        addRow(rows[0].map { k(it.toString()) })
        addRow(rows[1].map { k(it.toString()) })
        addRow(listOf(KeySpec(icon = R.drawable.ic_swap, desc = "More symbols", weight = 1.5f, style = 1,
            tap = { mode = if (mode == Mode.SYM1) Mode.SYM2 else Mode.SYM1; render() })) + rows[2].map { k(it.toString()) } + backspaceKey())
        bottomRow(false)
    }

    // ---------- shared panel header ----------
    private fun header(vararg extra: Triple<Int, String, () -> Unit>) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(icon(R.drawable.ic_keyboard, "Back to keyboard", 44, pal.accent) { searching = false; query = ""; selected.clear(); mode = Mode.LETTERS; render() })
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        extra.forEach { row.addView(icon(it.first, it.second, 44, onClick = it.third)) }
        inner.addView(row)
    }

    private fun panelHeight(rows: Int) = dp(s.keyHeight * rows)

    // ---------- clipboard ----------
    private fun clipPanel() {
        if (s.bioLock && !ClipLock.open()) {
            header()
            inner.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                addView(TextView(context).apply { text = "Clipboard history is locked"; setTextColor(pal.text); gravity = Gravity.CENTER })
                addView(icon(R.drawable.ic_lock, "Unlock clipboard", 64, pal.accent) { ime.unlock() })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelHeight(4)))
            return
        }
        header(
            Triple(R.drawable.ic_search, "Search clipboard") { searching = !searching; if (!searching) query = ""; loadClips(); render() },
            Triple(R.drawable.ic_paste, "Paste selected items together") {
                val list = clips.filter { it.id in selected }
                if (list.isNotEmpty()) { ime.pasteMany(list); selected.clear(); mode = Mode.LETTERS; render() }
            },
            Triple(if (confirmClear) R.drawable.ic_check else R.drawable.ic_delete, if (confirmClear) "Confirm clear all" else "Clear all unpinned") {
                if (confirmClear) { confirmClear = false; ime.scope.launch { ClipStore.clearAll(ime, true); loadClips() } } else { confirmClear = true; render() }
            }
        )
        if (searching) inner.addView(TextView(context).apply {
            text = if (query.isEmpty()) "Type to search…" else query; setTextColor(pal.text); setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(pal.key) }
        })
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (clips.isEmpty()) list.addView(TextView(context).apply { text = "Nothing copied yet"; setTextColor(pal.text); setPadding(dp(16), dp(16), dp(16), dp(16)) })
        clips.forEach { c -> list.addView(clipRow(c)) }
        inner.addView(ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelHeight(if (searching) 2 else 4)))
        if (searching) lettersRows()
    }

    private fun clipRow(c: Clip): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(if (c.id in selected) pal.fn else pal.key) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
        }
        val label = c.preview.replace('\n', ' ') + if (c.filePath != null) "   · file, ${c.size / 1000} K chars" else ""
        row.addView(TextView(context).apply {
            text = label; setTextColor(pal.text); maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(12), dp(8), dp(4), dp(8))
            contentDescription = "Paste: $label"
            setOnClickListener { ime.feedback(); ime.pasteClip(c); mode = Mode.LETTERS; searching = false; render() }
            setOnLongClickListener { if (!selected.add(c.id)) selected.remove(c.id); render(); true }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(icon(R.drawable.ic_pin, if (c.pinned) "Unpin" else "Pin", 40, if (c.pinned) pal.accent else pal.text) {
            ime.scope.launch { withContext(Dispatchers.IO) { Graph.db(ime).clips().update(c.copy(pinned = !c.pinned)) }; loadClips() }
        })
        row.addView(icon(R.drawable.ic_delete, "Delete", 40) { ime.scope.launch { ClipStore.remove(ime, c); loadClips() } })
        return row
    }

    // ---------- snippets ----------
    private fun snippetPanel() {
        header(Triple(R.drawable.ic_settings, "Manage snippets") { ime.openSettings() })
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (ime.snippets.isEmpty()) list.addView(TextView(context).apply { text = "No snippets yet. Add some in settings."; setTextColor(pal.text); setPadding(dp(16), dp(16), dp(16), dp(16)) })
        ime.snippets.forEach { sn ->
            list.addView(TextView(context).apply {
                text = "${sn.title}   ${sn.shortcut}\n${sn.body.take(80)}"; setTextColor(pal.text); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(pal.key) }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
                setOnClickListener { ime.feedback(); ime.pasteLarge(sn.body); mode = Mode.LETTERS; render() }
            })
        }
        inner.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelHeight(4)))
    }

    // ---------- emoji ----------
    private fun emojiPanel() {
        val prefs = ime.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val recents = (prefs.getString("recents", "") ?: "").split(" ").filter { it.isNotEmpty() }
        val tabs = listOf(recents.joinToString("")) + Layouts.EMOJI
        fun split(t: String) = t.codePoints().toArray().map { String(Character.toChars(it)) }
        val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(icon(R.drawable.ic_keyboard, "Back to keyboard", 44, pal.accent) { mode = Mode.LETTERS; render() })
        tabs.forEachIndexed { i, t ->
            val first = split(t).firstOrNull() ?: "🕘"
            bar.addView(TextView(context).apply {
                text = if (i == 0) "🕘" else first; textSize = 20f; gravity = Gravity.CENTER; alpha = if (i == emojiCat) 1f else 0.5f
                contentDescription = if (i == 0) "Recent emoji" else "Emoji category $i"
                setOnClickListener { emojiCat = i; render() }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        bar.addView(icon(R.drawable.ic_backspace, "Backspace", 44) { ime.backspace() })
        inner.addView(bar)
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        split(tabs.getOrElse(emojiCat) { "" }).chunked(8).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { e ->
                row.addView(TextView(context).apply {
                    text = e; textSize = 26f; gravity = Gravity.CENTER; contentDescription = "Emoji $e"
                    setOnClickListener {
                        ime.feedback(); ime.commitExternal(e)
                        prefs.edit().putString("recents", (listOf(e) + recents.filter { it != e }).take(24).joinToString(" ")).apply()
                    }
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            grid.addView(row)
        }
        inner.addView(ScrollView(context).apply { addView(grid) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelHeight(4)))
    }

    // ---------- long-press popup ----------
    fun showAlts(anchor: View, alts: String) {
        popup?.dismiss()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(pal.fn) }
        }
        alts.map { it.toString() }.forEach { c ->
            row.addView(TextView(context).apply {
                text = c; textSize = 22f; setTextColor(pal.text); gravity = Gravity.CENTER; minWidth = dp(40); minHeight = dp(48)
                contentDescription = c
                setOnClickListener { popup?.dismiss(); ime.type(c); afterType() }
            })
        }
        val pw = PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true; setBackgroundDrawable(ColorDrawable(0)); elevation = dp(8).toFloat()
        }
        popup = pw
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val sw = resources.displayMetrics.widthPixels
        val x = (loc[0] + anchor.width / 2 - row.measuredWidth / 2).coerceIn(0, maxOf(0, sw - row.measuredWidth))
        pw.showAtLocation(this, Gravity.NO_GRAVITY, x, loc[1] - row.measuredHeight - dp(6))
    }
}
