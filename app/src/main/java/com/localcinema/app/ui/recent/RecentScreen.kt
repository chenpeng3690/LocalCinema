package com.localcinema.app.ui.recent

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.localcinema.app.data.db.PlaybackStateDao
import com.localcinema.app.data.db.RecentRow
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.repo.PlaybackRepository
import com.localcinema.app.data.scanner.formatDuration
import com.localcinema.app.data.thumb.ThumbCache
import com.localcinema.app.ui.player.PlayerActivity
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.absoluteValue

data class HistoryGroup(val title: String, val rows: List<RecentRow>)

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val playbackDao: PlaybackStateDao,
    private val playbackRepository: PlaybackRepository,
    private val thumbs: ThumbCache
) : ViewModel() {

    val rows: StateFlow<List<RecentRow>> = playbackDao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val thumbPaths: StateFlow<Map<String, String>> = thumbs.paths

    fun ensureThumb(video: VideoEntity) = thumbs.ensure(video, viewModelScope)

    fun clearAll() {
        viewModelScope.launch { playbackRepository.clearAll() }
    }

    fun clearOne(row: RecentRow) {
        viewModelScope.launch { playbackRepository.clear(row.video.id) }
    }
}

@Composable
fun RecentScreen(
    viewModel: RecentViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val thumbPaths by viewModel.thumbPaths.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmClear by remember { mutableStateOf(false) }
    val groups = remember(rows) { buildGroups(rows) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近播放",
                    style = PlayerType.PageTitle,
                    color = PlayerColors.TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                )
                if (rows.isNotEmpty()) {
                    Text(
                        text = "清空",
                        fontSize = 11.5.sp,
                        color = PlayerColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { confirmClear = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = if (rows.isEmpty()) {
                    "还没有播放记录"
                } else {
                    "共 ${rows.size} 条记录 · 最近在前"
                },
                style = PlayerType.Caption,
                color = PlayerColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(PlayerColors.Surface, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = PlayerColors.TextTertiary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "看过的片子会记录在这里",
                    fontSize = 12.sp,
                    color = PlayerColors.TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "每个视频的进度独立保存，点开即续播",
                    fontSize = 10.5.sp,
                    color = PlayerColors.TextTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                groups.forEach { group ->
                    item(key = "h-${group.title}") {
                        Text(
                            text = group.title,
                            fontSize = 11.sp,
                            color = PlayerColors.GroupLabel,
                            modifier = Modifier.padding(
                                start = Dimens.pagePadding + 2.dp,
                                top = 12.dp,
                                bottom = 4.dp
                            )
                        )
                    }
                    items(group.rows, key = { it.video.id }) { row ->
                        androidx.compose.runtime.LaunchedEffect(row.video.id) {
                            viewModel.ensureThumb(row.video)
                        }
                        HistoryRowItem(
                            row = row,
                            thumbPath = thumbPaths[row.video.id],
                            onClick = {
                                context.startActivity(
                                    PlayerActivity.intent(
                                        context = context,
                                        albumId = row.video.albumId,
                                        videoId = row.video.id
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = PlayerColors.SurfaceElevated,
            titleContentColor = PlayerColors.TextPrimary,
            textContentColor = PlayerColors.TextSecondary,
            title = { Text("清空播放记录？", fontSize = 16.sp) },
            text = {
                Text(
                    text = "所有视频的观看进度会被清除，下次从头发播放。\n视频文件与专辑不受影响。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClear = false
                    Toast.makeText(context, "已清空播放记录", Toast.LENGTH_SHORT).show()
                }) {
                    Text("清空", color = PlayerColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消", color = PlayerColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun HistoryRowItem(
    row: RecentRow,
    thumbPath: String?,
    onClick: () -> Unit
) {
    val video = row.video
    val duration = if (row.stateDurationMs > 0) row.stateDurationMs else video.durationMs
    val progress = if (duration > 0) {
        (row.statePositionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.pagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(Dimens.radiusThumb))
                .background(seedBrush(video.displayName))
        ) {
            if (thumbPath != null) {
                AsyncImage(
                    model = File(thumbPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = PlayerColors.TextTertiary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(15.dp)
                )
            }

            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Dimens.progressHeight)
                        .background(PlayerColors.PlayerTrackInactive)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(Dimens.progressHeight)
                            .background(PlayerColors.Accent)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp)
        ) {
            Text(
                text = video.displayName,
                style = PlayerType.ListTitle,
                color = PlayerColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    row.albumName?.let { append("$it · ") }
                    append(
                        if (row.stateFinished) {
                            "已看完"
                        } else {
                            "已看 ${formatDuration(row.statePositionMs)}"
                        }
                    )
                },
                style = PlayerType.Caption,
                color = if (row.stateFinished) PlayerColors.TextSecondary else PlayerColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(
            text = relativeTime(row.stateUpdatedAt),
            fontSize = 9.5.sp,
            color = PlayerColors.TextTertiary
        )
    }
}

// ---------------------------------------------------------------------------
// 时间处理
// ---------------------------------------------------------------------------

private const val DAY_MS = 86_400_000L

private fun startOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** 按自然日分组：今天 / 昨天 / 本周 / 更早 */
private fun buildGroups(rows: List<RecentRow>): List<HistoryGroup> {
    val today = startOfDay(System.currentTimeMillis())
    val buckets = LinkedHashMap<String, MutableList<RecentRow>>()
    rows.forEach { row ->
        val day = startOfDay(row.stateUpdatedAt)
        val diffDays = ((today - day) / DAY_MS).coerceAtLeast(0L)
        val title = when {
            diffDays == 0L -> "今天"
            diffDays == 1L -> "昨天"
            diffDays < 7L -> "本周"
            else -> "更早"
        }
        buckets.getOrPut(title) { mutableListOf() }.add(row)
    }
    return buckets.map { HistoryGroup(it.key, it.value) }
}

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        startOfDay(timestamp) == startOfDay(now) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        startOfDay(now) - startOfDay(timestamp) == DAY_MS -> "昨天"
        diff < 7 * DAY_MS -> "${diff / DAY_MS} 天前"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun seedBrush(seed: String): Brush {
    val palettes = PlayerColors.CoverPalettes
    val idx = seed.hashCode().absoluteValue % palettes.size
    return Brush.linearGradient(palettes[idx])
}
