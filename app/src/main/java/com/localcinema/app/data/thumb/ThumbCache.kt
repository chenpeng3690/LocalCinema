package com.localcinema.app.data.thumb

import android.net.Uri
import com.localcinema.app.data.db.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局缩略图缓存。
 *
 * 首页「继续观看」、「最近」、「文件夹」都要显示缩略图，
 * 如果各写一份状态，同一部片子会在三个地方重复抽帧。
 */
@Singleton
class ThumbCache @Inject constructor(
    private val thumbs: ThumbnailStore
) {

    private val _paths = MutableStateFlow<Map<String, String>>(emptyMap())
    val paths: StateFlow<Map<String, String>> = _paths.asStateFlow()

    private val inFlight = mutableSetOf<String>()

    /** 有缓存直接用；没有就在 IO 线程抽帧，抽完更新状态 */
    fun ensure(video: VideoEntity, scope: CoroutineScope) {
        val id = video.id
        if (_paths.value.containsKey(id) || inFlight.contains(id)) return

        val cached = thumbs.fileFor(id)
        if (cached.exists() && cached.length() > 0) {
            _paths.update { it + (id to cached.absolutePath) }
            return
        }

        inFlight += id
        scope.launch(Dispatchers.IO) {
            val file = thumbs.ensure(
                uri = Uri.parse(video.uri),
                videoId = id,
                durationMs = video.durationMs
            )
            inFlight -= id
            if (file != null) {
                _paths.update { it + (id to file.absolutePath) }
            }
        }
    }
}
