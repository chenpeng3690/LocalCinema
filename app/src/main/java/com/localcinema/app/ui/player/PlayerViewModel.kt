package com.localcinema.app.ui.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.prefs.JumpButtonConfig
import com.localcinema.app.data.prefs.PlaylistMode
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.data.repo.PlaybackRepository
import com.localcinema.app.data.scanner.NaturalOrder
import com.localcinema.app.data.subtitle.SubtitleRepository
import com.localcinema.app.data.subtitle.TimedCue
import com.localcinema.app.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 可切换的轨道。index = -1 代表外挂字幕 */
data class TrackOption(val index: Int, val label: String)

/** 字幕显示参数，跨视频保留 */
data class SubtitlePrefs(
    val enabled: Boolean = true,
    val fontSizeSp: Float = 14f,
    val colorArgb: Long = 0xFFFFFFFF,
    val delayMs: Long = 0L,
    val bottomPaddingDp: Int = 48
)

/** 播完一集后的「下一集」倒计时状态 */
data class NextUpState(
    val title: String,
    val secondsLeft: Int
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoDao: VideoDao,
    private val playbackRepository: PlaybackRepository,
    private val subtitleRepository: SubtitleRepository,
    private val settingsStore: SettingsStore,
    private val connection: PlayerConnection,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>(KEY_ALBUM_ID) ?: ""
    private val startVideoId: String = savedStateHandle.get<String>(KEY_VIDEO_ID) ?: ""

    /** 播放器由 Service 持有，这里只是控制器；连接完成前为 null */
    val player: StateFlow<Player?> = connection.player

    private var attachedPlayer: Player? = null

    /** 已经在这个 player 实例上决定了播什么；换 controller 后要重新决定 */
    private var startedOnPlayer: Player? = null

    /**
     * 本次起播期望落到的位置。用于兜底校验：
     * setMediaItem(item, startMs) 正常会直接落在目标点，若实际位置明显落后，补一次 seek。
     */
    private var pendingResumeMs: Long? = null

    /** 退出播放页时是我们自己暂停的（挂后台且未开后台播放），回前台要接着放 */
    private var pausedByLeaving = false

    private val videoFlow = videoDao.observeByAlbum(albumId)
        .map { list -> list.sortedWith(compareBy(NaturalOrder) { it.displayName }) }

    /**
     * 当前专辑的全部视频。
     *
     * 必须用 Eagerly：播放页从来没订阅这个流，若用 WhileSubscribed，
     * value 会一直停在初始的空列表，上一集/下一集就永远拿不到数据。
     */
    val videos: StateFlow<List<VideoEntity>> = videoFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentVideo = MutableStateFlow<VideoEntity?>(null)
    val currentVideo: StateFlow<VideoEntity?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _resumeHint = MutableStateFlow<String?>(null)
    val resumeHint: StateFlow<String?> = _resumeHint.asStateFlow()

    private var hintJob: Job? = null

    private val _backgroundPlay = MutableStateFlow(false)

    // ---------- 倍速 ----------
    private val _speedPercent = MutableStateFlow(100)
    val speedPercent: StateFlow<Int> = _speedPercent.asStateFlow()

    /** 长按画面加速中；此时界面角标显示临时倍速 */
    private val _boosting = MutableStateFlow(false)
    val boosting: StateFlow<Boolean> = _boosting.asStateFlow()

    /** 快退/快进后是否弹居中提示（关闭后不遮挡画面） */
    private val _jumpHintEnabled = MutableStateFlow(true)
    val jumpHintEnabled: StateFlow<Boolean> = _jumpHintEnabled.asStateFlow()

    /** 长按画面临时加速的目标倍速 */
    private val _boostSpeedPercent = MutableStateFlow(200)
    val boostSpeedPercent: StateFlow<Int> = _boostSpeedPercent.asStateFlow()

    private var speedBeforeBoost: Int? = null

    // ---------- 播放列表 ----------
    private val _playlistMode = MutableStateFlow(PlaylistMode.SEQUENCE)
    val playlistMode: StateFlow<String> = _playlistMode.asStateFlow()

    private var nextCountdownEnabled = true

    private val _nextUp = MutableStateFlow<NextUpState?>(null)
    val nextUp: StateFlow<NextUpState?> = _nextUp.asStateFlow()

    private var countdownJob: Job? = null

    // ---------- 睡眠定时 ----------
    private val _sleepRemainingSec = MutableStateFlow<Int?>(null)
    val sleepRemainingSec: StateFlow<Int?> = _sleepRemainingSec.asStateFlow()

    private var sleepJob: Job? = null

    // ---------- 悬浮按钮 ----------
    private val _jumpLeft = MutableStateFlow(JumpButtonConfig.DEFAULT_LEFT)
    val jumpLeft: StateFlow<JumpButtonConfig> = _jumpLeft.asStateFlow()

    private val _jumpRight = MutableStateFlow(JumpButtonConfig.DEFAULT_RIGHT)
    val jumpRight: StateFlow<JumpButtonConfig> = _jumpRight.asStateFlow()

    // ---------- 字幕 ----------
    private val _subtitlePrefs = MutableStateFlow(SubtitlePrefs())
    val subtitlePrefs: StateFlow<SubtitlePrefs> = _subtitlePrefs.asStateFlow()

    private val _subtitleText = MutableStateFlow("")
    val subtitleText: StateFlow<String> = _subtitleText.asStateFlow()

    private var externalCues: List<TimedCue> = emptyList()
    private var externalActive: Boolean = false

    private val _externalSubtitleName = MutableStateFlow<String?>(null)
    private val _externalIsManual = MutableStateFlow(false)

    val externalSubtitleManual: StateFlow<Boolean> = _externalIsManual.asStateFlow()

    private val _textTracks = MutableStateFlow<List<TrackOption>>(emptyList())

    val textTracks: StateFlow<List<TrackOption>> = combine(
        _textTracks,
        _externalSubtitleName
    ) { embedded, externalName ->
        if (externalName == null) embedded else embedded + TrackOption(EXTERNAL_INDEX, "外挂字幕 · $externalName")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTextIndex = MutableStateFlow<Int?>(null)
    val selectedTextIndex: StateFlow<Int?> = _selectedTextIndex.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackOption>>(emptyList())
    val audioTracks: StateFlow<List<TrackOption>> = _audioTracks.asStateFlow()

    private val _selectedAudioIndex = MutableStateFlow(0)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) onEnded()
            if (playbackState == Player.STATE_READY) verifyResumePosition()
        }

        override fun onPlayerError(error: PlaybackException) {
            // 黑屏但没有任何提示是最难排查的，这里至少把错误码抛给用户
            showHint("播放出错：${error.errorCodeName}", 4_000L)
        }

        /**
         * 换集。
         *
         * 这里同时承担两件事：
         * 1. 队列自己往下走（自然播完）—— 要不要接着播由连播设置决定；
         * 2. 用户在通知/锁屏/耳机线上点了上一集下一集 —— 界面得跟着换，否则
         *    标题、字幕、进度保存全都会停在上一条上。
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = attachedPlayer ?: return
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> onAutoAdvance(p)

                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                    val id = mediaItem?.mediaId ?: return
                    videos.value.firstOrNull { it.id == id }?.let { adoptExternalVideo(it) }
                }

                // PLAYLIST_CHANGED 是我们自己 setMediaItems 触发的，忽略
                else -> Unit
            }
        }

        /** 内嵌字幕走这里；外挂字幕自己渲染 */
        override fun onCues(cueGroup: CueGroup) {
            if (externalActive) return
            if (!_subtitlePrefs.value.enabled) {
                if (_subtitleText.value.isNotEmpty()) _subtitleText.value = ""
                return
            }
            val text = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }
            if (text != _subtitleText.value) _subtitleText.value = text
        }

        override fun onTracksChanged(tracks: Tracks) {
            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            _textTracks.value = textGroups.mapIndexed { index, group ->
                val format = group.mediaTrackGroup.getFormat(0)
                TrackOption(index, format.label ?: format.language ?: "字幕 ${index + 1}")
            }
            if (!externalActive) {
                _selectedTextIndex.value =
                    textGroups.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
            }

            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            _audioTracks.value = audioGroups.mapIndexed { index, group ->
                val format = group.mediaTrackGroup.getFormat(0)
                val label = format.label ?: format.language ?: "音轨 ${index + 1}"
                TrackOption(index, "$label · ${format.channelCount}ch")
            }
            _selectedAudioIndex.value =
                audioGroups.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        }
    }

    init {
        // 先把持久化的设置灌进来，避免首帧用默认值闪一下
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            _subtitlePrefs.value = SubtitlePrefs(
                enabled = s.subtitleEnabled,
                fontSizeSp = s.subtitleFontSize,
                colorArgb = s.subtitleColorArgb,
                delayMs = s.subtitleDelayMs,
                bottomPaddingDp = s.subtitleBottomPadding
            )
            _playlistMode.value = s.playlistMode
            nextCountdownEnabled = s.nextCountdown
            _boostSpeedPercent.value = s.longPressSpeedPercent
            _jumpHintEnabled.value = s.jumpHintEnabled
        }

        connection.acquire()

        // 连接完成后绑定监听并开始播放
        viewModelScope.launch {
            connection.player.collect { p ->
                if (p == null) {
                    // 连接被释放：解绑，等重连后的新实例再走一遍起播判断
                    attachedPlayer?.removeListener(listener)
                    attachedPlayer = null
                    return@collect
                }
                if (p !== attachedPlayer) {
                    attachedPlayer?.removeListener(listener)
                    p.addListener(listener)
                    attachedPlayer = p
                    startPlaybackIfNeeded(p)
                }
            }
        }

        // 控制器偶发连不上（Service 正在重建、上一次连接刚被释放）：2 秒一次重试，最多 5 次。
        // 没有这一步的重试，播放页会永远黑屏，只能重新进一次。
        viewModelScope.launch {
            repeat(5) {
                delay(2_000)
                if (connection.player.value != null) return@launch
                connection.ensureConnected()
            }
        }

        // 设置变化时实时同步（用户可能在播放页改了按钮配置）
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                applySpeed(settings.speedPercent.coerceIn(50, 500), persist = false)
                _jumpLeft.value = settings.jumpLeft
                _jumpRight.value = settings.jumpRight
                _backgroundPlay.value = settings.backgroundPlay
                _playlistMode.value = settings.playlistMode
                attachedPlayer?.let { applyPlaylistMode(it) }
                nextCountdownEnabled = settings.nextCountdown
                _boostSpeedPercent.value = settings.longPressSpeedPercent
                _jumpHintEnabled.value = settings.jumpHintEnabled
                // 字幕样式可能在另一个入口被改过，同步但不覆盖当前开关以外的状态
                val prefs = _subtitlePrefs.value
                _subtitlePrefs.value = prefs.copy(
                    fontSizeSp = settings.subtitleFontSize,
                    colorArgb = settings.subtitleColorArgb,
                    bottomPaddingDp = settings.subtitleBottomPadding
                )
            }
        }

        // 每 5 秒落一次进度
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (attachedPlayer?.isPlaying == true) saveNow()
            }
        }

        // 外挂字幕按「播放位置 + 延迟」筛选
        viewModelScope.launch {
            while (true) {
                val prefs = _subtitlePrefs.value
                if (!prefs.enabled) {
                    if (_subtitleText.value.isNotEmpty()) _subtitleText.value = ""
                } else if (externalActive && externalCues.isNotEmpty()) {
                    val position = (attachedPlayer?.currentPosition ?: 0L) + prefs.delayMs
                    val text = externalCues.asSequence()
                        .filter { position >= it.startMs && position <= it.endMs }
                        .joinToString("\n") { it.text }
                    if (text != _subtitleText.value) _subtitleText.value = text
                }
                delay(120)
            }
        }
    }

    /**
     * 首次连上某个 player 时决定播什么。
     *
     * 判断挂在 player 实例上而不是一个布尔值：controller 断线重连（或上一次连接被
     * 上一个播放页释放掉）时会换一个新实例，布尔值会让我们以为「已经起过了」，
     * 于是新实例上什么也不做 —— 表现就是黑屏。
     */
    private fun startPlaybackIfNeeded(p: Player) {
        if (startedOnPlayer === p) return
        startedOnPlayer = p

        viewModelScope.launch {
            val list = videoFlow.first()
            if (list.isEmpty()) {
                // 视频还没从库里读出来，留空让下次 attach 再试
                startedOnPlayer = null
                return@launch
            }
            val target = list.firstOrNull { it.id == startVideoId } ?: list.first()

            val sameItem = p.mediaItemCount > 0 && p.currentMediaItem?.mediaId == target.id
            if (sameItem) {
                // Service 里的播放器还停在这一集上（用户从后台切回来 / 退出播放页又进来）。
                // 之前这里直接 return，于是「退出播放页时被我们暂停」的状态被原样带回来，
                // 看起来就是「打开视频不自动播放，得手动点一下」。
                _currentVideo.value = target
                pausedByLeaving = false
                applyPlaylistMode(p)
                // ENDED 状态下 play() 不会自己从头开始
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                if (!p.isPlaying) p.play()
                return@launch
            }
            playVideo(target)
        }
    }

    fun playVideo(video: VideoEntity) {
        val p = attachedPlayer ?: return
        viewModelScope.launch {
            cancelCountdown()
            saveNow()
            val startMs = playbackRepository.resumePosition(video)
            playbackRepository.markStarted(video)
            settingsStore.setLastVideo(albumId, video.id)

            _currentVideo.value = video
            resetSubtitleState()
            pausedByLeaving = false

            // 续播位置必须随 MediaItem 一起下发，不能先 prepare 再 seekTo。
            // 原因：MediaController.seekTo 要求 COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM 可用，
            // 而这个命令只在 isCurrentMediaItemSeekable == true 时才存在（见 Util.getAvailableCommands）。
            // 刚 setMediaItem 的瞬间媒体项还没准备好，命令被 session 拒掉（PERMISSION_DENIED）；
            // 控制器却已经在本地把 position 掩码成起始位置，所以界面显示的时间是对的、声音却从头开始。
            // 冷启动（进程被杀，播放器是新建的）必现；进程还活着时播放器早已 seekable，所以看不出问题。
            pendingResumeMs = startMs.takeIf { it > 0L }

            // 整个专辑作为播放队列下发。
            // 只放当前这一集的话，player 的 hasNextMediaItem()/hasPreviousMediaItem() 永远是 false，
            // COMMAND_SEEK_TO_NEXT/PREVIOUS_MEDIA_ITEM 不在可用命令里，通知和锁屏卡片就只剩一个
            // 播放/暂停键 —— 上一集/下一集不是我们没画，是系统根本没有这两个按钮。
            applyPlaylistMode(p)
            val queue = queueAround(video)
            p.setMediaItems(queue.items, queue.index, startMs.coerceAtLeast(0L))
            p.prepare()
            p.playWhenReady = true

            if (startMs > 0L) {
                showHint("已跳到 ${formatMs(startMs)}（上次看到这里）", 3_000L)
            }

            // 手动挂载过就以手动为准，否则用扫描时自动匹配的
            val subtitle = video.manualSubtitleUri ?: video.subtitleUri
            subtitle?.let {
                loadSubtitleFile(Uri.parse(it), manual = video.manualSubtitleUri != null)
            }
        }
    }

    private class PlaybackQueue(val items: List<MediaItem>, val index: Int)

    /**
     * 以 [video] 为中心构建播放队列。
     *
     * 队列长度决定通知栏能不能显示上一集/下一集：整张专辑都在队列里，
     * player 才会认为「有下一项」，系统媒体控件才会给出那两个键；
     * 耳机线控、锁屏、通知、车机走的是同一套命令。
     *
     * 规模保护：MediaItem 列表会随 timeline 经 Binder 发给控制器，
     * 单个事务有大小上限。正常专辑（几十到几百集）远不到线；
     * 真遇到上千集的目录就退化成单集队列，宁可少两个按钮也不能让 setMediaItems 抛异常。
     */
    private fun queueAround(video: VideoEntity): PlaybackQueue {
        val list = albumVideos().ifEmpty { listOf(video) }
        val index = list.indexOfFirst { it.id == video.id }.let { if (it >= 0) it else 0 }
        val total = list.size

        if (total > MAX_QUEUE_ITEMS) {
            return PlaybackQueue(listOf(buildMediaItem(video, index, total)), 0)
        }
        return PlaybackQueue(
            items = list.mapIndexed { i, v -> buildMediaItem(v, i, total) },
            index = index
        )
    }

    /**
     * 队列自己往前走了（当前这一集自然播完）。
     *
     * 直接顺播最省事，也不用重新加载；但「播完停止」和「下一集倒计时」必须停下来等用户，
     * 这时把播放位置退回刚播完那一集的末尾，交回 onEnded 走原有流程。
     */
    private fun onAutoAdvance(p: Player) {
        val mode = _playlistMode.value
        val mustIntercept = mode == PlaylistMode.STOP_AFTER_ONE ||
            (mode != PlaylistMode.LOOP_ONE && nextCountdownEnabled)

        if (!mustIntercept) {
            videos.value.firstOrNull { it.id == p.currentMediaItem?.mediaId }
                ?.let { adoptExternalVideo(it) }
            return
        }

        val endedIndex = p.currentMediaItemIndex - 1
        p.pause()
        if (endedIndex >= 0) p.seekTo(endedIndex, C.TIME_END_OF_SOURCE)
        onEnded()
    }

    /**
     * 播放器被「外部」换了集（用户点通知栏/锁屏/耳机上的上一集下一集）。
     *
     * 界面状态必须跟着换：标题、字幕、最近播放、播放次数。
     * 队列是 player 自己的，这里不要再 setMediaItems，否则会把用户刚点的操作打断。
     */
    private fun adoptExternalVideo(video: VideoEntity) {
        if (_currentVideo.value?.id == video.id) return

        _currentVideo.value = video
        resetSubtitleState()
        pausedByLeaving = false
        // 位置由 player 自己决定，兜底校验不该再插手
        pendingResumeMs = null

        viewModelScope.launch {
            playbackRepository.markStarted(video)
            settingsStore.setLastVideo(albumId, video.id)
            val subtitle = video.manualSubtitleUri ?: video.subtitleUri
            subtitle?.let { loadSubtitleFile(Uri.parse(it), manual = video.manualSubtitleUri != null) }
        }
    }

    private fun resetSubtitleState() {
        externalCues = emptyList()
        externalActive = false
        _externalSubtitleName.value = null
        _externalIsManual.value = false
        _selectedTextIndex.value = null
        _subtitleText.value = ""
    }

    /** 播放模式对应的 player 行为：单集循环用原生 repeat，随机用原生 shuffle */
    private fun applyPlaylistMode(p: Player) {
        p.repeatMode = if (_playlistMode.value == PlaylistMode.LOOP_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        p.shuffleModeEnabled = _playlistMode.value == PlaylistMode.SHUFFLE
    }

    /**
     * 起播位置兜底校验。
     * 只有「明显落后」才补 seek：seek 容差本身是 5 秒，位置比目标大不算异常。
     */
    private fun verifyResumePosition() {
        val expected = pendingResumeMs ?: return
        val p = attachedPlayer ?: return
        if (p.playbackState != Player.STATE_READY) return
        if (!p.isCurrentMediaItemSeekable) return
        pendingResumeMs = null
        if (expected - p.currentPosition > 8_000L) {
            p.seekTo(expected)
        }
    }

    private fun buildMediaItem(video: VideoEntity, index: Int, total: Int): MediaItem =
        MediaItem.Builder()
            .setUri(video.uri)
            .setMediaId(video.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.displayName)
                    // subtitle 会作为通知/锁屏卡片的副标题，顺手把「第几集 / 共几集」告诉用户
                    .setSubtitle("${index + 1} / $total")
                    .build()
            )
            .build()

    private suspend fun loadSubtitleFile(uri: Uri, manual: Boolean = false) {
        val name = uri.lastPathSegment ?: "subtitle"
        val cues = subtitleRepository.load(uri, name)
        if (cues.isEmpty()) {
            _resumeHint.value = "字幕解析失败：${name.take(24)}"
            return
        }
        externalCues = cues
        externalActive = true
        _externalSubtitleName.value = name
        _externalIsManual.value = manual
        _selectedTextIndex.value = EXTERNAL_INDEX
        _subtitlePrefs.value = _subtitlePrefs.value.copy(enabled = true)

        attachedPlayer?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    fun loadExternalSubtitle(uri: Uri) {
        viewModelScope.launch {
            loadSubtitleFile(uri, manual = true)
            // 记住这次选择，下次进来自动加载同一份字幕
            _currentVideo.value?.let { video ->
                videoDao.setManualSubtitle(video.id, uri.toString())
                _currentVideo.value = video.copy(manualSubtitleUri = uri.toString())
            }
        }
    }

    /** 取消手动绑定，回到扫描时的自动匹配结果 */
    fun clearManualSubtitle() {
        val video = _currentVideo.value ?: return
        viewModelScope.launch {
            videoDao.setManualSubtitle(video.id, null)
            _currentVideo.value = video.copy(manualSubtitleUri = null)
            externalCues = emptyList()
            externalActive = false
            _externalSubtitleName.value = null
            _externalIsManual.value = false
            _selectedTextIndex.value = null
            _subtitleText.value = ""

            val auto = video.subtitleUri
            if (auto != null) {
                loadSubtitleFile(Uri.parse(auto), manual = false)
            } else {
                attachedPlayer?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            }
        }
    }

    /**
     * 界面退到后台：落进度；未开启后台播放则暂停。
     *
     * 注意 onPause 不只发生在「按返回键离开」，通知授权弹窗、分屏、锁屏都会走这里。
     * 所以只记下「这次暂停是我们主动做的」，回前台时再还原，否则会出现
     * 「打开视频没自动播，要手动点一下」——那一暂停是弹窗引起的。
     */
    fun onActivityPause() {
        saveNow()
        val p = attachedPlayer ?: return
        if (!_backgroundPlay.value) {
            pausedByLeaving = p.isPlaying
            p.pause()
        }
    }

    /** 回到前台：如果上一次暂停是 onPause 替用户做的，就接着放 */
    fun onActivityResume() {
        val p = attachedPlayer ?: return
        if (!pausedByLeaving) return
        pausedByLeaving = false
        if (!p.isPlaying && p.playbackState != Player.STATE_ENDED) p.play()
    }

    fun togglePlay() {
        val p = attachedPlayer ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            // ENDED 状态下 play() 不会自动从头开始
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    // ---------- 倍速 ----------

    fun stepSpeed(delta: Int) = applySpeed(_speedPercent.value + delta)

    fun setSpeed(percent: Int) = applySpeed(percent)

    private fun applySpeed(percent: Int, persist: Boolean = true) {
        val clamped = percent.coerceIn(50, 500)
        _speedPercent.value = clamped
        if (speedBeforeBoost == null) {
            attachedPlayer?.setPlaybackSpeed(clamped / 100f)
        }
        if (persist) {
            viewModelScope.launch { settingsStore.setSpeedPercent(clamped) }
        }
    }

    /** 长按画面：临时拉高倍速，松开还原，不改用户设置 */
    fun beginTemporarySpeedUp() {
        val target = _boostSpeedPercent.value
        if (target <= 0 || speedBeforeBoost != null) return
        speedBeforeBoost = _speedPercent.value
        attachedPlayer?.setPlaybackSpeed(target / 100f)
        _boosting.value = true
    }

    fun endTemporarySpeedUp() {
        val back = speedBeforeBoost ?: return
        speedBeforeBoost = null
        attachedPlayer?.setPlaybackSpeed(back / 100f)
        _boosting.value = false
    }

    fun seekTo(positionMs: Long) {
        val p = attachedPlayer ?: return
        val max = durationMs()
        if (max > 0) p.seekTo(positionMs.coerceIn(0L, max))
    }

    fun seekBy(deltaMs: Long) {
        val p = attachedPlayer ?: return
        seekTo(p.currentPosition + deltaMs)
    }

    // ---------- 播放列表 ----------

    /** 手动点「下一集」：忽略「播完停止」，但遵守随机 */
    /** 本专辑内可播放的视频（排除失效文件），切集一律基于这个列表 */
    private fun albumVideos(): List<VideoEntity> = videos.value.filter { !it.missing }

    /**
     * 手动点「下一集」：遵守随机模式，但不受「播完停止」影响（那是自动连播的规则）。
     */
    fun playNext() {
        cancelCountdown()
        val next = pickNext()
        if (next == null) {
            showHint("已经是最后一集")
            return
        }
        playVideo(next)
    }

    fun playPrevious() {
        val list = albumVideos()
        val current = _currentVideo.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index <= 0) {
            showHint("已经是第一集")
            return
        }
        playVideo(list[index - 1])
    }

    private fun pickNext(): VideoEntity? {
        val list = albumVideos()
        if (list.isEmpty()) return null
        val current = _currentVideo.value

        if (_playlistMode.value == PlaylistMode.SHUFFLE) {
            val pool = list.filter { it.id != current?.id }
            return if (pool.isEmpty()) list.first() else pool.random()
        }

        val index = list.indexOfFirst { it.id == current?.id }
        if (index < 0) return list.firstOrNull()
        return list.getOrNull(index + 1)
    }

    fun cyclePlaylistMode() {
        val next = PlaylistMode.next(_playlistMode.value)
        _playlistMode.value = next
        attachedPlayer?.let { applyPlaylistMode(it) }
        viewModelScope.launch { settingsStore.setPlaylistMode(next) }
    }

    fun setPlaylistMode(mode: String) {
        _playlistMode.value = mode
        attachedPlayer?.let { applyPlaylistMode(it) }
        viewModelScope.launch { settingsStore.setPlaylistMode(mode) }
    }

    private fun onEnded() {
        cancelCountdown()
        saveNow()

        when (_playlistMode.value) {
            PlaylistMode.LOOP_ONE -> {
                attachedPlayer?.seekTo(0)
                attachedPlayer?.play()
            }

            PlaylistMode.STOP_AFTER_ONE -> attachedPlayer?.pause()

            else -> {
                val next = pickNext()
                if (next == null) {
                    showHint("已播完最后一集")
                    return
                }
                if (nextCountdownEnabled) startCountdown(next) else playVideo(next)
            }
        }
    }

    private fun startCountdown(next: VideoEntity) {
        countdownJob = viewModelScope.launch {
            for (left in COUNTDOWN_SECONDS downTo 1) {
                _nextUp.value = NextUpState(next.displayName, left)
                delay(1_000)
            }
            _nextUp.value = null
            playVideo(next)
        }
    }

    fun playNextNow() {
        val next = pickNext() ?: return
        cancelCountdown()
        playVideo(next)
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (_nextUp.value != null) _nextUp.value = null
    }

    // ---------- 睡眠定时 ----------

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _sleepRemainingSec.value = null
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            while (true) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0L) break
                _sleepRemainingSec.value = ((left + 999) / 1000).toInt()
                delay(1_000)
            }
            _sleepRemainingSec.value = null
            attachedPlayer?.pause()
            showHint("定时关闭：已暂停播放", 3_000L)
        }
    }

    /** 顶部提示统一走这里，避免各处自己 delay 导致提示残留或互相覆盖 */
    private fun showHint(text: String, durationMs: Long = 2_500L) {
        _resumeHint.value = text
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(durationMs)
            _resumeHint.value = null
        }
    }

    // ---------- 进度 ----------

    fun durationMs(): Long {
        val d = attachedPlayer?.duration ?: C.TIME_UNSET
        return if (d > 0 && d != C.TIME_UNSET) d else (_currentVideo.value?.durationMs ?: 0L)
    }

    fun positionMs(): Long = (attachedPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)

    fun saveNow() {
        val video = _currentVideo.value ?: return
        val p = attachedPlayer ?: return
        // 播放器上挂的已经不是这个视频了（例如连续起了两次播、或切集还在异步准备中），
        // 这时候的 currentPosition 属于另一个视频，写进去就是错的进度
        if (p.mediaItemCount > 0 && p.currentMediaItem?.mediaId != video.id) return
        val position = p.currentPosition
        if (position <= 0L) return
        viewModelScope.launch {
            playbackRepository.save(video, position, durationMs())
        }
    }

    // ---------- 字幕设置（写库，跨启动保留） ----------

    fun setSubtitleEnabled(enabled: Boolean) {
        _subtitlePrefs.value = _subtitlePrefs.value.copy(enabled = enabled)
        if (!enabled) _subtitleText.value = ""
        viewModelScope.launch { settingsStore.setSubtitleEnabled(enabled) }
    }

    fun setSubtitleFontSize(sp: Float) {
        val v = sp.coerceIn(10f, 30f)
        _subtitlePrefs.value = _subtitlePrefs.value.copy(fontSizeSp = v)
        viewModelScope.launch { settingsStore.setSubtitleFontSize(v) }
    }

    fun setSubtitleColor(argb: Long) {
        _subtitlePrefs.value = _subtitlePrefs.value.copy(colorArgb = argb)
        viewModelScope.launch { settingsStore.setSubtitleColor(argb) }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        val next = (_subtitlePrefs.value.delayMs + deltaMs).coerceIn(-60_000L, 60_000L)
        _subtitlePrefs.value = _subtitlePrefs.value.copy(delayMs = next)
        viewModelScope.launch { settingsStore.setSubtitleDelay(next) }
    }

    fun setSubtitleBottomPadding(dp: Int) {
        val v = dp.coerceIn(8, 160)
        _subtitlePrefs.value = _subtitlePrefs.value.copy(bottomPaddingDp = v)
        viewModelScope.launch { settingsStore.setSubtitleBottomPadding(v) }
    }

    fun selectTextTrack(index: Int?) {
        val p = attachedPlayer ?: return
        val builder = p.trackSelectionParameters.buildUpon()

        when {
            index == null -> {
                externalActive = false
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                _selectedTextIndex.value = null
            }

            index == EXTERNAL_INDEX -> {
                if (externalCues.isEmpty()) return
                externalActive = true
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                _selectedTextIndex.value = EXTERNAL_INDEX
            }

            else -> {
                val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (index !in groups.indices) return
                externalActive = false
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(groups[index].mediaTrackGroup, 0))
                _selectedTextIndex.value = index
            }
        }
        p.trackSelectionParameters = builder.build()
        _subtitleText.value = ""
    }

    fun selectAudioTrack(index: Int) {
        val p = attachedPlayer ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (index !in groups.indices) return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(groups[index].mediaTrackGroup, 0))
            .build()
        _selectedAudioIndex.value = index
    }

    override fun onCleared() {
        saveNow()
        cancelCountdown()
        sleepJob?.cancel()
        attachedPlayer?.removeListener(listener)
        attachedPlayer = null
        // 只释放控制器，播放器归 Service 所有，退出界面不应停止播放
        connection.release()
        super.onCleared()
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        const val KEY_VIDEO_ID = "videoId"
        const val EXTERNAL_INDEX = -1
        private const val COUNTDOWN_SECONDS = 5

        /**
         * 播放队列的上限。
         *
         * 队列随 timeline 经 Binder 发给控制器，单次事务有大小上限（约 1MB）。
         * 每个 MediaItem 序列化后大概几百字节，500 集约 200KB，留足余量。
         * 超过这个数就退化为单集队列 —— 通知里没有上下集按钮，但不会崩。
         */
        private const val MAX_QUEUE_ITEMS = 500
    }
}

internal fun formatSpeed(percent: Int): String =
    if (percent % 10 == 0) {
        String.format("%.1fx", percent / 100f)
    } else {
        String.format("%.2fx", percent / 100f)
    }

internal fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
