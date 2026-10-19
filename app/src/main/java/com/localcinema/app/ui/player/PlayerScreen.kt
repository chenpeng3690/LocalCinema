package com.localcinema.app.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.localcinema.app.data.prefs.JumpButtonConfig
import com.localcinema.app.data.prefs.PlaylistMode
import com.localcinema.app.ui.theme.PlayerColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

private enum class GestureMode { None, Undecided, Seek, Brightness, Volume }

private enum class PlayerPanel { None, Subtitle, Audio, Speed, More }

/** 长按多久开始临时加速 */
private const val LONG_PRESS_MS = 380L

/** 双击判定窗口 */
private const val DOUBLE_TAP_MS = 260L

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val current by viewModel.currentVideo.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val resumeHint by viewModel.resumeHint.collectAsStateWithLifecycle()
    val subtitleText by viewModel.subtitleText.collectAsStateWithLifecycle()
    val subtitlePrefs by viewModel.subtitlePrefs.collectAsStateWithLifecycle()
    val textTracks by viewModel.textTracks.collectAsStateWithLifecycle()
    val selectedTextIndex by viewModel.selectedTextIndex.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val selectedAudioIndex by viewModel.selectedAudioIndex.collectAsStateWithLifecycle()
    val speedPercent by viewModel.speedPercent.collectAsStateWithLifecycle()
    val jumpLeft by viewModel.jumpLeft.collectAsStateWithLifecycle()
    val jumpRight by viewModel.jumpRight.collectAsStateWithLifecycle()
    val nextUp by viewModel.nextUp.collectAsStateWithLifecycle()
    val playlistMode by viewModel.playlistMode.collectAsStateWithLifecycle()
    val boosting by viewModel.boosting.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepRemainingSec.collectAsStateWithLifecycle()
    val boostSpeed by viewModel.boostSpeedPercent.collectAsStateWithLifecycle()
    val jumpHintEnabled by viewModel.jumpHintEnabled.collectAsStateWithLifecycle()
    val manualSubtitle by viewModel.externalSubtitleManual.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var overlay by remember { mutableStateOf<String?>(null) }

    // 按钮 / 双击 / 手势触发的提示统一在这里自动消失，避免残留
    LaunchedEffect(overlay) {
        val currentText = overlay ?: return@LaunchedEffect
        delay(1_500)
        if (overlay == currentText) overlay = null
    }

    var panel by remember { mutableStateOf(PlayerPanel.None) }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadExternalSubtitle(uri)
            Toast.makeText(context, "已挂载字幕，下次进自动加载", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!sliderDragging) {
                positionMs = viewModel.positionMs()
                durationMs = viewModel.durationMs()
            }
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, locked, panel) {
        if (controlsVisible && isPlaying && !locked && panel == PlayerPanel.None) {
            delay(3_000)
            controlsVisible = false
        }
    }

    val gestureModifier = rememberVideoGestureModifier(
        locked = locked,
        viewModel = viewModel,
        durationProvider = { if (durationMs > 0) durationMs else viewModel.durationMs() },
        audioManager = audioManager,
        maxVolume = maxVolume,
        activity = activity,
        onTap = { controlsVisible = !controlsVisible },
        onDoubleTap = { left ->
            viewModel.seekBy(if (left) -10_000 else 30_000)
            if (jumpHintEnabled) overlay = if (left) "快退 10s" else "快进 30s"
        },
        onOverlay = { overlay = it },
        onSeekPreview = { seekPreview = it }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.resizeMode = aspectMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // ---------- 手势层：单击显隐、双击快退快进、长按加速、上下滑音量亮度、横滑进度 ----------
        Box(modifier = Modifier.fillMaxSize().then(gestureModifier))

        if (!locked && controlsVisible) {
            PlayerControls(
                title = current?.displayName ?: "",
                isPlaying = isPlaying,
                positionMs = seekPreview ?: if (sliderDragging) sliderValue.toLong() else positionMs,
                durationMs = durationMs,
                speedPercent = speedPercent,
                displayedSpeedPercent = if (boosting) boostSpeed else speedPercent,
                boosting = boosting,
                sleepRemainingSec = sleepRemaining,
                onOpenSpeed = { panel = PlayerPanel.Speed },
                onBack = onBack,
                onTogglePlay = viewModel::togglePlay,
                onPrev = viewModel::playPrevious,
                onNext = { viewModel.playNext(auto = false) },
                onSliderStart = {
                    sliderDragging = true
                    sliderValue = positionMs.toFloat()
                },
                onSliderChange = { value ->
                    sliderValue = value
                    positionMs = value.toLong()
                },
                onSliderFinish = {
                    viewModel.seekTo(sliderValue.toLong())
                    sliderDragging = false
                },
                onLock = {
                    locked = true
                    controlsVisible = false
                },
                onOpenSubtitle = { panel = PlayerPanel.Subtitle },
                onOpenAudio = { panel = PlayerPanel.Audio },
                onOpenMore = { panel = PlayerPanel.More }
            )
        }

        // ---------- 快退 / 快进悬浮按钮（按配置渲染） ----------
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            if (!locked) {
                if (jumpLeft.enabled && (controlsVisible || jumpLeft.constantVisible)) {
                    ConfigJumpButton(
                        config = jumpLeft,
                        forward = false,
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        onTrigger = {
                            viewModel.seekBy(-jumpLeft.seconds * 1000L)
                            if (jumpHintEnabled) overlay = "快退 ${jumpLeft.seconds}s"
                        }
                    )
                }
                if (jumpRight.enabled && (controlsVisible || jumpRight.constantVisible)) {
                    ConfigJumpButton(
                        config = jumpRight,
                        forward = true,
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        onTrigger = {
                            viewModel.seekBy(jumpRight.seconds * 1000L)
                            if (jumpHintEnabled) overlay = "快进 ${jumpRight.seconds}s"
                        }
                    )
                }
            }
        }

        // ---------- 字幕 ----------
        if (subtitlePrefs.enabled && subtitleText.isNotBlank()) {
            Text(
                text = subtitleText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = subtitlePrefs.bottomPaddingDp.dp
                    ),
                color = Color(subtitlePrefs.colorArgb),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = subtitlePrefs.fontSizeSp.sp,
                    lineHeight = (subtitlePrefs.fontSizeSp * 1.35f).sp,
                    shadow = Shadow(
                        color = Color(0xE6000000),
                        offset = Offset(0f, 2f),
                        blurRadius = 8f
                    )
                )
            )
        }

        // ---------- 长按加速提示 ----------
        if (boosting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(PlayerColors.Scrim)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "松开恢复原速", color = Color.White, fontSize = 10.5.sp)
            }
        }

        // ---------- 下一集倒计时 ----------
        nextUp?.let { state ->
            NextUpCard(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 76.dp),
                onCancel = viewModel::cancelCountdown,
                onPlayNow = viewModel::playNextNow
            )
        }

        // ---------- 锁定态 ----------
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PlayerColors.PlayerPlayBg)
                    .clickable {
                        locked = false
                        controlsVisible = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = "解锁",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        overlay?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(11.dp))
                    .background(PlayerColors.Scrim)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = text, color = Color.White, fontSize = 12.sp)
            }
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

    // ---------- 面板 ----------
    when (panel) {
        PlayerPanel.Subtitle -> SubtitleSheet(
            prefs = subtitlePrefs,
            tracks = textTracks,
            selectedIndex = selectedTextIndex,
            manual = manualSubtitle,
            onDismiss = { panel = PlayerPanel.None },
            onToggleEnabled = viewModel::setSubtitleEnabled,
            onSelectTrack = viewModel::selectTextTrack,
            onPickExternal = {
                panel = PlayerPanel.None
                subtitlePicker.launch(arrayOf("*/*"))
            },
            onClearManual = viewModel::clearManualSubtitle,
            onFontSize = viewModel::setSubtitleFontSize,
            onColor = viewModel::setSubtitleColor,
            onDelay = viewModel::adjustSubtitleDelay
        )

        PlayerPanel.Audio -> AudioTrackSheet(
            tracks = audioTracks,
            selectedIndex = selectedAudioIndex,
            onDismiss = { panel = PlayerPanel.None },
            onSelect = viewModel::selectAudioTrack
        )

        PlayerPanel.Speed -> SpeedSheet(
            percent = speedPercent,
            onDismiss = { panel = PlayerPanel.None },
            onStep = viewModel::stepSpeed,
            onSet = viewModel::setSpeed
        )

        PlayerPanel.More -> MoreSheet(
            playlistMode = playlistMode,
            aspectMode = aspectMode,
            sleepRemainingSec = sleepRemaining,
            onDismiss = { panel = PlayerPanel.None },
            onSelectMode = viewModel::setPlaylistMode,
            onSelectAspect = { mode ->
                aspectMode = mode
                overlay = when (mode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "画面：适应屏幕"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "画面：铺满"
                    else -> "画面：裁剪"
                }
            },
            onSelectSleep = viewModel::setSleepTimer
        )

        PlayerPanel.None -> Unit
    }
}

