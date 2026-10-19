package com.localcinema.app.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.localcinema.app.ui.theme.PlayerColors
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val current by viewModel.currentVideo.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val resumeHint by viewModel.resumeHint.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // 播放进度轮询（400ms，拖动时不覆盖）
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) {
                positionMs = viewModel.positionMs()
                durationMs = viewModel.durationMs()
            }
            delay(400)
        }
    }

    // 播放中 3 秒自动隐藏控件
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 点击切换控件（在控件层之下，不拦截按钮）
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = interaction, indication = null) {
                    controlsVisible = !controlsVisible
                }
        )

        if (controlsVisible) {
            PlayerControls(
                title = current?.displayName ?: "",
                isPlaying = isPlaying,
                positionMs = if (dragging) dragValue.toLong() else positionMs,
                durationMs = durationMs,
                dragging = dragging,
                dragValue = dragValue,
                onBack = onBack,
                onTogglePlay = viewModel::togglePlay,
                onSeekBy = viewModel::seekBy,
                onPrev = viewModel::playPrevious,
                onNext = { viewModel.playNext(auto = false) },
                onDragStart = { dragging = true },
                onDrag = { dragValue = it },
                onDragFinish = {
                    viewModel.seekTo(dragValue.toLong())
                    positionMs = dragValue.toLong()
                    dragging = false
                },
                onUnavailable = { label ->
                    Toast.makeText(context, "$label 将在后续阶段接入", Toast.LENGTH_SHORT).show()
                }
            )
        }

        resumeHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(PlayerColors.Scrim)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text = hint, color = Color.White, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    dragging: Boolean,
    dragValue: Float,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragFinish: () -> Unit,
    onUnavailable: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 上下渐变压暗，保证文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to PlayerColors.PlayerScrimTop,
                        0.28f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to PlayerColors.PlayerScrimBottom
                    )
                )
        )

        // 顶部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButtonFlat(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(PlayerColors.RateBadge)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "1.0x",
                    color = PlayerColors.RateBadgeInk,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButtonFlat(onClick = { onUnavailable("更多菜单") }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 中部：左 10s / 播放暂停 / 右 30s
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            JumpButton(seconds = 10, forward = false) { onSeekBy(-10_000) }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PlayerColors.PlayerPlayBg)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            JumpButton(seconds = 30, forward = true) { onSeekBy(30_000) }
        }

        // 底部
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            Slider(
                value = if (dragging) dragValue else positionMs.toFloat(),
                onValueChange = { value ->
                    if (!dragging) onDragStart()
                    onDrag(value)
                },
                onValueChangeFinished = onDragFinish,
                valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = PlayerColors.Accent,
                    inactiveTrackColor = PlayerColors.PlayerTrackInactive
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(positionMs),
                    color = PlayerColors.PlayerTimeText,
                    fontSize = 10.sp
                )
                Text(
                    text = formatMs(durationMs),
                    color = PlayerColors.PlayerTimeText,
                    fontSize = 10.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolItem(icon = Icons.Rounded.SkipPrevious, label = "上一集", onClick = onPrev)
                ToolItem(icon = Icons.Rounded.SkipNext, label = "下一集", onClick = onNext)
                ToolItem(icon = Icons.Rounded.Speed, label = "倍速", onClick = { onUnavailable("倍速") })
                ToolItem(icon = Icons.Rounded.Subtitles, label = "字幕", onClick = { onUnavailable("字幕") })
                ToolItem(icon = Icons.Rounded.AspectRatio, label = "比例", onClick = { onUnavailable("画面比例") })
                ToolItem(icon = Icons.Rounded.Lock, label = "锁定", onClick = { onUnavailable("锁屏") })
            }
        }
    }
}

@Composable
private fun IconButtonFlat(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun JumpButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(PlayerColors.PlayerJumpBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (forward) "»" else "«",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 16.sp
            )
            Text(
                text = seconds.toString(),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PlayerColors.PlayerToolTint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(text = label, color = PlayerColors.PlayerToolTint, fontSize = 8.5.sp)
    }
}
