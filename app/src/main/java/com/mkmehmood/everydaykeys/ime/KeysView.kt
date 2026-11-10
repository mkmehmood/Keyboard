package com.mkmehmood.everydaykeys.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mkmehmood.everydaykeys.R
import kotlin.math.abs
import kotlin.math.hypot

fun View.dp(v: Float) = v * resources.displayMetrics.density
fun Context.dpi(v: Float) = (v * resources.displayMetrics.density).toInt()
fun View.dpi(v: Float) = context.dpi(v)

/** Draws a grid of keys on one canvas so touch, long-press, glide and spacebar drag stay consistent. */
class KeysView(ctx: Context) : View(ctx) {
    interface Listener {
        fun onKey(k: Key, text: String)
        fun onGlide(path: String)
        fun onSpaceDrag(steps: Int)
        fun onTouchFeedback()
    }

    private class Cell(val key: Key, val r: RectF, val hit: RectF)

    var listener: Listener? = null
    var palette: Palette = Palette(0xFFD6DCE4.toInt(), -1, 0xFFBAC4D0.toInt(), 0xFF17202A.toInt(), 0xFF5B6775.toInt(), 0xFF0F766E.toInt(), -1, false)
        set(v) { field = v; invalidate() }
    var rows: List<List<Key>> = emptyList()
        set(v) { field = v; rebuild(); requestLayout(); invalidate() }
    var rowH = context.dpi(52f)
    var textPx = 20f * resources.displayMetrics.scaledDensity
    var borders = false
    var longPressMs = 350L
    var accentPopup = true
    var preview = true
    var glideEnabled = false
    var cursorSens = 0.5f
    var isLetters = false
    var enterIcon = R.drawable.ic_enter
    var shift = 0
        set(v) { field = v; invalidate() }

    private var cells: List<Cell> = emptyList()
    private val gap = context.dpi(4f).toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dpi(4f).toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val handler = Handler(Looper.getMainLooper())
    private val icons = HashMap<Int, android.graphics.drawable.Drawable>()

    private var pressed: Cell? = null
    private var downX = 0f
    private var lastDragX = 0f
    private var spaceDragging = false
    private var consumed = false
    private var gliding = false
    private var glideStart: Cell? = null
    private val glidePath = StringBuilder()
    private val trailPath = Path()
    private var travelled = 0f
    private var lastX = 0f
    private var lastY = 0f

    private var altPopup: PopupWindow? = null
    private var altChars: List<String> = emptyList()
    private var altRanges: List<Pair<Float, Float>> = emptyList()
    private var altViews: List<TextView> = emptyList()
    private var altSel = 0
    private var altLeft = 0f
    private var previewPw: PopupWindow? = null
    private var previewTv: TextView? = null

    private fun rebuild() {
        requestLayout()
        post { buildCells() }
        buildCells()
    }

