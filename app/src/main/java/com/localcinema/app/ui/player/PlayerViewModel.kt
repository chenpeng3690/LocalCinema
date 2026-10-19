package com.localcinema.app.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.repo.PlaybackRepository
import com.localcinema.app.data.scanner.NaturalOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    videoDao: VideoDao,
    private val playbackRepository: PlaybackRepository,
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
        })

        viewModelScope.launch {
            val list = videoFlow.first()
            if (list.isEmpty()) return@launch
            val target = list.firstOrNull { it.id == startVideoId } ?: list.first()
            playVideo(target)
        }

        // 每 5 秒落一次进度，最多丢 5 秒
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveNow()
            }
        }
    }

    fun playVideo(video: VideoEntity) {
        viewModelScope.launch {
            saveNow()
            val startMs = playbackRepository.resumePosition(video)
            playbackRepository.markStarted(video)

            _currentVideo.value = video
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
        }
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
            // 已经是最后一集，停在结尾
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
        val duration = durationMs()
        viewModelScope.launch {
            playbackRepository.save(video, position, duration)
        }
    }

    override fun onCleared() {
        saveNow()
        player.release()
        super.onCleared()
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        const val KEY_VIDEO_ID = "videoId"
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
