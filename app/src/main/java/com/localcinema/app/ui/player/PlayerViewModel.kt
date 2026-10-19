package com.localcinema.app.ui.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
    private var playbackStarted = false

    private val videoFlow = videoDao.observeByAlbum(albumId)
        .map { list -> list.sortedWith(compareBy(NaturalOrder) { it.displayName }) }

    val videos: StateFlow<List<VideoEntity>> = videoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentVideo = MutableStateFlow<VideoEntity?>(null)
    val currentVideo: StateFlow<VideoEntity?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _resumeHint = MutableStateFlow<String?>(null)
    val resumeHint: StateFlow<String?> = _resumeHint.asStateFlow()

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

        connection.connect()

        // 连接完成后绑定监听并开始播放
        viewModelScope.launch {
            connection.player.collect { p ->
                if (p != null && p !== attachedPlayer) {
                    attachedPlayer?.removeListener(listener)
                    p.addListener(listener)
                    attachedPlayer = p
                    startPlaybackIfNeeded(p)
                }
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
     * 首次连接时决定播什么。
     * 如果 Service 正在放同一个视频（用户从后台切回来），不打断它，只同步 UI。
     */
    private fun startPlaybackIfNeeded(p: Player) {
        if (playbackStarted) return
        playbackStarted = true

        viewModelScope.launch {
            val list = videoFlow.first()
            if (list.isEmpty()) return@launch
            val target = list.firstOrNull { it.id == startVideoId } ?: list.first()

            val alreadyPlaying = p.mediaItemCount > 0 && p.currentMediaItem?.mediaId == target.id
            if (alreadyPlaying) {
                _currentVideo.value = target
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
            externalCues = emptyList()
            externalActive = false
            _externalSubtitleName.value = null
            _externalIsManual.value = false
            _selectedTextIndex.value = null
            _subtitleText.value = ""

            p.setMediaItem(buildMediaItem(video))
            p.prepare()
            if (startMs > 0L) {
                p.seekTo(startMs)
                _resumeHint.value = "已跳到 ${formatMs(startMs)}（上次看到这里）"
                viewModelScope.launch {
                    delay(3_000)
                    _resumeHint.value = null
                }
            }
            p.playWhenReady = true

            // 手动挂载过就以手动为准，否则用扫描时自动匹配的
            val subtitle = video.manualSubtitleUri ?: video.subtitleUri
            subtitle?.let {
                loadSubtitleFile(Uri.parse(it), manual = video.manualSubtitleUri != null)
            }
        }
    }

    private fun buildMediaItem(video: VideoEntity): MediaItem =
        MediaItem.Builder()
            .setUri(video.uri)
            .setMediaId(video.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.displayName)
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

    /** 界面退到后台：落进度；未开启后台播放则暂停 */
    fun onActivityPause() {
        saveNow()
        if (!_backgroundPlay.value) {
            attachedPlayer?.pause()
        }
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
    fun playNext(auto: Boolean = false) {
        if (!auto) cancelCountdown()
        val next = pickNext(auto) ?: run {
            if (!auto) {
                attachedPlayer?.pause()
                _resumeHint.value = "已经是最后一集"
            }
            return
        }
        playVideo(next)
    }

    fun playPrevious() {
        val list = videos.value
        val current = _currentVideo.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index > 0) playVideo(list[index - 1])
    }

    private fun pickNext(auto: Boolean): VideoEntity? {
        val list = videos.value.filter { !it.missing }
        if (list.isEmpty()) return null
        val current = _currentVideo.value

        if (_playlistMode.value == PlaylistMode.SHUFFLE) {
            val pool = list.filter { it.id != current?.id }
            return if (pool.isEmpty()) list.first() else pool.random()
        }

        val index = list.indexOfFirst { it.id == current?.id }
        val nextIndex = index + 1
        return if (nextIndex in list.indices) list[nextIndex] else null
    }

    fun cyclePlaylistMode() {
        val next = PlaylistMode.next(_playlistMode.value)
        _playlistMode.value = next
        viewModelScope.launch { settingsStore.setPlaylistMode(next) }
    }

    fun setPlaylistMode(mode: String) {
        _playlistMode.value = mode
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
                val next = pickNext(auto = true)
                if (next == null) {
                    _resumeHint.value = "已播完最后一集"
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
        val next = pickNext(auto = true) ?: return
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
            _resumeHint.value = "定时关闭：已暂停播放"
            viewModelScope.launch {
                delay(3_000)
                _resumeHint.value = null
            }
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
        val position = attachedPlayer?.currentPosition ?: return
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
