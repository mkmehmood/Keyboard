package com.mkmehmood.everydaykeys.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.Clip
import com.mkmehmood.everydaykeys.data.Snippet
import com.mkmehmood.everydaykeys.smart.Calc
import com.mkmehmood.everydaykeys.smart.Conv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

fun roundBg(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }

fun Context.iconBtn(res: Int, tint: Int, desc: String, size: Int = 40, onClick: () -> Unit) = ImageButton(this).apply {
    setImageResource(res); setColorFilter(tint); background = null; contentDescription = desc
    scaleType = ImageView.ScaleType.CENTER
    layoutParams = LinearLayout.LayoutParams(dpi(size.toFloat()), dpi(size.toFloat()))
    setOnClickListener { onClick() }
}

fun Context.textView(t: String, color: Int, sp: Float, bold: Boolean = false) = TextView(this).apply {
    text = t; setTextColor(color); textSize = sp; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
}

class RowAdapter<T>(private val make: (Context) -> View, private val bind: (View, T) -> Unit) : RecyclerView.Adapter<RowAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v)
    var items: List<T> = emptyList()
        private set
    @SuppressLint("NotifyDataSetChanged")
    fun submit(l: List<T>) { items = l; notifyDataSetChanged() }
    @SuppressLint("NotifyDataSetChanged")
    fun refresh() = notifyDataSetChanged()
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(make(p.context))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, i: Int) = bind(h.itemView, items[i])
}

class SearchField(ctx: Context, private val pal: Palette, private val hint: String, onTap: () -> Unit, onClear: () -> Unit) : LinearLayout(ctx) {
    private val tv = TextView(ctx)
    private val clear: ImageButton

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = roundBg(pal.func, ctx.dpi(10f).toFloat())
        setPadding(ctx.dpi(10f), 0, ctx.dpi(2f), 0)
        addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_search); setColorFilter(pal.sub); layoutParams = LayoutParams(ctx.dpi(20f), ctx.dpi(20f)) })
        tv.textSize = 15f; tv.maxLines = 1; tv.setPadding(ctx.dpi(8f), 0, 0, 0)
        tv.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        addView(tv)
        clear = ctx.iconBtn(R.drawable.ic_close, pal.sub, "Clear search", 36) { onClear() }
        addView(clear)
        setOnClickListener { onTap() }
        setQuery("")
    }

    fun setQuery(q: String) {
        tv.text = if (q.isEmpty()) hint else q
        tv.setTextColor(if (q.isEmpty()) pal.sub else pal.text)
        clear.visibility = if (q.isEmpty()) GONE else VISIBLE
    }
}

private class ClipRow(ctx: Context, private val pal: Palette) : LinearLayout(ctx) {
    val icon = ImageView(ctx)
    val title = TextView(ctx)
    val sub = TextView(ctx)
    var clip: Clip? = null
    lateinit var pin: ImageButton
    lateinit var del: ImageButton

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
        setPadding(ctx.dpi(6f), ctx.dpi(5f), ctx.dpi(2f), ctx.dpi(5f))
        val box = FrameLayout(ctx).apply { background = roundBg(pal.accent and 0x00FFFFFF or 0x22000000, ctx.dpi(10f).toFloat()); layoutParams = LayoutParams(ctx.dpi(40f), ctx.dpi(40f)) }
        icon.setColorFilter(pal.accent)
        box.addView(icon, FrameLayout.LayoutParams(ctx.dpi(22f), ctx.dpi(22f), Gravity.CENTER))
        addView(box)
        val col = LinearLayout(ctx).apply { orientation = VERTICAL; setPadding(ctx.dpi(12f), 0, ctx.dpi(8f), 0); layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f) }
        title.setTextColor(pal.text); title.textSize = 15f; title.maxLines = 1; title.ellipsize = android.text.TextUtils.TruncateAt.END
        sub.setTextColor(pal.sub); sub.textSize = 12f; sub.maxLines = 1
        col.addView(title); col.addView(sub)
        addView(col)
    }

    fun setSelectedState(on: Boolean) { background = if (on) roundBg(pal.accent and 0x00FFFFFF or 0x33000000, context.dpi(10f).toFloat()) else null }
}

class ClipPanel(private val svc: EverydayKeysService, private val pal: Palette) : LinearLayout(svc) {
    private val query = MutableStateFlow("")
    private var job: Job? = null
    private val selected = LinkedHashSet<Long>()
    private lateinit var field: SearchField
    private lateinit var adapter: RowAdapter<Clip>
    private lateinit var empty: TextView
    private val body = LinearLayout(svc)
    private val lockedBox = LinearLayout(svc)

