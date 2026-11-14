package com.mkmehmood.everydaykeys.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.GeminiClient
import com.mkmehmood.everydaykeys.data.KbBackground
import com.mkmehmood.everydaykeys.data.SettingsRepo
import com.mkmehmood.everydaykeys.design.Dim
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

private sealed interface ImageStudioState {
    data object Idle : ImageStudioState
    data object Loading : ImageStudioState
    data class Error(val message: String) : ImageStudioState
}

/** Create or edit images from text prompts using gemini-nano-banana-2.1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var prompt by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultCaption by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<ImageStudioState>(ImageStudioState.Idle) }

    val activePreview = resultBitmap ?: sourceBitmap
    val isEditMode = sourceBitmap != null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { KbBackground.decode(ctx, uri) }
                if (decoded == null) {
                    Snack.post("Could not open that image.")
                } else {
                    sourceBitmap = decoded
                    resultBitmap = null
                    resultCaption = null
                    state = ImageStudioState.Idle
                }
            }
        }
    }

    val saveDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bmp = resultBitmap ?: sourceBitmap ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(uri)?.use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } ?: false
                    }.getOrDefault(false)
                }
                Snack.post(if (ok) "Image saved" else "Could not save image")
            }
        }
    }

    fun generate() {
        val clean = prompt.trim()
        if (clean.isEmpty()) {
            state = ImageStudioState.Error("Enter a text prompt to create or edit an image.")
            return
        }
        scope.launch {
            state = ImageStudioState.Loading
            try {
                val res = GeminiClient.generateOrEditImage(
                    prompt = clean,
                    inputBitmap = sourceBitmap,
                    aspectRatio = aspectRatio
                )
                resultBitmap = res.bitmap
                resultCaption = res.caption
                state = ImageStudioState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiClient.Failure) {
                state = ImageStudioState.Error(e.message ?: "Image generation failed.")
            } catch (e: Exception) {
                state = ImageStudioState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    fun applyAsBackground(bmp: Bitmap) {
        scope.launch {
            val dm = ctx.resources.displayMetrics
            val targetAspect = dm.widthPixels / (320f * dm.density)
            val outW = min(1080, dm.widthPixels)
            val outH = (outW / targetAspect).toInt().coerceAtLeast(1)
            withContext(Dispatchers.IO) {
                val scale = max(outW.toFloat() / bmp.width, outH.toFloat() / bmp.height)
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val left = (outW - drawW) / 2f
                val top = (outH - drawH) / 2f
                val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                AndroidCanvas(out).drawBitmap(
                    bmp,
                    null,
                    RectF(left, top, left + drawW, top + drawH),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                FileOutputStream(KbBackground.file(ctx)).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            SettingsRepo.set(ctx, "bgOn", "true")
            SettingsRepo.set(ctx, "bgVersion", System.currentTimeMillis().toString())
            Snack.post("Applied as keyboard background!")
        }
    }

    val createIdeas = remember {
        listOf(
            "Aurora over misty mountains",
            "Minimalist pastel wave gradient",
            "Warm desert sunset dunes",
            "Soft dark geometric waves"
        )
    }
    val editIdeas = remember {
        listOf(
            "Make it warm golden hour lighting",
            "Convert to watercolor illustration",
            "Add subtle glowing neon rain",
            "Soft dreamy bokeh background"
        )
    }
    val ratios = remember { listOf("16:9" to "Keyboard (16:9)", "1:1" to "Square (1:1)", "4:3" to "4:3", "9:16" to "9:16") }

    SettingsScaffold("Create & edit images", onBack = { nav.popBackStack() }) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode badge + picker action
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isEditMode) cs.tertiaryContainer else cs.primaryContainer,
                    contentColor = if (isEditMode) cs.onTertiaryContainer else cs.onPrimaryContainer
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.ic_sparkle, null, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isEditMode) "Edit mode · Nano Banana 2.1" else "Create mode · Nano Banana 2.1",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (sourceBitmap != null) {
                    TextButton(onClick = { sourceBitmap = null; resultBitmap = null; resultCaption = null }) {
                        Text("Clear photo")
                    }
                }
            }

            // Image preview canvas
            Surface(
                Modifier.fillMaxWidth().heightIn(min = 210.dp, max = 320.dp),
                shape = CardShape,
                color = cs.surfaceContainerHigh,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f))
            ) {
                Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                    if (activePreview != null) {
                        Image(
                            bitmap = activePreview.asImageBitmap(),
                            contentDescription = if (resultBitmap != null) "Generated or edited image" else "Source photo for editing",
                            modifier = Modifier.fillMaxSize().clip(CardShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Tile(R.drawable.ic_sparkle, 52.dp, filled = true)
                            Text("Create or edit with Gemini", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text(
                                "Describe an image to generate from scratch, or pick a photo to edit with a text prompt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (state is ImageStudioState.Loading) {
                        Box(
                            Modifier.fillMaxSize().background(cs.scrim.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(shape = Dim.Card, color = cs.surfaceContainerHigh) {
                                Column(
                                    Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = cs.primary)
                                    Text(
                                        "Generating with Gemini Nano Banana 2.1…\n(may take 30–60s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error display
            val err = (state as? ImageStudioState.Error)?.message
            AnimatedVisibility(visible = err != null) {
                if (err != null) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = Dim.Field,
                        color = cs.errorContainer,
                        contentColor = cs.onErrorContainer
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ic(R.drawable.ic_info, null, cs.onErrorContainer, 20.dp)
                            Text(err, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }

            // Optional caption from model
            if (!resultCaption.isNullOrBlank()) {
                Surface(Modifier.fillMaxWidth(), shape = Dim.Field, color = cs.surfaceContainerLow) {
                    Text(
                        resultCaption.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Prompt input
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().testTag("image_studio_prompt_input"),
                shape = Dim.Field,
                minLines = 2,
                maxLines = 4,
                label = { Text(if (isEditMode) "Describe how to edit this photo" else "Describe the image to create") },
                placeholder = { Text(if (isEditMode) "e.g. Change the sky to a vivid purple sunset" else "e.g. Minimalist misty mountain peaks at dawn") },
                trailingIcon = if (prompt.isNotEmpty()) ({
                    IconButton(onClick = { prompt = "" }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Ic(R.drawable.ic_close, "Clear prompt")
                    }
                }) else null
            )

            // Quick prompt chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(if (isEditMode) editIdeas else createIdeas) { idea ->
                    SuggestionChip(
                        onClick = { prompt = idea },
                        label = { Text(idea, style = MaterialTheme.typography.labelMedium) },
                        shape = CircleShape
                    )
                }
            }

            // Aspect ratio chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ratios) { (value, label) ->
                    FilterChip(
                        selected = aspectRatio == value,
                        onClick = { aspectRatio = value },
                        label = { Text(label) },
                        shape = Dim.Chip,
                        leadingIcon = if (aspectRatio == value) ({
                            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize))
                        }) else null
                    )
                }
            }

            // Primary actions: Pick photo & Generate
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalButton(
                    if (sourceBitmap == null) "Pick photo" else "Change photo",
                    R.drawable.ic_upload,
                    Modifier.weight(1f).testTag("image_studio_pick_button")
                ) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Button(
                    onClick = { generate() },
                    enabled = state !is ImageStudioState.Loading,
                    shape = CircleShape,
                    modifier = Modifier.weight(1.2f).heightIn(min = 44.dp).testTag("image_studio_generate_button")
                ) {
                    Ic(R.drawable.ic_sparkle, null, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEditMode) "Edit image" else "Create image", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Post-generation actions
            if (resultBitmap != null) {
                GroupCard {
                    ListRow(
                        "Continue editing this result",
                        Modifier.clickable {
                            sourceBitmap = resultBitmap
                            resultBitmap = null
                            resultCaption = null
                            prompt = ""
                        }.testTag("image_studio_continue_edit"),
                        sub = "Use the generated image as your next edit base",
                        leading = { Tile(R.drawable.ic_edit) },
                        trailing = { Ic(R.drawable.ic_right, null, cs.onSurfaceVariant) }
                    )
                    RowDivider()
                    ListRow(
                        "Use as keyboard background",
                        Modifier.clickable { resultBitmap?.let(::applyAsBackground) }.testTag("image_studio_use_bg"),
                        sub = "Applies immediately to Everyday Keys",
                        leading = { Tile(R.drawable.ic_keyboard) },
                        trailing = { Ic(R.drawable.ic_check, null, cs.primary) }
                    )
                    RowDivider()
                    ListRow(
                        "Save image to device",
                        Modifier.clickable {
                            saveDocLauncher.launch("everydaykeys-${System.currentTimeMillis()}.png")
                        }.testTag("image_studio_save"),
                        sub = "Export as high-resolution PNG",
                        leading = { Tile(R.drawable.ic_download) },
                        trailing = { Ic(R.drawable.ic_right, null, cs.onSurfaceVariant) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
