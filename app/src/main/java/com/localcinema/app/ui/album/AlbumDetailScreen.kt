package com.localcinema.app.ui.album

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
import com.localcinema.app.data.db.SortMode
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.data.repo.LibraryRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** 排序选项，顺序即菜单顺序 */
val SORT_OPTIONS = listOf(
    SortMode.NAME_NATURAL to "名称",
    SortMode.ADDED to "最近加入",
    SortMode.MODIFIED to "修改时间",
    SortMode.DURATION to "时长"
)

fun sortLabel(mode: String): String =
    SORT_OPTIONS.firstOrNull { it.first == mode }?.second ?: "名称"

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val albumDao: AlbumDao,
    private val videoDao: VideoDao,
    private val thumbs: ThumbnailStore,
    private val playbackRepository: PlaybackRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsStore: SettingsStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle.get<String>("albumId"))

    /** 上次播放的视频，用于列表高亮 */
    val lastVideoId: StateFlow<String?> = settingsStore.settings
        .map { it.lastVideoId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NAME_NATURAL)
    val sortMode: StateFlow<String> = _sortMode.asStateFlow()

    init {
        // 进入专辑即记录，供下次冷启动直达
        viewModelScope.launch {
            albumDao.touch(albumId, System.currentTimeMillis())
            settingsStore.setLastAlbum(albumId)
            albumDao.getById(albumId)?.let { _sortMode.value = it.sortMode }
        }
    }

    fun albumIdValue(): String = albumId

    val album: StateFlow<AlbumEntity?> = albumDao.observeById(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val videoFlow = videoDao.observeByAlbum(albumId)

    /** 失效记录：目录里已找不到的文件，单独提示与清理 */
    val missing: StateFlow<List<VideoEntity>> = videoDao.observeMissing(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<VideoRowUi>> = combine(
        videoFlow,
        playbackRepository.observeStates(),
        _sortMode,
        _query
    ) { list, states, sort, query ->
        val keyword = query.trim()
        val filtered = if (keyword.isEmpty()) {
            list
        } else {
            list.filter { it.displayName.contains(keyword, ignoreCase = true) }
        }

        val sorted = when (sort) {
            SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
            SortMode.ADDED -> filtered.sortedByDescending { it.addedAt }
            SortMode.MODIFIED -> filtered.sortedByDescending { it.lastModified }
            else -> filtered.sortedWith(compareBy(NaturalOrder) { it.displayName })
        }

        sorted.map { video ->
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

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSortMode(mode: String) {
        _sortMode.value = mode
        viewModelScope.launch {
            albumDao.getById(albumId)?.let { albumDao.upsert(it.copy(sortMode = mode)) }
        }
    }

    fun markFinished(video: VideoEntity) {
        viewModelScope.launch { playbackRepository.markFinished(video) }
    }

    fun clearProgress(video: VideoEntity) {
        viewModelScope.launch { playbackRepository.clear(video.id) }
    }

    fun removeVideo(video: VideoEntity) {
        viewModelScope.launch {
            runCatching { thumbs.fileFor(video.id).delete() }
            videoDao.deleteById(video.id)
        }
    }

    // ---------- 多选 ----------
    private val _selectMode = MutableStateFlow(false)
    val selectMode: StateFlow<Boolean> = _selectMode.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun enterSelectMode() {
        _selectMode.value = true
        _selected.value = emptySet()
    }

    fun exitSelectMode() {
        _selectMode.value = false
        _selected.value = emptySet()
    }

    fun toggleSelect(id: String) {
        _selected.update { if (id in it) it - id else it + id }
    }

    /** 全选 / 取消全选。只作用于当前过滤结果，不会把搜索外的视频也选上 */
    fun toggleSelectAll() {
        val all = rows.value.map { it.video.id }.toSet()
        _selected.value = if (all.isNotEmpty() && _selected.value.containsAll(all)) {
            emptySet()
        } else {
            all
        }
    }

    fun batchMarkFinished() = viewModelScope.launch {
        val ids = _selected.value
        rows.value.filter { it.video.id in ids }.forEach { playbackRepository.markFinished(it.video) }
        exitSelectMode()
    }

    fun batchClearProgress() = viewModelScope.launch {
        _selected.value.forEach { playbackRepository.clear(it) }
        exitSelectMode()
    }

    fun batchRemove() = viewModelScope.launch {
        val ids = _selected.value
        rows.value.filter { it.video.id in ids }.forEach { row ->
            runCatching { thumbs.fileFor(row.video.id).delete() }
            videoDao.deleteById(row.video.id)
        }
        exitSelectMode()
    }

    fun clearMissing() {
        viewModelScope.launch { libraryRepository.clearMissing(albumId) }
    }

    fun setCover(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val album = albumDao.getById(albumId)
            if (album == null) {
                onDone(false)
                return@launch
            }
            onDone(libraryRepository.setAlbumCover(album, uri))
        }
    }

    /**
     * 进入专辑时要自动打开的视频（开关打开且上次视频属于本专辑）。
     * 这里直接读设置而不是用 StateFlow.value：WhileSubscribed 在没有订阅者时不会产出值。
     */
    suspend fun resolveAutoResume(): String? {
        val settings = settingsStore.settings.first()
        if (!settings.autoResumeLastVideo) return null
        val id = settings.lastVideoId ?: return null
        return if (videoDao.getById(id)?.albumId == albumId) id else null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val thumbs by viewModel.thumbPaths.collectAsStateWithLifecycle()
    val lastVideoId by viewModel.lastVideoId.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val selectMode by viewModel.selectMode.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState()

    var searchOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var menuVideo by remember { mutableStateOf<VideoRowUi?>(null) }
    var missingOpen by remember { mutableStateOf(false) }
    var autoResumeDone by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setCover(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "封面已更新" else "无法读取该图片",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun openPlayer(videoId: String) {
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                albumId = viewModel.albumIdValue(),
                videoId = videoId
            )
        )
    }

    // 进入专辑后滚动到上次播放的那一集（前面有 Hero 与分组标题两个 item）
    LaunchedEffect(rows.size, lastVideoId) {
        val target = lastVideoId ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.video.id == target }
        if (index >= 0) {
            delay(250)
            listState.animateScrollToItem(index + 2)
        }
    }

    // 自动续播：开关打开时直接进播放页，只触发一次
    LaunchedEffect(rows.size) {
        if (autoResumeDone || rows.isEmpty()) return@LaunchedEffect
        autoResumeDone = true
        viewModel.resolveAutoResume()?.let { openPlayer(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = album?.name ?: "",
            searchOpen = searchOpen,
            selectMode = selectMode,
            selectedCount = selected.size,
            onBack = onBack,
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) viewModel.setQuery("")
            },
            onToggleSelect = {
                if (selectMode) viewModel.exitSelectMode() else viewModel.enterSelectMode()
            },
            onSelectAll = viewModel::toggleSelectAll
        )

        if (searchOpen && !selectMode) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("搜索本专辑的视频", fontSize = 12.sp) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.pagePadding, vertical = 4.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                AlbumHero(
                    album = album,
                    videoCount = rows.size,
                    onPickCover = { coverPicker.launch(arrayOf("image/*")) },
                    onPlay = {
                        val target = rows.firstOrNull { it.video.id == lastVideoId }?.video
                            ?: rows.firstOrNull()?.video
                        if (target != null) openPlayer(target.id)
                    }
                )
            }

            if (missing.isNotEmpty()) {
                item {
                    MissingBanner(
                        count = missing.size,
                        onClick = { missingOpen = true }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Dimens.pagePadding,
                            end = Dimens.pagePadding,
                            top = 16.dp,
                            bottom = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "全部 ${rows.size} 集"
                        } else {
                            "找到 ${rows.size} 集"
                        },
                        style = PlayerType.SectionTitle,
                        color = PlayerColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { sortOpen = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = null,
                            tint = PlayerColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = sortLabel(sortMode),
                            style = PlayerType.Caption,
                            color = PlayerColors.TextSecondary
                        )
                    }
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
                            text = if (query.isBlank()) "这个专辑里没有找到视频" else "没有匹配的视频",
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
                        isLastPlayed = row.video.id == lastVideoId && !selectMode,
                        selectMode = selectMode,
                        selected = row.video.id in selected,
                        onClick = {
                            if (selectMode) viewModel.toggleSelect(row.video.id) else openPlayer(row.video.id)
                        },
                        onLongClick = {
                            if (selectMode) viewModel.toggleSelect(row.video.id) else menuVideo = row
                        }
                    )
                }
            }
        }

        if (selectMode) {
            BatchBar(
                count = selected.size,
                onMarkFinished = viewModel::batchMarkFinished,
                onClearProgress = viewModel::batchClearProgress,
                onRemove = viewModel::batchRemove
            )
        }
    }

    // ---------- 排序 ----------
    if (sortOpen) {
        ModalBottomSheet(
            onDismissRequest = { sortOpen = false },
            sheetState = sheetState,
            containerColor = PlayerColors.SurfaceElevated,
            contentColor = PlayerColors.TextPrimary
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 14.dp)) {
                Text(
                    text = "排序方式",
                    fontSize = 15.sp,
                    color = PlayerColors.TextPrimary,
                    modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 8.dp)
                )
                SORT_OPTIONS.forEach { (mode, label) ->
                    SheetAction(
                        icon = Icons.AutoMirrored.Rounded.Sort,
                        label = label,
                        highlight = mode == sortMode,
                        onClick = {
                            viewModel.setSortMode(mode)
                            sortOpen = false
                        }
                    )
                }
            }
        }
    }

    // ---------- 单集操作 ----------
    menuVideo?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { menuVideo = null },
            sheetState = sheetState,
            containerColor = PlayerColors.SurfaceElevated,
            contentColor = PlayerColors.TextPrimary
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 14.dp)) {
                Text(
                    text = row.video.displayName,
                    fontSize = 14.sp,
                    color = PlayerColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(6.dp))
                SheetAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "播放",
                    onClick = {
                        menuVideo = null
                        openPlayer(row.video.id)
                    }
                )
                SheetAction(
                    icon = Icons.Rounded.CheckCircle,
                    label = "标记为已看完",
                    onClick = {
                        viewModel.markFinished(row.video)
                        menuVideo = null
                    }
                )
                SheetAction(
                    icon = Icons.Rounded.Restore,
                    label = "清除观看进度",
                    onClick = {
                        viewModel.clearProgress(row.video)
                        menuVideo = null
                    }
                )
                SheetAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "从专辑中移除",
                    danger = true,
                    onClick = {
                        viewModel.removeVideo(row.video)
                        menuVideo = null
                        Toast.makeText(context, "已移除记录，视频文件未删除", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // ---------- 失效文件 ----------
    if (missingOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { missingOpen = false },
            containerColor = PlayerColors.SurfaceElevated,
            titleContentColor = PlayerColors.TextPrimary,
            textContentColor = PlayerColors.TextSecondary,
            title = { Text("${missing.size} 个文件已失效", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        text = "这些文件在目录里已经找不到了，可能是被移动或删除。清理只移除记录，不会动手机里的文件。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    missing.take(6).forEach { video ->
                        Text(
                            text = "· ${video.displayName}",
                            fontSize = 11.sp,
                            color = PlayerColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (missing.size > 6) {
                        Text(
                            text = "… 等 ${missing.size} 个",
                            fontSize = 11.sp,
                            color = PlayerColors.TextTertiary
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.clearMissing()
                    missingOpen = false
                }) {
                    Text("清理记录", color = PlayerColors.Accent)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { missingOpen = false }) {
                    Text("取消", color = PlayerColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    searchOpen: Boolean,
    selectMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSelect: () -> Unit,
    onSelectAll: () -> Unit
) {
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
                .clickable(onClick = if (selectMode) onToggleSelect else onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selectMode) {
                    Icons.Rounded.Close
                } else {
                    Icons.AutoMirrored.Rounded.ArrowBack
                },
                contentDescription = if (selectMode) "退出多选" else "返回",
                tint = PlayerColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = if (selectMode) "已选 $selectedCount 项" else title,
            style = PlayerType.TopBarTitle,
            color = PlayerColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )

        if (selectMode) {
            Text(
                text = "全选",
                fontSize = 12.sp,
                color = PlayerColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onSelectAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchOpen) "关闭搜索" else "搜索",
                    tint = PlayerColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Checklist,
                    contentDescription = "多选",
                    tint = PlayerColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MissingBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding, vertical = 4.dp)
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(PlayerColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count 个文件已失效",
            fontSize = 11.5.sp,
            color = PlayerColors.Accent,
            modifier = Modifier.weight(1f)
        )
        Text(text = "查看 ›", fontSize = 11.sp, color = PlayerColors.TextSecondary)
    }
}

@Composable
private fun AlbumHero(
    album: AlbumEntity?,
    videoCount: Int,
    onPickCover: () -> Unit,
    onPlay: () -> Unit
) {
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
        // 换封面：放在左上角，不与右下角的播放键抢位置
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp, top = 10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(PlayerColors.BadgeScrim)
                .clickable(onClick = onPickCover),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = "更换封面",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 13.dp, bottom = 13.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(PlayerColors.Accent)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                danger -> PlayerColors.Accent
                highlight -> PlayerColors.Accent
                else -> PlayerColors.Icon
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(13.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = when {
                danger -> PlayerColors.Accent
                highlight -> PlayerColors.Accent
                else -> PlayerColors.TextPrimary
            },
            modifier = Modifier.weight(1f)
        )
        if (highlight) {
            Text(text = "✓", fontSize = 13.sp, color = PlayerColors.Accent)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(
    ui: VideoRowUi,
    thumbPath: String?,
    isLastPlayed: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val video = ui.video
    val highlightShape = RoundedCornerShape(11.dp)
    val highlighted = isLastPlayed || selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding, vertical = 9.dp)
            .then(
                if (highlighted) {
                    Modifier
                        .clip(highlightShape)
                        .background(PlayerColors.AccentSoft)
                        .border(1.5.dp, PlayerColors.Accent, highlightShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectMode) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(PlayerColors.Accent)
                        } else {
                            Modifier.border(1.5.dp, PlayerColors.Chevron, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text(text = "✓", fontSize = 10.sp, color = Color.White)
                }
            }
            Spacer(Modifier.width(9.dp))
        }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.displayName,
                    style = PlayerType.ListTitle,
                    color = PlayerColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isLastPlayed) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "上次播放",
                        fontSize = 8.5.sp,
                        color = Color.White,
                        modifier = Modifier
                            .background(PlayerColors.Accent, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }
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

@Composable
private fun BatchBar(
    count: Int,
    onMarkFinished: () -> Unit,
    onClearProgress: () -> Unit,
    onRemove: () -> Unit
) {
    val enabled = count > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlayerColors.SurfaceElevated)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BatchAction(
            icon = Icons.Rounded.CheckCircle,
            label = "标记已看",
            enabled = enabled,
            onClick = onMarkFinished
        )
        BatchAction(
            icon = Icons.Rounded.Restore,
            label = "清进度",
            enabled = enabled,
            onClick = onClearProgress
        )
        BatchAction(
            icon = Icons.Rounded.DeleteOutline,
            label = "移除记录",
            enabled = enabled,
            danger = true,
            onClick = onRemove
        )
    }
}

@Composable
private fun BatchAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> PlayerColors.TextTertiary.copy(alpha = 0.45f)
        danger -> PlayerColors.Accent
        else -> PlayerColors.TextSecondary
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 9.5.sp, color = tint)
    }
}
