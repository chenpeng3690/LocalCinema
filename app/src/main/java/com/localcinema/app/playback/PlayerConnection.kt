package com.localcinema.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
 *
 * 连接是**引用计数**的：播放页进去 acquire、销毁 release，只有最后一个使用者
 * 退出时才真的断开。否则会出现这种时序 —— 新播放页 onCreate 里刚 connect，
 * 上一个播放页才走到 onDestroy/onCleared 把连接 release 掉，新页面永远等不到
 * player，表现就是黑屏、必须退出重进。
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** 当前有多少个使用者在等这个连接 */
    private var clients = 0

    /** 播放页进入时调用，与 release 成对出现 */
    @Synchronized
    fun acquire() {
        clients++
        connectLocked()
    }

    /** 连接失败或断开后重试用；已连上时什么都不做 */
    @Synchronized
    fun ensureConnected() {
        if (_player.value == null) connectLocked()
    }

    @Synchronized
    fun release() {
        clients = (clients - 1).coerceAtLeast(0)
        // 还有别人在用（新旧播放页交替的那一刻），不要拆连接
        if (clients > 0) return
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _player.value = null
    }

    private fun connectLocked() {
        if (controllerFuture != null) return
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                synchronized(this) {
                    // 失败时清掉 future，下次 ensureConnected 才有机会重连
                    if (controller == null && controllerFuture === future) controllerFuture = null
                    _player.value = controller
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun currentPlayer(): Player? = _player.value
}