    private fun buildCells() {
        val w = width.toFloat()
        if (w <= 0f) { cells = emptyList(); return }
        val out = ArrayList<Cell>()
        rows.forEachIndexed { ri, row ->
            val sum = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            val top = ri * rowH.toFloat()
            row.forEach { k ->
                val kw = w * k.weight / sum
                if (!k.spacer) out.add(Cell(k, RectF(x + gap / 2, top + gap / 2, x + kw - gap / 2, top + rowH - gap / 2), RectF(x, top, x + kw, top + rowH)))
                x += kw
            }
        }
        cells = out
        invalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), rows.size * rowH)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { buildCells() }

    private fun icon(id: Int) = icons.getOrPut(id) { ContextCompat.getDrawable(context, id)!!.mutate() }

    private fun label(k: Key): String = if (k.letter && shift > 0) k.text.uppercase() else k.text

    override fun onDraw(c: Canvas) {
        val radius = dp(7f)
        for (cell in cells) {
            val k = cell.key
            val isPressed = cell === pressed
            val base = when (k.style) { 1 -> palette.func; 2 -> palette.accent; else -> palette.key }
            paint.style = Paint.Style.FILL
            paint.color = if (isPressed) blend(base, palette.accent, 0.35f) else base
            c.drawRoundRect(cell.r, radius, radius, paint)
            if (borders && k.style != 2) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = palette.sub and 0x55FFFFFF
                c.drawRoundRect(cell.r, radius, radius, paint)
            }
            val fg = if (k.style == 2) palette.onAccent else palette.text
            var iconRes = k.icon
            if (k.act == Act.SHIFT) iconRes = when (shift) { 0 -> R.drawable.ic_shift; 1 -> R.drawable.ic_shift_on; else -> R.drawable.ic_caps }
            if (k.act == Act.ENTER) iconRes = enterIcon
            if (iconRes != 0) {
                val d = icon(iconRes)
                d.setTint(fg)
                val s = dp(22f).toInt()
                val cx = cell.r.centerX().toInt(); val cy = cell.r.centerY().toInt()
                d.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                d.draw(c)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = fg
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT
                paint.textSize = if (k.style == 1 || k.letter.not() && k.text.length == 1 && k.text[0].isLetterOrDigit().not()) textPx * 0.9f else textPx
                val fm = paint.fontMetrics
                c.drawText(label(k), cell.r.centerX(), cell.r.centerY() - (fm.ascent + fm.descent) / 2, paint)
            }
        }
        if (gliding) { trail.color = palette.accent; c.drawPath(trailPath, trail) }
    }

    private fun blend(a: Int, b: Int, t: Float) = androidx.core.graphics.ColorUtils.blendARGB(a, b, t)

    private fun hit(x: Float, y: Float): Cell? {
        cells.firstOrNull { it.hit.contains(x, y) }?.let { return it }
        return cells.minByOrNull { hypot(it.r.centerX() - x, it.r.centerY() - y) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(e.x, e.y)
            MotionEvent.ACTION_MOVE -> move(e.x, e.y, e.rawX)
            MotionEvent.ACTION_UP -> up()
            MotionEvent.ACTION_CANCEL -> { cancelAll() }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // second finger: type immediately so fast two-thumb typing is not lost
                val i = e.actionIndex
                hit(e.getX(i), e.getY(i))?.let { listener?.onTouchFeedback(); fire(it) }
            }
        }
        return true
    }

    private fun down(x: Float, y: Float) {
        val cell = hit(x, y) ?: return
        pressed = cell; downX = x; lastDragX = x; lastX = x; lastY = y
        spaceDragging = false; consumed = false; gliding = false; travelled = 0f
        trailPath.reset(); trailPath.moveTo(x, y)
        glidePath.setLength(0)
        glideStart = if (isLetters && glideEnabled && cell.key.letter) cell else null
        if (glideStart != null) glidePath.append(cell.key.text.lowercase())
        listener?.onTouchFeedback()
        showPreview(cell)
        handler.postDelayed(longPress, longPressMs)
        if (cell.key.act == Act.DELETE) handler.postDelayed(repeatDelete, 420)
        invalidate()
    }

    private val longPress = Runnable {
        val cell = pressed ?: return@Runnable
        val k = cell.key
        if (k.longAct != Act.NONE) { consumed = true; hidePreview(); listener?.onKey(Key(act = k.longAct), ""); return@Runnable }
        if (k.alts.isNotEmpty() && accentPopup) { consumed = true; hidePreview(); showAlt(cell) }
    }

    private val repeatDelete = object : Runnable {
        override fun run() {
            val cell = pressed ?: return
            if (cell.key.act != Act.DELETE) return
            consumed = true
            listener?.onKey(cell.key, "")
            handler.postDelayed(this, 45)
        }
    }

    private fun move(x: Float, y: Float, rawX: Float) {
        if (altPopup != null) { highlightAlt(rawX); return }
        val cell = pressed ?: return
        if (cell.key.act == Act.SPACE) {
            if (abs(x - downX) > dp(14f) || spaceDragging) {
                spaceDragging = true; consumed = true
                handler.removeCallbacks(longPress)
                val stepPx = dp(24f) * (1.6f - cursorSens)
                val dx = x - lastDragX
                if (abs(dx) >= stepPx) {
                    val steps = (dx / stepPx).toInt()
                    lastDragX += steps * stepPx
                    listener?.onSpaceDrag(steps)
                }
            }
            return
        }
        if (glideStart != null) {
            travelled += hypot(x - lastX, y - lastY)
            sampleGlide(lastX, lastY, x, y)
            trailPath.lineTo(x, y)
            lastX = x; lastY = y
            if (!gliding && travelled > dp(36f) && glidePath.length >= 2) {
                gliding = true; consumed = true
                handler.removeCallbacks(longPress); hidePreview()
            }
            if (gliding) invalidate()
            return
        }
        val now = hit(x, y)
        if (now != null && now !== cell && !consumed) {
            pressed = now; hidePreview(); showPreview(now)
            handler.removeCallbacks(longPress); handler.postDelayed(longPress, longPressMs)
            invalidate()
        }
    }

    private fun sampleGlide(x0: Float, y0: Float, x1: Float, y1: Float) {
        val d = hypot(x1 - x0, y1 - y0)
        val steps = (d / dp(6f)).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val cell = hit(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps) ?: continue
            if (!cell.key.letter) continue
            val c = cell.key.text.lowercase()
            if (glidePath.isEmpty() || glidePath.last().toString() != c) glidePath.append(c)
        }
    }

    private fun up() {
        handler.removeCallbacks(longPress); handler.removeCallbacks(repeatDelete)
        hidePreview()
        val cell = pressed
        if (altPopup != null) {
            val sel = altChars.getOrNull(altSel)
            dismissAlt()
            if (sel != null && cell != null) listener?.onKey(Key(text = sel, letter = false), sel)
        } else if (gliding) {
            listener?.onGlide(glidePath.toString())
        } else if (cell != null && !consumed) {
            fire(cell)
        }
        pressed = null; gliding = false; glideStart = null
        invalidate()
    }

    private fun cancelAll() {
        handler.removeCallbacks(longPress); handler.removeCallbacks(repeatDelete)
        hidePreview(); dismissAlt(); pressed = null; gliding = false; invalidate()
    }

    private fun fire(cell: Cell) {
        val k = cell.key
        listener?.onKey(k, if (k.letter && shift > 0) k.text.uppercase() else k.text)
    }

    // ---- popups ----
    private fun showPreview(cell: Cell) {
        if (!preview) return
        val k = cell.key
        if (k.text.isEmpty() || k.act != Act.NONE || k.style != 0) return
        try {
            val tv = previewTv ?: TextView(context).also {
                it.gravity = Gravity.CENTER; it.textSize = 26f
                previewTv = it
            }
            tv.text = label(k)
            tv.setTextColor(palette.text)
            tv.background = GradientDrawable().apply { setColor(palette.key); cornerRadius = dp(10f); setStroke(dpi(1f), palette.accent) }
            val w = dpi(52f); val h = dpi(60f)
            tv.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            val loc = IntArray(2); getLocationOnScreen(loc)
            val x = (loc[0] + cell.r.centerX() - w / 2).toInt()
            val y = (loc[1] + cell.r.top - h - dp(4f)).toInt()
            val pw = previewPw ?: PopupWindow(tv, w, h).also { it.isClippingEnabled = false; it.isTouchable = false; it.elevation = dp(6f); previewPw = it }
            if (pw.isShowing) pw.update(x, y, w, h) else pw.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        } catch (_: Exception) { }
    }

    private fun hidePreview() { try { previewPw?.dismiss() } catch (_: Exception) { } }

    private fun showAlt(cell: Cell) {
        try {
            val raw = cell.key.alts
            val txt = if (shift > 0) raw.uppercase() else raw
            altChars = txt.map { it.toString() }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
                background = GradientDrawable().apply { setColor(palette.key); cornerRadius = dp(12f); setStroke(dpi(1f), palette.accent) }
            }
            altViews = altChars.map { t ->
                TextView(context).apply {
                    text = t; textSize = 22f; gravity = Gravity.CENTER; setTextColor(palette.text)
                    layoutParams = LinearLayout.LayoutParams(dpi(40f), dpi(48f))
                }.also { row.addView(it) }
            }
            val w = dpi(8f) + altChars.size * dpi(40f); val h = dpi(56f)
            val loc = IntArray(2); getLocationOnScreen(loc)
            val screenW = resources.displayMetrics.widthPixels
            val x = (loc[0] + cell.r.centerX() - w / 2).toInt().coerceIn(0, (screenW - w).coerceAtLeast(0))
            val y = (loc[1] + cell.r.top - h - dp(6f)).toInt()
            altLeft = x + dp(4f)
            altRanges = altChars.indices.map { i -> (altLeft + i * dpi(40f)) to (altLeft + (i + 1) * dpi(40f)) }
            val pw = PopupWindow(row, w, h).also { it.isClippingEnabled = false; it.isTouchable = false; it.elevation = dp(8f) }
            pw.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            altPopup = pw
            altSel = 0; paintAlt()
        } catch (_: Exception) { altPopup = null }
    }

    private fun highlightAlt(rawX: Float) {
        val i = altRanges.indexOfFirst { rawX >= it.first && rawX < it.second }
        val sel = when { i >= 0 -> i; rawX < altLeft -> 0; else -> altChars.size - 1 }
        if (sel != altSel) { altSel = sel; paintAlt() }
    }

    private fun paintAlt() {
        altViews.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == altSel) palette.onAccent else palette.text)
            tv.background = if (i == altSel) GradientDrawable().apply { setColor(palette.accent); cornerRadius = dp(8f) } else null
        }
    }

    private fun dismissAlt() { try { altPopup?.dismiss() } catch (_: Exception) { }; altPopup = null }

    override fun onDetachedFromWindow() { cancelAll(); super.onDetachedFromWindow() }
}
