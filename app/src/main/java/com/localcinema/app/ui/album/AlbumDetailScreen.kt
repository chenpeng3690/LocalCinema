package com.localcinema.app.ui.album

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.localcinema.app.data.db.AlbumDao
import com.localcinema.app.data.db.AlbumEntity
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.repo.PlaybackRepository
import com.localcinema.app.data.scanner.NaturalOrder
import com.localcinema.app.data.scanner.formatDuration
import com.localcinema.app.data.scanner.resolutionLabel
import com.localcinema.app.data.thumb.ThumbnailStore
import com.localcinema.app.ui.player.PlayerActivity
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.absoluteValue

data class VideoRowUi(
    val video: VideoEntity,
    val progress: Float,
    val finished: Boolean,
    val lastPositionMs: Long
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    albumDao: AlbumDao,
    videoDao: VideoDao,
    private val thumbs: ThumbnailStore,
    playbackRepository: PlaybackRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle.get<String>("albumId"))

    fun albumIdValue(): String = albumId

    val album: StateFlow<AlbumEntity?> = albumDao.observeById(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val videoFlow = videoDao.observeByAlbum(albumId)
        .map { list -> list.sortedWith(compareBy(NaturalOrder) { it.displayName }) }

    val rows: StateFlow<List<VideoRowUi>> = combine(
        videoFlow,
        playbackRepository.observeStates()
    ) { list, states ->
        list.map { video ->
            val state = states[video.id]
            val duration = if (video.durationMs > 0) video.durationMs else (state?.durationMs ?: 0L)
            val position = state?.positionMs ?: 0L
            VideoRowUi(
                video = video,
                progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                finished = state?.finished == true,
                lastPositionMs = position
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _thumbPaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val thumbPaths: StateFlow<Map<String, String>> = _thumbPaths.asStateFlow()

    private val inFlight = mutableSetOf<String>()

    fun ensureThumb(video: VideoEntity) {
        val id = video.id
        if (_thumbPaths.value.containsKey(id) || inFlight.contains(id)) return

        val cached = thumbs.fileFor(id)
        if (cached.exists() && cached.length() > 0) {
            _thumbPaths.update { it + (id to cached.absolutePath) }
            return
        }

        inFlight += id
        viewModelScope.launch {
            val file = thumbs.ensure(
                uri = Uri.parse(video.uri),
                videoId = id,
                durationMs = video.durationMs
            )
            inFlight -= id
            if (file != null) {
                _thumbPaths.update { it + (id to file.absolutePath) }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val thumbs by viewModel.thumbPaths.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = album?.name ?: "", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { AlbumHero(album = album, videoCount = rows.size) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Dimens.pagePadding,
                            end = Dimens.pagePadding,
                            top = 20.dp,
                            bottom = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部 ${rows.size} 集",
                        style = PlayerType.SectionTitle,
                        color = PlayerColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "自然排序",
                        style = PlayerType.Caption,
                        color = PlayerColors.TextSecondary
                    )
                }
            }

            if (rows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoFile,
                            contentDescription = null,
                            tint = PlayerColors.TextTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "这个专辑里没有找到视频",
                            fontSize = 12.sp,
                            color = PlayerColors.TextSecondary
                        )
                    }
                }
            } else {
                items(rows, key = { it.video.id }) { row ->
                    LaunchedEffect(row.video.id) { viewModel.ensureThumb(row.video) }
                    VideoRow(
                        ui = row,
                        thumbPath = thumbs[row.video.id],
                        onClick = {
                            context.startActivity(
                                PlayerActivity.intent(
                                    context = context,
                                    albumId = viewModel.albumIdValue(),
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

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding - 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = PlayerColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = title,
            style = PlayerType.TopBarTitle,
            color = PlayerColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
    }
}

@Composable
private fun AlbumHero(album: AlbumEntity?, videoCount: Int) {
    val name = album?.name ?: ""
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding)
            .height(132.dp)
            .clip(RoundedCornerShape(Dimens.radiusHero))
            .background(heroBrush(name))
    ) {
        val cover = album?.coverPath
        if (cover != null) {
            AsyncImage(
                model = File(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to PlayerColors.HeroScrimTop,
                        1f to PlayerColors.HeroScrimBottom
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 13.dp, bottom = 13.dp, end = 76.dp)
        ) {
            Text(
                text = name,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$videoCount 个视频 · 已授权目录",
                fontSize = 10.5.sp,
                color = PlayerColors.HeroSubText,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 13.dp, bottom = 13.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(PlayerColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun VideoRow(
    ui: VideoRowUi,
    thumbPath: String?,
    onClick: () -> Unit
) {
    val video = ui.video
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.pagePadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(Dimens.radiusThumb))
                .background(PlayerColors.SurfaceElevated)
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
                        .size(16.dp)
                )
            }

            val res = resolutionLabel(video.width, video.height)
            if (res.isNotEmpty()) {
                Text(
                    text = res,
                    fontSize = 8.5.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(PlayerColors.BadgeScrim, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.5.dp)
                )
            }

            if (ui.finished) {
                Text(
                    text = "✓",
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(PlayerColors.Accent, RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp)
                )
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
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatDuration(video.durationMs),
                    style = PlayerType.Caption,
                    color = PlayerColors.TextSecondary
                )
                Text(
                    text = "·",
                    style = PlayerType.Caption,
                    color = PlayerColors.TextTertiary
                )
                Text(
                    text = when {
                        ui.finished -> "已看完"
                        ui.lastPositionMs > 0 -> "已看 ${formatDuration(ui.lastPositionMs)}"
                        else -> video.container?.uppercase() ?: "未知"
                    },
                    style = PlayerType.Caption,
                    color = if (ui.lastPositionMs > 0 && !ui.finished) {
                        PlayerColors.Accent
                    } else {
                        PlayerColors.TextSecondary
                    }
                )
            }

            // 观看进度条：未看过不显示
            if (ui.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .height(Dimens.progressHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PlayerColors.TrackInactive)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ui.progress)
                            .height(Dimens.progressHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PlayerColors.Accent)
                    )
                }
            }
        }
    }
}

private fun heroBrush(seed: String): Brush {
    val palettes = PlayerColors.CoverPalettes
    val idx = seed.hashCode().absoluteValue % palettes.size
    return Brush.linearGradient(palettes[idx])
}