/**
 * 播放页手势。
 *
 * 合并到一个 pointerInput 里手写状态机，是因为 Compose 的多个 pointerInput 会并行接收事件：
 * 「长按加速」「双击快进」「上下滑调亮度/音量」「横滑拖进度」四条通道分开写会互相抢事件，
 * 表现为调音量时顺带触发双击、长按加速时找不到停下的时机。
 */
@Composable
private fun rememberVideoGestureModifier(
    locked: Boolean,
    viewModel: PlayerViewModel,
    durationProvider: () -> Long,
    audioManager: AudioManager,
    maxVolume: Int,
    activity: Activity?,
    onTap: () -> Unit,
    onDoubleTap: (Boolean) -> Unit,
    onOverlay: (String?) -> Unit,
    onSeekPreview: (Long?) -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var pendingTap by remember { mutableStateOf<Job?>(null) }

    return Modifier.pointerInput(locked) {
        if (locked) return@pointerInput
        val slop = viewConfiguration.touchSlop

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            val startY = down.position.y
            val downTime = down.uptimeMillis

            val basePosition = viewModel.positionMs()
            val baseVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val rawBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
            val baseBrightness = if (rawBrightness < 0f) 0.5f else rawBrightness

            var mode = GestureMode.Undecided
            var moved = false
            var boosting = false
            var preview: Long? = null

            val boostJob = scope.launch {
                delay(LONG_PRESS_MS)
                if (!moved) {
                    boosting = true
                    viewModel.beginTemporarySpeedUp()
                }
            }

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break

                val dx = change.position.x - startX
                val dy = change.position.y - startY
                if (!moved && hypot(dx, dy) > slop) {
                    moved = true
                    boostJob.cancel()
                }
                if (!moved || boosting) continue

                if (mode == GestureMode.Undecided) {
                    mode = when {
                        abs(dx) > abs(dy) -> GestureMode.Seek
                        startX < size.width / 2f -> GestureMode.Brightness
                        else -> GestureMode.Volume
                    }
                }

                when (mode) {
                    GestureMode.Seek -> {
                        val total = durationProvider().coerceAtLeast(1L)
                        val delta = (dx / size.width.toFloat() * total * 1.5f).toLong()
                        val target = (basePosition + delta).coerceIn(0L, total)
                        preview = target
                        onSeekPreview(target)
                        onOverlay("跳到 ${formatMs(target)}")
                    }

                    GestureMode.Brightness -> {
                        val heightPx = size.height.toFloat().coerceAtLeast(1f)
                        val target = (baseBrightness - dy / heightPx * 1.4f).coerceIn(0.01f, 1f)
                        activity?.window?.let { window ->
                            val attrs = window.attributes
                            attrs.screenBrightness = target
                            window.attributes = attrs
                        }
                        onOverlay("亮度 ${(target * 100).toInt()}%")
                    }

                    GestureMode.Volume -> {
                        val heightPx = size.height.toFloat().coerceAtLeast(1f)
                        val delta = (-dy / heightPx * maxVolume * 1.4f).toInt()
                        val target = (baseVolume + delta).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        onOverlay("音量 ${target * 100 / maxVolume}%")
                    }

                    else -> Unit
                }
            }

            boostJob.cancel()

            if (boosting) {
                viewModel.endTemporarySpeedUp()
                return@awaitEachGesture
            }

            when (mode) {
                GestureMode.Seek -> {
                    preview?.let { viewModel.seekTo(it) }
                    onSeekPreview(null)
                    onOverlay(null)
                }

                GestureMode.Brightness, GestureMode.Volume -> onOverlay(null)

                else -> {
                    // 没有明显位移：判定为单击或双击
                    if (downTime - lastTapTime < DOUBLE_TAP_MS) {
                        pendingTap?.cancel()
                        lastTapTime = 0L
                        onDoubleTap(startX < size.width / 2f)
                    } else {
                        lastTapTime = downTime
                        pendingTap = scope.launch {
                            delay(DOUBLE_TAP_MS)
                            onTap()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpCard(
    state: NextUpState,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit
) {
    Column(
        modifier = modifier
            .width(196.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerColors.SurfaceElevated)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.secondsLeft}",
                fontSize = 15.sp,
                color = PlayerColors.Accent,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = "秒后播放下一集",
                fontSize = 10.5.sp,
                color = PlayerColors.TextSecondary
            )
        }
        Text(
            text = state.title,
            fontSize = 11.5.sp,
            color = PlayerColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlayerColors.ChipBg)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "取消", fontSize = 10.5.sp, color = PlayerColors.TextSecondary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlayerColors.Accent)
                    .clickable(onClick = onPlayNow),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "立即播放", fontSize = 10.5.sp, color = Color.White)
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
    speedPercent: Int,
    displayedSpeedPercent: Int,
    boosting: Boolean,
    sleepRemainingSec: Int?,
    onOpenSpeed: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSliderStart: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderFinish: () -> Unit,
    onLock: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenMore: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
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

            // 定时关闭剩余时间
            sleepRemainingSec?.let { seconds ->
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "定时关闭",
                        tint = PlayerColors.RateBadge,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formatRemaining(seconds),
                        color = PlayerColors.RateBadge,
                        fontSize = 9.5.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(PlayerColors.RateBadge)
                    .clickable(onClick = onOpenSpeed)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formatSpeed(displayedSpeedPercent),
                    color = PlayerColors.RateBadgeInk,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButtonFlat(onClick = onOpenMore) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            Slider(
                value = positionMs.toFloat(),
                onValueChange = { value ->
                    onSliderStart()
                    onSliderChange(value)
                },
                onValueChangeFinished = onSliderFinish,
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
                Text(text = formatMs(positionMs), color = PlayerColors.PlayerTimeText, fontSize = 10.sp)
                Text(text = formatMs(durationMs), color = PlayerColors.PlayerTimeText, fontSize = 10.sp)
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
                ToolItem(
                    icon = Icons.Rounded.Speed,
                    label = formatSpeed(speedPercent),
                    onClick = onOpenSpeed
                )
                ToolItem(icon = Icons.Rounded.Subtitles, label = "字幕", onClick = onOpenSubtitle)
                ToolItem(icon = Icons.Rounded.Audiotrack, label = "音轨", onClick = onOpenAudio)
                ToolItem(icon = Icons.Rounded.Lock, label = "锁定", onClick = onLock)
            }
        }
    }
}

