package com.mkmehmood.everydaykeys.ime

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.P
import com.mkmehmood.everydaykeys.data.Snap

class KeyboardView(private val svc: EverydayKeysService) : LinearLayout(svc), KeysView.Listener {
    enum class Mode { LETTERS, NUMPAD, SYMBOLS1, SYMBOLS2 }

    val keys = KeysView(svc)
    var mode = Mode.LETTERS
        private set
    var phoneField = false
    private var pal = Palettes.from(svc, Snap(androidx.datastore.preferences.core.emptyPreferences()))
    private var snap: Snap = Snap(androidx.datastore.preferences.core.emptyPreferences())
    private var layoutIds: List<String> = listOf("en_qwerty")
    private var layoutIndex = 0

    private val progressBox = LinearLayout(svc)
    private val progressTitle = TextView(svc)
    private val progressPct = TextView(svc)
    private val progressBar = ProgressBar(svc, null, android.R.attr.progressBarStyleHorizontal)
    private val toolbarRow = LinearLayout(svc)
    private val toolbarScroll = HorizontalScrollView(svc)
    private val suggestRow = LinearLayout(svc)
    private val suggestViews = List(3) { TextView(svc) }
    private val panelHost = FrameLayout(svc)

    val currentLayout: LayoutDef get() = Layouts.byId(layoutIds[layoutIndex.coerceIn(0, layoutIds.size - 1)])

