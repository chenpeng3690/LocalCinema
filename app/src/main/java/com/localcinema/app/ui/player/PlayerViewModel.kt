package com.localcinema.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.repo.PlaybackRepository
import com.localcinema.app.data.scanner.NaturalOrder
import com.localcinema.app.data.subtitle.SubtitleRepository
import com.localcinema.app.data.subtitle.TimedCue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 可切换的轨道。index = -1 代表外挂字幕 */
data class TrackOption(val index: Int, val label: String)

/** 字幕显示参数 */
data class SubtitlePrefs(
    val enabled: Boolean = true,
    val fontSizeSp: Float = 14f,
    val colorArgb: Long = 0xFFFFFFFF,
    val delayMs: Long = 0L,
    val bottomPaddingDp: Int = 48
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    videoDao: VideoDao,
    private val playbackRepository: PlaybackRepository,
    private val subtitleRepository: SubtitleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>(KEY_ALBUM_ID) ?: ""
    private val startVideoId: String = savedStateHandle.get<String>(KEY_VIDEO_ID) ?: ""

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
    }

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

    // ---------- 字幕 ----------
    private val _subtitlePrefs = MutableStateFlow(SubtitlePrefs())
    val subtitlePrefs: StateFlow<SubtitlePrefs> = _subtitlePrefs.asStateFlow()

    private val _subtitleText = MutableStateFlow("")
    val subtitleText: StateFlow<String> = _subtitleText.asStateFlow()

    /** 外挂字幕（自解析，支持延迟） */
    private var externalCues: List<TimedCue> = emptyList()
    private var externalActive: Boolean = false

    private val _externalSubtitleName = MutableStateFlow<String?>(null)

    private val _textTracks = MutableStateFlow<List<TrackOption>>(emptyList())

    /** 内嵌轨 + 外挂字幕（index = -1）合并后的可选项 */
    val textTracks: StateFlow<List<TrackOption>> = combine(
        _textTracks,
        _externalSubtitleName
    ) { embedded, externalName ->
        if (externalName == null) embedded else embedded + TrackOption(EXTERNAL_INDEX, "外挂字幕 · $externalName")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null = 关闭；-1 = 外挂；>=0 = 内嵌轨索引 */
    private val _selectedTextIndex = MutableStateFlow<Int?>(null)
    val selectedTextIndex: StateFlow<Int?> = _selectedTextIndex.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackOption>>(emptyList())
    val audioTracks: StateFlow<List<TrackOption>> = _audioTracks.asStateFlow()

    private val _selectedAudioIndex = MutableStateFlow(0)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveNow()
                    playNext(auto = true)
                }
            }

            /** 内嵌字幕走这里；外挂字幕自己渲染，不接收 */
            override fun onCues(cueGroup: CueGroup) {
                if (externalActive) return
                if (!_subtitlePrefs.value.enabled) {
                    if (_subtitleText.value.isNotEmpty()) _subtitleText.value = ""
                    return
                }
                val text = cueGroup.cues
                    .joinToString("\n") { it.text?.toString().orEmpty() }
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
        })

        viewModelScope.launch {
            val list = videoFlow.first()
            if (list.isEmpty()) return@launch
            val target = list.firstOrNull { it.id == startVideoId } ?: list.first()
            playVideo(target)
        }

        // 每 5 秒落一次进度
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveNow()
            }
        }

        // 外挂字幕按「播放位置 + 延迟」筛选，120ms 刷新
        viewModelScope.launch {
            while (true) {
                val prefs = _subtitlePrefs.value
                if (!prefs.enabled) {
                    if (_subtitleText.value.isNotEmpty()) _subtitleText.value = ""
                } else if (externalActive && externalCues.isNotEmpty()) {
                    val position = player.currentPosition + prefs.delayMs
                    val text = externalCues.asSequence()
                        .filter { position >= it.startMs && position <= it.endMs }
                        .joinToString("\n") { it.text }
                    if (text != _subtitleText.value) _subtitleText.value = text
                }
                delay(120)
            }
        }
    }

    // ---------- 播放 ----------

    fun playVideo(video: VideoEntity) {
        viewModelScope.launch {
            saveNow()
            val startMs = playbackRepository.resumePosition(video)
            playbackRepository.markStarted(video)

            _currentVideo.value = video
            externalCues = emptyList()
            externalActive = false
            _externalSubtitleName.value = null
            _selectedTextIndex.value = null
            _subtitleText.value = ""

            player.setMediaItem(MediaItem.fromUri(video.uri))
            player.prepare()
            if (startMs > 0L) {
                player.seekTo(startMs)
                _resumeHint.value = "已跳到 ${formatMs(startMs)}（上次看到这里）"
                viewModelScope.launch {
                    delay(3_000)
                    _resumeHint.value = null
                }
            }
            player.playWhenReady = true

            // 扫描阶段配对好的同名外挂字幕
            video.subtitleUri?.let { loadSubtitleFile(Uri.parse(it)) }
        }
    }

    private suspend fun loadSubtitleFile(uri: Uri) {
        val name = uri.lastPathSegment ?: "subtitle"
        val cues = subtitleRepository.load(uri, name)
        if (cues.isEmpty()) {
            _resumeHint.value = "字幕解析失败：${name.take(24)}"
            return
        }
        externalCues = cues
        externalActive = true
        _externalSubtitleName.value = name
        _selectedTextIndex.value = EXTERNAL_INDEX
        _subtitlePrefs.value = _subtitlePrefs.value.copy(enabled = true)

        // 关闭内嵌字幕轨，避免和外挂字幕重叠显示
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** 手动挂载字幕：只解析文件，不重建播放器，不打断播放 */
    fun loadExternalSubtitle(uri: Uri) {
        viewModelScope.launch { loadSubtitleFile(uri) }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val max = durationMs()
        if (max > 0) player.seekTo(positionMs.coerceIn(0L, max))
    }

    fun seekBy(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun playNext(auto: Boolean = false) {
        val list = videos.value
        val current = _currentVideo.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index >= 0 && index + 1 < list.size) {
            playVideo(list[index + 1])
        } else if (!auto) {
            player.pause()
        }
    }

    fun playPrevious() {
        val list = videos.value
        val current = _currentVideo.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index > 0) playVideo(list[index - 1])
    }

    fun durationMs(): Long {
        val d = player.duration
        return if (d > 0 && d != C.TIME_UNSET) d else (_currentVideo.value?.durationMs ?: 0L)
    }

    fun positionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    fun saveNow() {
        val video = _currentVideo.value ?: return
        val position = player.currentPosition
        if (position <= 0L) return
        viewModelScope.launch {
            playbackRepository.save(video, position, durationMs())
        }
    }

    // ---------- 字幕设置 ----------

    fun setSubtitleEnabled(enabled: Boolean) {
        _subtitlePrefs.value = _subtitlePrefs.value.copy(enabled = enabled)
        if (!enabled) _subtitleText.value = ""
    }

    fun setSubtitleFontSize(sp: Float) {
        _subtitlePrefs.value = _subtitlePrefs.value.copy(fontSizeSp = sp.coerceIn(10f, 30f))
    }

    fun setSubtitleColor(argb: Long) {
        _subtitlePrefs.value = _subtitlePrefs.value.copy(colorArgb = argb)
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        val next = (_subtitlePrefs.value.delayMs + deltaMs).coerceIn(-60_000L, 60_000L)
        _subtitlePrefs.value = _subtitlePrefs.value.copy(delayMs = next)
    }

    fun setSubtitleBottomPadding(dp: Int) {
        _subtitlePrefs.value = _subtitlePrefs.value.copy(bottomPaddingDp = dp.coerceIn(8, 160))
    }

    /** index = null 关闭；-1 外挂；>=0 内嵌轨 */
    fun selectTextTrack(index: Int?) {
        val builder = player.trackSelectionParameters.buildUpon()

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
                val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (index !in groups.indices) return
                externalActive = false
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(groups[index].mediaTrackGroup, 0))
                _selectedTextIndex.value = index
            }
        }
        player.trackSelectionParameters = builder.build()
        _subtitleText.value = ""
    }

    fun selectAudioTrack(index: Int) {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (index !in groups.indices) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(groups[index].mediaTrackGroup, 0))
            .build()
        _selectedAudioIndex.value = index
    }

    override fun onCleared() {
        saveNow()
        player.release()
        super.onCleared()
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        const val KEY_VIDEO_ID = "videoId"
        const val EXTERNAL_INDEX = -1
    }
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