    init {
        orientation = VERTICAL
        setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(2f))
        // locked state
        lockedBox.orientation = VERTICAL; lockedBox.gravity = Gravity.CENTER
        lockedBox.addView(ImageView(svc).apply { setImageResource(R.drawable.ic_lock); setColorFilter(pal.sub); layoutParams = LayoutParams(dpi(36f), dpi(36f)) })
        lockedBox.addView(svc.textView("Clipboard history is locked", pal.text, 15f).apply { setPadding(0, dpi(8f), 0, dpi(8f)) })
        lockedBox.addView(svc.iconBtn(R.drawable.ic_lock, pal.onAccent, "Unlock", 48) { svc.requestUnlock() }.apply { background = roundBg(pal.accent, dp(24f)) })
        addView(lockedBox, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        body.orientation = VERTICAL
        val head = LinearLayout(svc).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        field = SearchField(svc, pal, "Search clipboard", { startSearch() }, { query.value = ""; field.setQuery(""); svc.endSearch() })
        head.addView(field, LayoutParams(0, dpi(40f), 1f))
        head.addView(svc.iconBtn(R.drawable.ic_swap, pal.text, "Swap with previous item") { svc.swapLastPaste() })
        head.addView(svc.iconBtn(R.drawable.ic_merge, pal.text, "Paste selected items together") {
            val picked = adapter.items.filter { it.id in selected }
            if (picked.isNotEmpty()) { svc.pasteMerged(picked); selected.clear(); adapter.refresh() }
        })
        head.addView(svc.iconBtn(R.drawable.ic_trash, pal.text, "Clear all") { svc.scope.launch { svc.store.clearUnpinned() } })
        head.addView(svc.iconBtn(R.drawable.ic_keyboard, pal.text, "Back to keyboard") { svc.showLetters() })
        body.addView(head, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        adapter = RowAdapter({ ctx -> makeRow(ctx) }, { v, c ->
            val r = v as ClipRow
            r.clip = c
            r.title.text = c.preview.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: c.preview.take(80)
            r.sub.text = when {
                c.pinned -> "Pinned"
                c.kind == "file" -> "Large item, stored as a file (${c.size / 1000}K characters)"
                c.kind == "link" -> "Link"
                else -> "Text"
            }
            r.icon.setImageResource(when {
                c.pinned -> R.drawable.ic_pin
                c.kind == "file" -> R.drawable.ic_doc
                c.kind == "link" -> R.drawable.ic_link
                else -> R.drawable.ic_text
            })
            r.setSelectedState(c.id in selected)
        })
        val list = RecyclerView(svc)
        list.layoutManager = LinearLayoutManager(svc)
        list.adapter = adapter
        body.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        empty = svc.textView("Nothing copied yet. Copied items appear here.", pal.sub, 14f)
        empty.gravity = Gravity.CENTER
        body.addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val foot = LinearLayout(svc).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(4f), dpi(2f), 0, 0) }
        foot.addView(ImageView(svc).apply { setImageResource(R.drawable.ic_lock); setColorFilter(pal.sub); layoutParams = LayoutParams(dpi(14f), dpi(14f)) })
        foot.addView(svc.textView("No size limit. Stays on this phone.", pal.sub, 12f).apply { setPadding(dpi(6f), 0, 0, 0) })
        body.addView(foot)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun makeRow(ctx: Context): View {
        val r = ClipRow(ctx, pal)
        r.pin = ctx.iconBtn(R.drawable.ic_pin, pal.sub, "Pin or unpin", 36) { r.clip?.let { c -> svc.scope.launch { svc.db.clips().togglePin(c.id) } } }
        r.del = ctx.iconBtn(R.drawable.ic_close, pal.sub, "Delete", 36) { r.clip?.let { c -> svc.scope.launch { svc.store.delete(c.id) } } }
        r.addView(r.pin); r.addView(r.del)
        r.setOnClickListener {
            val c = r.clip ?: return@setOnClickListener
            if (selected.isNotEmpty()) toggle(c.id) else svc.pasteClip(c)
        }
        r.setOnLongClickListener { r.clip?.let { toggle(it.id) }; true }
        return r
    }

    private fun toggle(id: Long) { if (!selected.add(id)) selected.remove(id); adapter.refresh() }

    private fun startSearch() { svc.beginSearch(query.value) { q -> query.value = q; field.setQuery(q) } }

