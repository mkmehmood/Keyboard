package com.mkmehmood.everydaykeys.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation.NavController
import com.mkmehmood.everydaykeys.data.KbBackground
import com.mkmehmood.everydaykeys.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.min

/** Pick a photo, position and zoom it into the keyboard shape, then set dimming, blur and key opacity. */
@Composable
fun BackgroundScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var fresh by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offFx by remember { mutableFloatStateOf(0f) }
    var offFy by remember { mutableFloatStateOf(0f) }
    var dim by remember { mutableFloatStateOf(35f) }
    var blur by remember { mutableFloatStateOf(0f) }
    var keyAlpha by remember { mutableFloatStateOf(85f) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    // the keyboard is about 320dp tall; the frame has the same shape as the phone's keyboard area
    val dm = ctx.resources.displayMetrics
    val aspect = dm.widthPixels / (320f * dm.density)

    LaunchedEffect(Unit) {
        val p = SettingsRepo.flow(ctx).first()
        dim = p.int("bgDim").toFloat(); blur = p.int("bgBlur").toFloat(); keyAlpha = p.int("bgKeyAlpha").toFloat()
        src = withContext(Dispatchers.IO) { KbBackground.file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { KbBackground.decode(ctx, uri) }
            if (b == null) Toast.makeText(ctx, "Could not open that image", Toast.LENGTH_SHORT).show()
            else { src = b; fresh = true; zoom = 1f; offFx = 0f; offFy = 0f }
        }
    }

    fun clamp() {
        val b = src ?: return
        if (frame.width == 0) return
        offFx = KbBackground.clampX(frame.width.toFloat(), frame.height.toFloat(), b.width, b.height, zoom, offFx)
        offFy = KbBackground.clampY(frame.width.toFloat(), frame.height.toFloat(), b.width, b.height, zoom, offFy)
    }

    fun save() {
        val b = src
        if (b == null) { Toast.makeText(ctx, "Choose a photo first", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            if (fresh && frame.width > 0) withContext(Dispatchers.IO) {
                val outW = min(1080, dm.widthPixels); val outH = (outW / aspect).toInt()
                val k = outW / frame.width.toFloat()
                val pl = KbBackground.place(frame.width.toFloat(), frame.height.toFloat(), b.width, b.height, zoom, offFx, offFy)
                val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                AndroidCanvas(out).drawBitmap(b, null, RectF(pl.left * k, pl.top * k, (pl.left + pl.w) * k, (pl.top + pl.h) * k), Paint(Paint.FILTER_BITMAP_FLAG))
                FileOutputStream(KbBackground.file(ctx)).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            SettingsRepo.set(ctx, "bgDim", dim.toInt().toString()); SettingsRepo.set(ctx, "bgBlur", blur.toInt().toString())
            SettingsRepo.set(ctx, "bgKeyAlpha", keyAlpha.toInt().toString()); SettingsRepo.set(ctx, "bgOn", "true")
            SettingsRepo.set(ctx, "bgVersion", System.currentTimeMillis().toString())
            Toast.makeText(ctx, "Background saved", Toast.LENGTH_SHORT).show()
            nav.popBackStack()
        }
    }

    fun remove() = scope.launch {
        KbBackground.delete(ctx)
        SettingsRepo.set(ctx, "bgOn", "false"); SettingsRepo.set(ctx, "bgVersion", System.currentTimeMillis().toString())
        nav.popBackStack()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        TopBar("Background image") { nav.popBackStack() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2B323B)).onSizeChanged { frame = it }
                    .pointerInput(src) {
                        detectTransformGestures { _, pan, gz, _ ->
                            if (src != null && frame.width > 0) {
                                zoom = (zoom * gz).coerceIn(1f, 5f)
                                offFx += pan.x / frame.width; offFy += pan.y / frame.height
                                clamp()
                            }
                        }
                    }
            ) {
                val b = src
                if (b == null) Text("No photo yet", Modifier.align(Alignment.Center), color = Color.White.copy(alpha = .7f))
                else {
                    val img = remember(b) { b.asImageBitmap() }
                    Box(Modifier.matchParentSize().clipToBounds()) {
                        Canvas(Modifier.matchParentSize().then(if (blur > 0f) Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded) else Modifier)) {
                            val pl = KbBackground.place(size.width, size.height, b.width, b.height, zoom, offFx, offFy)
                            drawImage(img, IntOffset.Zero, IntSize(b.width, b.height),
                                IntOffset(pl.left.toInt(), pl.top.toInt()), IntSize(pl.w.toInt(), pl.h.toInt()))
                        }
                    }
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Color.Black.copy(alpha = dim / 100f))
                        // a sketch of the key rows so the key opacity can be judged
                        val rows = 4; val cols = 10; val padX = 8.dp.toPx(); val gap = 4.dp.toPx()
                        val top = size.height * 0.30f
                        val kw = (size.width - padX * 2 - gap * (cols - 1)) / cols
                        val kh = (size.height - top - 8.dp.toPx() - gap * (rows - 1)) / rows
                        for (r in 0 until rows) for (cI in 0 until cols) drawRoundRect(
                            Color.White.copy(alpha = keyAlpha / 100f), Offset(padX + cI * (kw + gap), top + r * (kh + gap)), Size(kw, kh), CornerRadius(8.dp.toPx())
                        )
                    }
                }
            }
            Text("Pinch to zoom and drag to position. The frame matches the keyboard.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Text(if (src == null) "Choose a photo" else "Choose another photo") }

            LabeledSlider("Zoom  %.1fx".format(zoom), zoom, 1f..5f) { zoom = it; clamp() }
            LabeledSlider("Dimming  ${dim.toInt()}%", dim, 0f..90f) { dim = it }
            LabeledSlider("Blur  ${blur.toInt()}  (Android 12 and newer)", blur, 0f..25f) { blur = it }
            LabeledSlider("Key opacity  ${keyAlpha.toInt()}%", keyAlpha, 30f..100f) { keyAlpha = it }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(::save, Modifier.weight(1f)) { Text("Save") }
                OutlinedButton({ remove() }, Modifier.weight(1f)) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) { Text(label); Slider(v, onChange, valueRange = range) }
}