private fun formatRemaining(seconds: Int): String {
    val m = seconds / 60
    return if (m >= 1) "${m}分" else "${seconds}秒"
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

/**
 * 按用户配置渲染的悬浮按钮。
 * 位置用屏幕比例换算，换设备不会跑出屏幕；透明度作用于整体（含描边）。
 */
@Composable
private fun ConfigJumpButton(
    config: JumpButtonConfig,
    forward: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    onTrigger: () -> Unit
) {
    val size = config.sizeDp.dp
    val scope = rememberCoroutineScope()

    val x = containerWidth * config.xRatio - size / 2
    val y = containerHeight * config.yRatio - size / 2

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .graphicsLayer(alpha = config.opacity)
            .clip(CircleShape)
            .background(PlayerColors.PlayerJumpBg)
            .border(1.dp, Color(0x47FFFFFF), CircleShape)
            .pointerInput(config) {
                detectTapGestures(
                    onPress = {
                        var repeated = false
                        val job = scope.launch {
                            if (config.longPressRepeat) {
                                delay(300)
                                repeated = true
                                while (isActive) {
                                    onTrigger()
                                    delay(300)
                                }
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (!repeated) onTrigger()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (forward) "»" else "«",
                color = Color.White,
                fontSize = (config.sizeDp * 0.26f).sp,
                lineHeight = (config.sizeDp * 0.26f).sp
            )
            Text(
                text = config.seconds.toString(),
                color = Color.White,
                fontSize = (config.sizeDp * 0.22f).sp
            )
        }
    }
}

@Composable
private fun ToolItem(
    icon: ImageVector,
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