    fun refresh() {
        val locked = svc.clipLocked()
        lockedBox.visibility = if (locked) VISIBLE else GONE
        body.visibility = if (locked) GONE else VISIBLE
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
        if (svc.clipLocked()) svc.requestUnlock()
        job = svc.scope.launch {
            query.flatMapLatest { svc.db.clips().observe(it) }.collect {
                adapter.submit(it)
                empty.visibility = if (it.isEmpty()) VISIBLE else GONE
            }
        }
    }

    override fun onDetachedFromWindow() { job?.cancel(); super.onDetachedFromWindow() }
}

class SnippetsPanel(private val svc: EverydayKeysService, private val pal: Palette) : LinearLayout(svc) {
    private var job: Job? = null
    private lateinit var adapter: RowAdapter<Snippet>
    private lateinit var empty: TextView

    init {
        orientation = VERTICAL
        setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(2f))
        val head = LinearLayout(svc).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(svc.textView("Text snippets", pal.text, 16f, true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dpi(6f) })
        head.addView(svc.iconBtn(R.drawable.ic_add, pal.text, "Add or edit snippets") { svc.openSettings("snippets") })
        head.addView(svc.iconBtn(R.drawable.ic_keyboard, pal.text, "Back to keyboard") { svc.showLetters() })
        addView(head)
        adapter = RowAdapter({ ctx ->
            val col = LinearLayout(ctx).apply {
                orientation = VERTICAL
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                setPadding(ctx.dpi(10f), ctx.dpi(8f), ctx.dpi(10f), ctx.dpi(8f))
            }
            col.addView(ctx.textView("", pal.text, 15f).apply { maxLines = 1 })
            col.addView(ctx.textView("", pal.sub, 12f).apply { maxLines = 1 })
            col
        }, { v, s ->
            val col = v as LinearLayout
            (col.getChildAt(0) as TextView).text = s.title
            (col.getChildAt(1) as TextView).text = "${s.shortcut}   ${s.body.replace('\n', ' ').take(60)}"
            col.setOnClickListener { svc.insertText(s.body) }
        })
        val list = RecyclerView(svc)
        list.layoutManager = LinearLayoutManager(svc)
        list.adapter = adapter
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        empty = svc.textView("No snippets yet. Tap the plus icon to add one, then type its shortcut (for example ;addr) and a space.", pal.sub, 14f)
        empty.gravity = Gravity.CENTER; empty.setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        addView(empty)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        job = svc.scope.launch { svc.db.snippets().observe().collect { adapter.submit(it); empty.visibility = if (it.isEmpty()) VISIBLE else GONE } }
    }

    override fun onDetachedFromWindow() { job?.cancel(); super.onDetachedFromWindow() }
}

class EmojiPanel(private val svc: EverydayKeysService, private val pal: Palette) : LinearLayout(svc) {
    private val prefs = svc.getSharedPreferences("emoji", Context.MODE_PRIVATE)
    private lateinit var field: SearchField
    private lateinit var adapter: RowAdapter<String>
    private var q = ""

    init {
        orientation = VERTICAL
        setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(2f))
        val head = LinearLayout(svc).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        field = SearchField(svc, pal, "Search emoji", { startSearch() }, { q = ""; field.setQuery(""); svc.endSearch(); reload() })
        head.addView(field, LayoutParams(0, dpi(40f), 1f))
        head.addView(svc.iconBtn(R.drawable.ic_keyboard, pal.text, "Back to keyboard") { svc.showLetters() })
        addView(head)
        adapter = RowAdapter({ ctx ->
            ctx.textView("", pal.text, 26f).apply {
                gravity = Gravity.CENTER
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, ctx.dpi(46f))
            }
        }, { v, e ->
            val tv = v as TextView
            tv.text = e
            tv.setOnClickListener { svc.insertText(e); remember(e) }
        })
        val list = RecyclerView(svc)
        list.layoutManager = GridLayoutManager(svc, 8)
        list.adapter = adapter
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        reload()
    }

    private fun startSearch() { svc.beginSearch(q) { s -> q = s; field.setQuery(s); reload() } }

    private fun recents(): List<String> = prefs.getString("recent", "")!!.split(" ").filter { it.isNotEmpty() }

    private fun remember(e: String) {
        val l = (listOf(e) + recents().filter { it != e }).take(24)
        prefs.edit().putString("recent", l.joinToString(" ")).apply()
    }

    private fun reload() {
        adapter.submit(if (q.isBlank()) (recents() + EmojiData.entries.map { it.first }).distinct() else EmojiData.search(q))
    }
}

