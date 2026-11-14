package com.mkmehmood.everydaykeys.design

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.GeminiClient
import com.mkmehmood.everydaykeys.data.GeminiClient.QuotaSnapshot
import com.mkmehmood.everydaykeys.data.GeminiClient.QuotaStatus
import kotlinx.coroutines.delay

/** Live UI view of [GeminiClient.quotaFlow] with an auto-ticking 1s countdown & 60s window counter. */
data class LiveQuotaView(
    val snapshot: QuotaSnapshot,
    val effectiveStatus: QuotaStatus,
    val rpm60: Int,
    val tpm60: Int,
    val cooldownSec: Int
)

@Composable
fun rememberLiveQuota(): LiveQuotaView {
    val snap by GeminiClient.quotaFlow.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val needsTicker = snap.rpmLast60s > 0 ||
        snap.tpmLast60s > 0 ||
        snap.cooldownUntilMs > nowMs ||
        snap.status == QuotaStatus.IN_FLIGHT

    LaunchedEffect(needsTicker, snap) {
        nowMs = System.currentTimeMillis()
        while (
            snap.status == QuotaStatus.IN_FLIGHT ||
            snap.cooldownUntilMs > nowMs ||
            GeminiClient.liveWindowCounts(nowMs).first > 0
        ) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val (rpm, tpm) = GeminiClient.liveWindowCounts(nowMs)
    val cdSec = ((snap.cooldownUntilMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(0)
    val status = when {
        snap.status == QuotaStatus.COOLDOWN && cdSec == 0 -> QuotaStatus.READY
        else -> snap.status
    }
    return LiveQuotaView(
        snapshot = snap,
        effectiveStatus = status,
        rpm60 = rpm,
        tpm60 = tpm,
        cooldownSec = cdSec
    )
}

/** Compact Material 3 pill showing live Gemini rate-limit status and rolling requests/min. */
@Composable
fun LiveQuotaPill(
    live: LiveQuotaView,
    pal: Palette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg, dot) = when (live.effectiveStatus) {
        QuotaStatus.UNCONFIGURED -> Triple(pal.trackOff, pal.sub, pal.off)
        QuotaStatus.READY -> Triple(pal.accentContainer, pal.onAccentContainer, pal.accent)
        QuotaStatus.IN_FLIGHT -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        QuotaStatus.COOLDOWN -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        QuotaStatus.QUOTA_EXHAUSTED -> Triple(cs.errorContainer, cs.onErrorContainer, cs.error)
    }
    val animBg by animateColorAsState(bg, label = "quotaPillBg")
    val animFg by animateColorAsState(fg, label = "quotaPillFg")
    val label = when (live.effectiveStatus) {
        QuotaStatus.UNCONFIGURED -> "No API key"
        QuotaStatus.IN_FLIGHT -> "Live · Calling…"
        QuotaStatus.COOLDOWN -> "Limit · ${live.cooldownSec}s"
        QuotaStatus.QUOTA_EXHAUSTED -> "Quota limit"
        QuotaStatus.READY -> if (live.rpm60 > 0) "Live · ${live.rpm60}/m" else "Live · Ready"
    }

    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = animFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    val baseMod = modifier
        .testTag("gemini_quota_pill")
        .semantics { contentDescription = "Gemini quota status: $label" }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = baseMod,
            shape = CircleShape,
            color = animBg,
            contentColor = animFg
        ) { content() }
    } else {
        Surface(
            modifier = baseMod,
            shape = CircleShape,
            color = animBg,
            contentColor = animFg
        ) { content() }
    }
}

/** Detailed Material 3 card showing live RPM, today's requests/images/tokens, cooldown, and AI Studio usage link. */
@Composable
fun LiveQuotaDetailsCard(
    live: LiveQuotaView,
    pal: Palette,
    ctx: Context,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val snap = live.snapshot
    val statusDesc = when (live.effectiveStatus) {
        QuotaStatus.UNCONFIGURED -> "GEMINI_API_KEY not configured"
        QuotaStatus.READY -> "API ready"
        QuotaStatus.IN_FLIGHT -> "Running ${snap.activeModel ?: "Gemini"}…"
        QuotaStatus.COOLDOWN -> "Rate-limit cooldown (${live.cooldownSec}s remaining)"
        QuotaStatus.QUOTA_EXHAUSTED -> "Quota unavailable / billing required"
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("gemini_quota_card"),
        shape = Dim.Card,
        color = pal.surface,
        border = BorderStroke(0.5.dp, pal.hairline.copy(alpha = 0.55f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Live Gemini Quota & Usage",
                        color = pal.text,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall
                    )
                    Text(
                        statusDesc,
                        color = pal.sub,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LiveQuotaPill(live, pal)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuotaMetricBox(
                    label = "60s RPM",
                    value = "${live.rpm60}",
                    sub = if (live.tpm60 > 0) "${formatCompact(live.tpm60.toLong())} tok" else "0 tok",
                    pal = pal,
                    modifier = Modifier.weight(1f)
                )
                QuotaMetricBox(
                    label = "Today",
                    value = "${snap.requestsToday}",
                    sub = "${snap.imagesToday} img",
                    pal = pal,
                    modifier = Modifier.weight(1f)
                )
                QuotaMetricBox(
                    label = "Tokens",
                    value = formatCompact(snap.tokensToday),
                    sub = snap.lastModel?.removePrefix("gemini-") ?: "idle",
                    pal = pal,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!snap.lastQuotaId.isNullOrBlank() || !snap.lastError.isNullOrBlank()) {
                val detail = buildString {
                    if (!snap.lastQuotaId.isNullOrBlank()) append("${snap.lastQuotaId}: ")
                    append(snap.lastError.orEmpty())
                }
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = Dim.Field,
                    color = if (live.effectiveStatus == QuotaStatus.QUOTA_EXHAUSTED) cs.errorContainer else cs.tertiaryContainer
                ) {
                    Text(
                        detail,
                        color = if (live.effectiveStatus == QuotaStatus.QUOTA_EXHAUSTED) cs.onErrorContainer else cs.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (compact) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "App-observed calls & response tokens",
                    color = pal.sub,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (live.effectiveStatus == QuotaStatus.COOLDOWN || live.effectiveStatus == QuotaStatus.QUOTA_EXHAUSTED) {
                        TextButton(
                            onClick = { GeminiClient.clearCooldown() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("gemini_clear_cooldown_button")
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (snap.requestsToday > 0) {
                        TextButton(
                            onClick = { GeminiClient.resetUsageStats() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("gemini_reset_stats_button")
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.dev/usage?tab=rate-limit"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("gemini_open_usage_button")
                    ) {
                        Text("AI Studio Usage", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaMetricBox(
    label: String,
    value: String,
    sub: String,
    pal: Palette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = Dim.Field,
        color = pal.trackOff.copy(alpha = 0.55f)
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, color = pal.sub, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                value,
                color = pal.text,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                maxLines = 1
            )
            Text(
                sub,
                color = pal.sub,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCompact(n: Long): String = when {
    n >= 1_000_000L -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000L -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}
