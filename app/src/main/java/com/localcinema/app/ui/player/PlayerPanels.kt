package com.localcinema.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localcinema.app.ui.common.PlayerSwitch
import com.localcinema.app.ui.theme.PlayerColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFF, // 白
    0xFFFFE082, // 黄
    0xFF80DEEA, // 青
    0xFFA5D6A7  // 绿
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSheet(
    prefs: SubtitlePrefs,
    tracks: List<TrackOption>,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectTrack: (Int?) -> Unit,
    onPickExternal: () -> Unit,
    onFontSize: (Float) -> Unit,
    onColor: (Long) -> Unit,
    onDelay: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlayerColors.SurfaceElevated,
        contentColor = PlayerColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字幕",
                    fontSize = 15.sp,
                    color = PlayerColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                PlayerSwitch(checked = prefs.enabled, onCheckedChange = onToggleEnabled)
            }

            Spacer(Modifier.height(10.dp))
            PanelLabel("字幕轨道")

            // 关闭
            TrackRow(
                label = "关闭字幕",
                selected = selectedIndex == null,
                onClick = { onSelectTrack(null) }
            )
            tracks.forEach { track ->
                TrackRow(
                    label = track.label,
                    selected = selectedIndex == track.index,
                    onClick = { onSelectTrack(track.index) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onPickExternal)
                    .padding(vertical = 11.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = PlayerColors.Accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "选择外部字幕文件…",
                    fontSize = 12.5.sp,
                    color = PlayerColors.Accent
                )
            }

            HorizontalDivider(color = PlayerColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            PanelLabel("字号  ${prefs.fontSizeSp.toInt()}")
            Slider(
                value = prefs.fontSizeSp,
                onValueChange = onFontSize,
                valueRange = 10f..30f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = PlayerColors.Accent,
                    inactiveTrackColor = PlayerColors.PlayerTrackInactive
                ),
                modifier = Modifier.height(26.dp)
            )

            PanelLabel("颜色")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SUBTITLE_COLORS.forEach { argb ->
                    val selected = prefs.colorArgb == argb
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, PlayerColors.Accent, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onColor(argb) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            PanelLabel("延迟  ${formatDelay(prefs.delayMs)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DelayButton("-1s") { onDelay(-1_000) }
                DelayButton("-0.5s") { onDelay(-500) }
                DelayButton("+0.5s") { onDelay(500) }
                DelayButton("+1s") { onDelay(1_000) }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "延迟仅对外挂字幕生效",
                    fontSize = 9.5.sp,
                    color = PlayerColors.TextTertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    tracks: List<TrackOption>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlayerColors.SurfaceElevated,
        contentColor = PlayerColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text(text = "音轨", fontSize = 15.sp, color = PlayerColors.TextPrimary)
            Spacer(Modifier.height(8.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = "当前文件只有一条音轨",
                    fontSize = 12.sp,
                    color = PlayerColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                tracks.forEach { track ->
                    TrackRow(
                        label = track.label,
                        selected = selectedIndex == track.index,
                        onClick = { onSelect(track.index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.5.sp,
        color = PlayerColors.TextSecondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = if (selected) PlayerColors.Accent else PlayerColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = PlayerColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DelayButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlayerColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, fontSize = 11.5.sp, color = PlayerColors.TextPrimary)
    }
}

private fun formatDelay(ms: Long): String {
    if (ms == 0L) return "0.0s"
    val seconds = ms / 1000.0
    return if (seconds > 0) "+%.1fs".format(seconds) else "%.1fs".format(seconds)
}

// ---------------------------------------------------------------------------
// 倍速面板
// ---------------------------------------------------------------------------

private val SPEED_PRESETS = listOf(50, 75, 100, 125, 150, 200, 300, 400, 500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    percent: Int,
    onDismiss: () -> Unit,
    onStep: (Int) -> Unit,
    onSet: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlayerColors.SurfaceElevated,
        contentColor = PlayerColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放速度",
                    fontSize = 13.sp,
                    color = PlayerColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "恢复 1.0x",
                    fontSize = 10.5.sp,
                    color = PlayerColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSet(100) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StepButton(
                    label = "−",
                    enabled = percent > 50,
                    onClick = { onStep(-10) },
                    scope = scope
                )
                Text(
                    text = formatSpeed(percent),
                    fontSize = 28.sp,
                    color = PlayerColors.Accent,
                    modifier = Modifier.width(86.dp),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { value -> onSet((value / 10f).roundToInt() * 10) },
                    valueRange = 50f..500f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = PlayerColors.Accent,
                        inactiveTrackColor = PlayerColors.PlayerTrackInactive
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                )
                StepButton(
                    label = "+",
                    enabled = percent < 500,
                    onClick = { onStep(10) },
                    scope = scope
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SPEED_PRESETS.forEach { preset ->
                    val selected = percent == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (selected) PlayerColors.Accent else PlayerColors.ChipBg
                            )
                            .clickable { onSet(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = presetLabel(preset),
                            fontSize = 10.5.sp,
                            color = if (selected) {
                                PlayerColors.ChipActiveInk
                            } else {
                                PlayerColors.PlayerToolTint
                            }
                        )
                    }
                }
            }

            Text(
                text = "长按 − / + 可连续调节，每次 0.1x，范围 0.5x – 5.0x",
                fontSize = 9.5.sp,
                color = PlayerColors.TextTertiary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/** 单击步进 0.1x；按住 220ms 后每 80ms 连续步进 */
@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(PlayerColors.StepperBg.copy(alpha = if (enabled) 1f else 0.45f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        var firedRepeat = false
                        val job = scope.launch {
                            delay(220)
                            firedRepeat = true
                            while (isActive) {
                                onClick()
                                delay(80)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (!firedRepeat) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = if (enabled) Color(0xFFE8E8F0) else Color(0x66E8E8F0)
        )
    }
}

private fun presetLabel(percent: Int): String =
    if (percent % 10 == 0) String.format("%.1f", percent / 100f)
    else String.format("%.2f", percent / 100f)