class CursorPanel(private val svc: EverydayKeysService, private val pal: Palette) : LinearLayout(svc) {
    init {
        orientation = VERTICAL
        setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
        addView(buttonRow(
            b(R.drawable.ic_chevron_left, "Cursor left") { svc.moveCursor(android.view.KeyEvent.KEYCODE_DPAD_LEFT) },
            b(R.drawable.ic_chevron_up, "Cursor up") { svc.moveCursor(android.view.KeyEvent.KEYCODE_DPAD_UP) },
            b(R.drawable.ic_chevron_down, "Cursor down") { svc.moveCursor(android.view.KeyEvent.KEYCODE_DPAD_DOWN) },
            b(R.drawable.ic_chevron_right, "Cursor right") { svc.moveCursor(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) },
        ), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(buttonRow(
            b(R.drawable.ic_select_all, "Select all") { svc.edit(android.R.id.selectAll) },
            b(R.drawable.ic_cut, "Cut") { svc.edit(android.R.id.cut) },
            b(R.drawable.ic_copy, "Copy") { svc.edit(android.R.id.copy) },
            b(R.drawable.ic_paste, "Paste") { svc.edit(android.R.id.paste) },
        ), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(buttonRow(
            b(R.drawable.ic_undo, "Undo") { svc.undoRedo(false) },
            b(R.drawable.ic_redo, "Redo") { svc.undoRedo(true) },
            b(R.drawable.ic_chevron_left, "Start of line") { svc.moveCursor(android.view.KeyEvent.KEYCODE_MOVE_HOME) },
            b(R.drawable.ic_keyboard, "Back to keyboard") { svc.showLetters() },
        ), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun b(icon: Int, desc: String, action: () -> Unit) = Triple(icon, desc, action)

    private fun buttonRow(vararg items: Triple<Int, String, () -> Unit>): LinearLayout {
        val row = LinearLayout(svc).apply { orientation = HORIZONTAL }
        items.forEach { (icon, desc, action) ->
            val b = ImageButton(svc).apply {
                setImageResource(icon); setColorFilter(pal.text); contentDescription = desc
                background = roundBg(pal.func, dp(10f))
                setOnClickListener { action() }
            }
            row.addView(b, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dpi(3f), dpi(3f), dpi(3f), dpi(3f)) })
        }
        return row
    }
}

class ConvertPanel(private val svc: EverydayKeysService, private val pal: Palette) : LinearLayout(svc) {
    private var cat = "Length"
    private var from = 0
    private var to = 1
    private val value: Double = svc.numberBeforeCursor() ?: 1.0
    private val chips = LinearLayout(svc)
    private val result = svc.textView("", pal.text, 22f, true)

    init {
        orientation = VERTICAL
        setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        val head = LinearLayout(svc).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(svc.textView("Unit converter", pal.text, 16f, true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        head.addView(svc.iconBtn(R.drawable.ic_check, pal.onAccent, "Insert result") { insert() }.apply { background = roundBg(pal.accent, dp(20f)) })
        head.addView(svc.iconBtn(R.drawable.ic_keyboard, pal.text, "Back to keyboard") { svc.view?.setMode(KeyboardView.Mode.NUMPAD) })
        addView(head)
        chips.orientation = VERTICAL
        addView(chips)
        addView(result, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8f) })
        render()
    }

    private fun units() = Conv.categories[cat]!!.map { it.first }

    private fun chipRow(items: List<String>, sel: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(svc).apply { orientation = HORIZONTAL }
        items.forEachIndexed { i, t ->
            val on = i == sel
            row.addView(svc.textView(t, if (on) pal.onAccent else pal.text, 14f).apply {
                background = roundBg(if (on) pal.accent else pal.func, dp(14f))
                setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f))
                setOnClickListener { onPick(i) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = dpi(6f) })
        }
        return HorizontalScrollView(svc).apply { isHorizontalScrollBarEnabled = false; addView(row); setPadding(0, dpi(4f), 0, dpi(4f)) }
    }

    private fun render() {
        chips.removeAllViews()
        val cats = Conv.categories.keys.toList()
        chips.addView(chipRow(cats, cats.indexOf(cat)) { cat = cats[it]; from = 0; to = 1; render() })
        chips.addView(svc.textView("From", pal.sub, 12f))
        chips.addView(chipRow(units(), from) { from = it; render() })
        chips.addView(svc.textView("To", pal.sub, 12f))
        chips.addView(chipRow(units(), to) { to = it; render() })
        result.text = "${Calc.format(value)} ${units()[from]} = ${out()} ${units()[to]}"
    }

    private fun out() = Calc.format(Conv.convert(cat, units()[from], units()[to], value))

    private fun insert() { svc.insertText(out()); svc.view?.setMode(KeyboardView.Mode.NUMPAD) }
}
