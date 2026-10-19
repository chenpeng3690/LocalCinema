package com.localcinema.app.ui.folder

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localcinema.app.data.db.AlbumDao
import com.localcinema.app.data.db.AlbumEntity
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.repo.LibraryRepository
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 一个受管目录。
 *
 * [albumId] 为 null 表示这个目录拿到过 SAF 授权但还没建成专辑 ——
 * 这种目录在首页是看不到的，正是「文件夹」页要解决的问题。
 */
data class FolderRow(
    val treeUri: String,
    val displayPath: String,
    val albumId: String?,
    val albumName: String?,
    val videoCount: Int,
    val authorized: Boolean
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val albumDao: AlbumDao,
    private val videoDao: VideoDao,
    private val repository: LibraryRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _rows = MutableStateFlow<List<FolderRow>>(emptyList())
    val rows: StateFlow<List<FolderRow>> = _rows.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val albums = albumDao.observeAll().first()
            val counts = withContext(Dispatchers.IO) { videoDao.countGrouped() }
                .associate { it.albumId to it.count }
            val persisted = persistedTrees()

            val albumRows = albums.map { album ->
                FolderRow(
                    treeUri = album.treeUri.orEmpty(),
                    displayPath = album.treeUri?.let { prettyTreePath(it) } ?: "路径未授权",
                    albumId = album.id,
                    albumName = album.name,
                    videoCount = counts[album.id] ?: 0,
                    authorized = album.treeUri != null && album.treeUri in persisted
                )
            }

            val known = albums.mapNotNull { it.treeUri }.toSet()
            val orphans = persisted
                .filter { it.isNotEmpty() && it !in known }
                .map { uri ->
                    FolderRow(
                        treeUri = uri,
                        displayPath = prettyTreePath(uri),
                        albumId = null,
                        albumName = null,
                        videoCount = 0,
                        authorized = true
                    )
                }

            _rows.value = albumRows + orphans
        }
    }

    /** 系统里仍然有效的持久授权目录 */
    private suspend fun persistedTrees(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    /** 把 content:// tree uri 转成人看得懂的路径 */
    private fun prettyTreePath(treeUri: String): String {
        val decoded = Uri.decode(treeUri)
        val tail = decoded.substringAfter("/tree/", "")
        if (tail.isBlank()) return decoded
        return tail
            .replace("primary:", "内部存储/")
            .replace("home:", "文档/")
            .replace(':', '/')
    }

    fun addAsAlbum(row: FolderRow) {
        if (row.treeUri.isBlank()) return
        _scanning.value = true
        viewModelScope.launch {
            val albumId = repository.addAlbum(Uri.parse(row.treeUri))
            if (albumId == null) {
                _scanning.value = false
                Toast.makeText(context, "无法读取该目录，请重新授权", Toast.LENGTH_SHORT).show()
                return@launch
            }
            repository.scanAlbum(albumId)
            _scanning.value = false
            refresh()
        }
    }

    fun rescan(row: FolderRow) {
        val albumId = row.albumId ?: return
        _scanning.value = true
        viewModelScope.launch {
            repository.scanAlbum(albumId)
            _scanning.value = false
            refresh()
        }
    }

    fun deleteAlbum(row: FolderRow) {
        val albumId = row.albumId ?: return
        viewModelScope.launch {
            albumDao.getById(albumId)?.let { repository.deleteAlbum(it) }
            refresh()
        }
    }

    fun onTreePicked(uri: Uri) {
        _scanning.value = true
        viewModelScope.launch {
            val albumId = repository.addAlbum(uri)
            if (albumId != null) repository.scanAlbum(albumId)
            _scanning.value = false
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(viewModel: FolderViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuRow by remember { mutableStateOf<FolderRow?>(null) }
    var deleteRow by remember { mutableStateOf<FolderRow?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onTreePicked(uri)
    }

    // 每次进入这个 Tab 重新读一次授权状态（用户可能在系统设置里撤销过）
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {
            Text(
                text = "文件夹",
                style = PlayerType.PageTitle,
                color = PlayerColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = if (rows.isEmpty()) {
                    "还没有授权过任何目录"
                } else {
                    "已授权 ${rows.size} 个目录 · ${rows.count { it.albumId == null }} 个未添加到专辑"
                },
                style = PlayerType.Caption,
                color = PlayerColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(Dimens.radiusControl))
                    .background(PlayerColors.Accent)
                    .clickable(enabled = !scanning) { picker.launch(null) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CreateNewFolder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (scanning) "正在扫描…" else "添加目录",
                    color = Color.White,
                    fontSize = 12.5.sp
                )
            }
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "选一个存放视频的目录，之后在这里能看到所有授权过的目录",
                    fontSize = 11.5.sp,
                    color = PlayerColors.TextTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
            ) {
                items(rows, key = { it.treeUri + it.albumId }) { row ->
                    FolderRowItem(
                        row = row,
                        onClick = {
                            if (row.albumId != null) {
                                menuRow = row
                            } else {
                                viewModel.addAsAlbum(row)
                            }
                        }
                    )
                }
            }
        }
    }

    // ---------- 目录操作 ----------
    menuRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { menuRow = null },
            sheetState = sheetState,
            containerColor = PlayerColors.SurfaceElevated,
            contentColor = PlayerColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
                    Text(
                        text = row.albumName.orEmpty(),
                        style = PlayerType.TopBarTitle,
                        color = PlayerColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = row.displayPath,
                        fontSize = 10.sp,
                        color = PlayerColors.TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                FolderAction(
                    icon = Icons.Rounded.Refresh,
                    label = "重新扫描",
                    enabled = row.authorized,
                    onClick = {
                        viewModel.rescan(row)
                        menuRow = null
                    }
                )
                FolderAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "删除专辑",
                    danger = true,
                    onClick = {
                        menuRow = null
                        deleteRow = row
                    }
                )
            }
        }
    }

    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            containerColor = PlayerColors.SurfaceElevated,
            titleContentColor = PlayerColors.TextPrimary,
            textContentColor = PlayerColors.TextSecondary,
            title = { Text("删除「${row.albumName}」？", fontSize = 16.sp) },
            text = {
                Text(
                    text = "将移除该专辑下的所有视频记录与播放进度。\n手机里的视频文件不会被删除。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(row)
                    deleteRow = null
                }) {
                    Text("删除", color = PlayerColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRow = null }) {
                    Text("取消", color = PlayerColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun FolderRowItem(row: FolderRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding, vertical = 5.dp)
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PlayerColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (row.authorized) {
                    Icons.Rounded.Folder
                } else {
                    Icons.Rounded.FolderOff
                },
                contentDescription = null,
                tint = if (row.authorized) PlayerColors.Icon else PlayerColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp)
        ) {
            Text(
                text = row.albumName ?: row.displayPath.substringAfterLast('/').ifBlank { row.displayPath },
                style = PlayerType.ListTitle,
                color = PlayerColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.displayPath,
                fontSize = 9.5.sp,
                color = PlayerColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                !row.authorized -> "授权失效"
                row.albumId == null -> "未添加到专辑"
                else -> "${row.videoCount} 个视频"
            },
            fontSize = 9.5.sp,
            color = when {
                !row.authorized -> PlayerColors.Accent
                row.albumId == null -> PlayerColors.Accent
                else -> PlayerColors.TextSecondary
            }
        )
    }
}

@Composable
private fun FolderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> PlayerColors.TextTertiary
        danger -> PlayerColors.Accent
        else -> PlayerColors.Icon
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(13.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (danger && enabled) PlayerColors.Accent else PlayerColors.TextPrimary
        )
    }
}