    init {
        orientation = VERTICAL
        // paste progress strip
        progressBox.orientation = VERTICAL
        progressBox.setPadding(dpi(14f), dpi(6f), dpi(14f), dpi(6f))
        val top = LinearLayout(svc).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        progressTitle.textSize = 13f
        progressPct.textSize = 13f
        top.addView(progressTitle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        top.addView(progressPct)
        progressBox.addView(top)
        progressBar.max = 100
        progressBox.addView(progressBar, LayoutParams(LayoutParams.MATCH_PARENT, dpi(6f)).apply { topMargin = dpi(4f) })
        progressBox.visibility = GONE
        addView(progressBox)

        toolbarScroll.isHorizontalScrollBarEnabled = false
        toolbarScroll.isFillViewport = true
        toolbarScroll.addView(toolbarRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(toolbarScroll, LayoutParams(LayoutParams.MATCH_PARENT, dpi(44f)))

        suggestRow.orientation = HORIZONTAL
        suggestViews.forEachIndexed { i, tv ->
            tv.gravity = Gravity.CENTER; tv.textSize = 17f; tv.maxLines = 1
            suggestRow.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(suggestRow, LayoutParams(LayoutParams.MATCH_PARENT, dpi(38f)))

        keys.listener = this
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        panelHost.visibility = GONE
        addView(panelHost, LayoutParams(LayoutParams.MATCH_PARENT, dpi(260f)))
    }

    fun applySettings(s: Snap) {
        snap = s
        pal = Palettes.from(svc, s)
        setBackgroundColor(pal.bg)
        keys.palette = pal
        keys.rowH = dpi(s[P.keyHeight])
        keys.textPx = s[P.fontSize] * resources.displayMetrics.scaledDensity
        keys.borders = s[P.keyBorder]
        keys.longPressMs = s[P.longPress].toLong()
        keys.accentPopup = s[P.accentPopup]
        keys.preview = s[P.keyPreview]
        keys.glideEnabled = s[P.glide]
        keys.cursorSens = s[P.cursorSens]
        layoutIds = s[P.layouts].split(",").filter { id -> Layouts.all.any { it.id == id } }.ifEmpty { listOf("en_qwerty") }
        layoutIndex = layoutIndex.coerceIn(0, layoutIds.size - 1)
        buildToolbar()
        suggestRow.visibility = if (s[P.toolbarCollapsed]) GONE else VISIBLE
        suggestViews.forEach { it.setTextColor(pal.text) }
        progressTitle.setTextColor(pal.text); progressPct.setTextColor(pal.accent)
        progressBar.progressTintList = ColorStateList.valueOf(pal.accent)
        progressBar.progressBackgroundTintList = ColorStateList.valueOf(pal.func)
        setMode(mode)
    }

    private fun buildToolbar() {
        toolbarRow.removeAllViews()
        toolbarRow.orientation = HORIZONTAL
        snap[P.toolbar].split(",").mapNotNull { Tools.byId(it) }.forEach { t ->
            val b = ImageButton(svc).apply {
                setImageResource(t.icon); setColorFilter(pal.sub); background = null
                contentDescription = t.name; scaleType = ImageView.ScaleType.CENTER
                minimumWidth = dpi(44f)
                setOnClickListener { svc.onTool(t.id) }
            }
            toolbarRow.addView(b, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    fun setMode(m: Mode) {
        mode = m
        hidePanel()
        keys.isLetters = m == Mode.LETTERS
        keys.rows = when (m) {
            Mode.LETTERS -> Layouts.letters(currentLayout, snap[P.numberRow], snap[P.langKey] && layoutIds.size > 1)
            Mode.NUMPAD -> Layouts.numpad(if (phoneField) "phone" else snap[P.numpadStyle], snap[P.mathRow])
            Mode.SYMBOLS1 -> Layouts.symbols(1)
            Mode.SYMBOLS2 -> Layouts.symbols(2)
        }
        suggestRow.alpha = if (m == Mode.LETTERS) 1f else 0.0f
    }

    fun nextLang() {
        if (layoutIds.size < 2) return
        layoutIndex = (layoutIndex + 1) % layoutIds.size
        setMode(Mode.LETTERS)
        Toast.makeText(svc, currentLayout.name, Toast.LENGTH_SHORT).show()
    }

    fun setSuggestions(list: List<String>) {
        suggestViews.forEachIndexed { i, tv ->
            val s = list.getOrNull(i)
            tv.text = s ?: ""
            tv.setOnClickListener { if (s != null) svc.pickSuggestion(s) }
        }
    }

    fun showProgress(pct: Int?) {
        if (pct == null) { progressBox.visibility = GONE; return }
        progressBox.visibility = VISIBLE
        progressTitle.text = "Pasting a large document. Sent in small chunks so nothing is cut off."
        progressPct.text = "$pct%"
        progressBar.progress = pct
    }

    fun setEnterIcon(imeAction: Int) {
        keys.enterIcon = if (imeAction == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) R.drawable.ic_search else R.drawable.ic_enter
        keys.invalidate()
    }

    fun setShift(n: Int) { keys.shift = n }

    // ---- panels ----
    private fun showPanel(v: View) {
        svc.endSearch()
        panelHost.removeAllViews()
        panelHost.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        panelHost.layoutParams = panelHost.layoutParams.apply { height = keys.rowH * 5 }
        panelHost.visibility = VISIBLE
        keys.visibility = GONE
        suggestRow.alpha = 0f
    }

    fun hidePanel() {
        if (panelHost.visibility == VISIBLE) svc.endSearch()
        panelHost.removeAllViews()
        panelHost.visibility = GONE
        keys.visibility = VISIBLE
        suggestRow.alpha = if (mode == Mode.LETTERS) 1f else 0f
    }

    /** While a panel search field is active the letters keyboard sits under a shorter panel. */
    fun setSearching(on: Boolean) {
        if (panelHost.visibility != VISIBLE) return
        if (on) {
            keys.isLetters = true
            keys.rows = Layouts.letters(currentLayout, false, false)
            keys.visibility = VISIBLE
            panelHost.layoutParams = panelHost.layoutParams.apply { height = keys.rowH * 3 }
        } else {
            keys.visibility = GONE
            panelHost.layoutParams = panelHost.layoutParams.apply { height = keys.rowH * 5 }
        }
    }

    private var clipPanel: ClipPanel? = null
    fun showClip() { val p = ClipPanel(svc, pal); clipPanel = p; showPanel(p) }
    fun refreshClip() { clipPanel?.refresh() }
    fun showSnippets() = showPanel(SnippetsPanel(svc, pal))
    fun showEmoji() = showPanel(EmojiPanel(svc, pal))
    fun showCursor() = showPanel(CursorPanel(svc, pal))
    fun showConvert() = showPanel(ConvertPanel(svc, pal))

    // ---- KeysView.Listener ----
    override fun onKey(k: Key, text: String) = svc.onKey(k, text)
    override fun onGlide(path: String) = svc.onGlide(path)
    override fun onSpaceDrag(steps: Int) = svc.onSpaceDrag(steps)
    override fun onTouchFeedback() = svc.feedback()
}
