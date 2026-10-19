package com.localcinema.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 与 PlaybackService 建立 MediaController 连接。
 *
 * MediaController 是异步连接的，连上之前拿不到 player，
 * 所以对外暴露 StateFlow<Player?>，UI 和 ViewModel 都要处理 null。
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                _player.value = runCatching { future.get() }.getOrNull()
            },
            MoreExecutors.directExecutor()
        )
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _player.value = null
    }

    fun currentPlayer(): Player? = _player.value
}
