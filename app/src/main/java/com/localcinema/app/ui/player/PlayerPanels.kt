package com.localcinema.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localcinema.app.ui.common.PlayerSwitch
import com.localcinema.app.ui.theme.PlayerColors

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
                    text = "调整字幕相对画面的快慢",
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
