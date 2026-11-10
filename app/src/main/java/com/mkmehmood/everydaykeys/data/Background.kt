package com.mkmehmood.everydaykeys.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max

/** Keyboard background photo, stored privately inside the app. */
object KbBackground {
    private var cached: ImageBitmap? = null
    private var stamp = -1L

    fun file(ctx: Context) = File(ctx.filesDir, "kb_bg.jpg")

    fun load(ctx: Context): ImageBitmap? {
        val f = file(ctx)
        if (!f.exists()) { cached = null; stamp = -1; return null }
        if (cached != null && f.lastModified() == stamp) return cached
        return runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull().also { cached = it; stamp = f.lastModified() }
    }

    fun delete(ctx: Context) { file(ctx).delete(); cached = null; stamp = -1 }

    /** Decodes a picked photo, downscaled so the longest side is about [maxDim] pixels, honouring rotation. */
    fun decode(ctx: Context, uri: Uri, maxDim: Int = 2048): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val big = max(info.size.width, info.size.height)
                if (big > maxDim) { val k = maxDim.toFloat() / big; dec.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1)) }
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxDim) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    }.getOrNull()

    /** Where the photo lands inside a W x H frame: zoom 1 just covers the frame; offsets are fractions of the frame size. */
    class Placed(val left: Float, val top: Float, val w: Float, val h: Float)

    fun place(frameW: Float, frameH: Float, bw: Int, bh: Int, zoom: Float, offFx: Float, offFy: Float): Placed {
        val s = max(frameW / bw, frameH / bh) * zoom
        val dw = bw * s; val dh = bh * s
        val maxX = ((dw - frameW) / 2f / frameW).coerceAtLeast(0f)
        val maxY = ((dh - frameH) / 2f / frameH).coerceAtLeast(0f)
        val fx = offFx.coerceIn(-maxX, maxX); val fy = offFy.coerceIn(-maxY, maxY)
        return Placed((frameW - dw) / 2f + fx * frameW, (frameH - dh) / 2f + fy * frameH, dw, dh)
    }

    fun clampX(frameW: Float, frameH: Float, bw: Int, bh: Int, zoom: Float, v: Float): Float {
        val s = max(frameW / bw, frameH / bh) * zoom
        val m = ((bw * s - frameW) / 2f / frameW).coerceAtLeast(0f); return v.coerceIn(-m, m)
    }
    fun clampY(frameW: Float, frameH: Float, bw: Int, bh: Int, zoom: Float, v: Float): Float {
        val s = max(frameW / bw, frameH / bh) * zoom
        val m = ((bh * s - frameH) / 2f / frameH).coerceAtLeast(0f); return v.coerceIn(-m, m)
    }
}
