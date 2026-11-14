package com.mkmehmood.everydaykeys.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.KbBackground
import com.mkmehmood.everydaykeys.data.SettingsRepo
import com.mkmehmood.everydaykeys.design.MonoFontFamily
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
    val cs = MaterialTheme.colorScheme
    val scrimC = cs.scrim
    val keyC = if (cs.surface.luminance() < 0.5f) cs.surfaceContainerHighest else cs.surfaceContainerLowest
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { KbBackground.decode(ctx, uri) }
            if (b == null) Snack.post("Could not open that image")
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
        if (b == null) { Snack.post("Choose a photo first"); return }
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
            Snack.post("Background saved")
            nav.popBackStack()
        }
    }

    fun remove() = scope.launch {
        KbBackground.delete(ctx)
        SettingsRepo.set(ctx, "bgOn", "false"); SettingsRepo.set(ctx, "bgVersion", System.currentTimeMillis().toString())
        nav.popBackStack()
    }

    SettingsScaffold("Background image", onBack = { nav.popBackStack() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(aspect).clip(CardShape)
                    .background(cs.surfaceContainerHighest)
                    .border(BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)), CardShape)
                    .onSizeChanged { frame = it }
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
                if (b == null) {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Tile(R.drawable.ic_upload, 48.dp)
                        Text("No background image yet", color = cs.onSurface, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Choose a photo below to preview it behind your keys.",
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val img = remember(b) { b.asImageBitmap() }
                    Box(Modifier.matchParentSize().clipToBounds()) {
                        Canvas(Modifier.matchParentSize().then(if (blur > 0f) Modifier.blur(blur.dp, BlurredEdgeTreatment.Unbounded) else Modifier)) {
                            val pl = KbBackground.place(size.width, size.height, b.width, b.height, zoom, offFx, offFy)
                            drawImage(
                                img, IntOffset.Zero, IntSize(b.width, b.height),
                                IntOffset(pl.left.toInt(), pl.top.toInt()), IntSize(pl.w.toInt(), pl.h.toInt())
                            )
                        }
                    }
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(scrimC.copy(alpha = dim / 100f))
                        // a sketch of the key rows so the key opacity can be judged
                        val rows = 4; val cols = 10; val padX = 8.dp.toPx(); val gap = 4.dp.toPx()
                        val top = size.height * 0.30f
                        val kw = (size.width - padX * 2 - gap * (cols - 1)) / cols
                        val kh = (size.height - top - 8.dp.toPx() - gap * (rows - 1)) / rows
                        for (r in 0 until rows) for (cI in 0 until cols) drawRoundRect(
                            keyC.copy(alpha = keyAlpha / 100f), Offset(padX + cI * (kw + gap), top + r * (kh + gap)), Size(kw, kh), CornerRadius(8.dp.toPx())
                        )
                    }
                    Surface(
                        Modifier.align(Alignment.TopEnd).padding(10.dp),
                        shape = CircleShape,
                        color = cs.surfaceContainerHigh.copy(alpha = 0.88f),
                        contentColor = cs.onSurface
                    ) {
                        Text(
                            "%.1fx".format(zoom),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Text(
                "Pinch to zoom and drag to position. The frame matches the keyboard.",
                Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalButton(
                    if (src == null) "Choose photo" else "Change photo",
                    R.drawable.ic_upload,
                    Modifier.weight(1f).testTag("bg_choose_photo_button")
                ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                TonalButton(
                    "Create with AI",
                    R.drawable.ic_sparkle,
                    Modifier.weight(1f).testTag("bg_create_ai_button")
                ) { nav.navigate("image_studio") }
            }
            Spacer(Modifier.height(16.dp))

            GroupCard {
                LabeledSlider("Zoom", "%.1fx".format(zoom), zoom, 1f..5f) { zoom = it; clamp() }
                RowDivider()
                LabeledSlider("Dimming", "${dim.toInt()}%", dim, 0f..90f) { dim = it }
                RowDivider()
                LabeledSlider("Blur (Android 12+)", "${blur.toInt()} dp", blur, 0f..25f) { blur = it }
                RowDivider()
                LabeledSlider("Key opacity", "${keyAlpha.toInt()}%", keyAlpha, 30f..100f) { keyAlpha = it }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Save", R.drawable.ic_check, Modifier.weight(1f).testTag("bg_save_button")) { save() }
                SecondaryButton("Remove", R.drawable.ic_trash, Modifier.weight(1f).testTag("bg_remove_button")) { remove() }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, badge: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Surface(shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Slider(v, onChange, valueRange = range)
    }
}
