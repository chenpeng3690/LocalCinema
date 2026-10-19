package com.localcinema.app.ui.home

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.localcinema.app.data.db.AlbumDao
import com.localcinema.app.data.db.AlbumEntity
import com.localcinema.app.data.repo.LibraryRepository
import com.localcinema.app.data.repo.ScanState
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.absoluteValue

@HiltViewModel
class HomeViewModel @Inject constructor(
    albumDao: AlbumDao,
    private val repository: LibraryRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val albums: StateFlow<List<AlbumEntity>> = albumDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState: StateFlow<ScanState> = repository.scanState

    fun onTreeSelected(treeUri: Uri) {
        viewModelScope.launch {
            val albumId = repository.addAlbum(treeUri)
            if (albumId == null) {
                Toast.makeText(appContext, "无法读取该目录，请重新选择", Toast.LENGTH_SHORT).show()
                return@launch
            }
            repository.scanAlbum(albumId)
        }
    }

    fun rescan(album: AlbumEntity) {
        viewModelScope.launch { repository.scanAlbum(album.id) }
    }

    fun rename(album: AlbumEntity, newName: String) {
        viewModelScope.launch {
            repository.renameAlbum(album, newName)
            Toast.makeText(appContext, "已重命名", Toast.LENGTH_SHORT).show()
        }
    }

    fun delete(album: AlbumEntity) {
        viewModelScope.launch {
            repository.deleteAlbum(album)
            Toast.makeText(appContext, "已删除「${album.name}」，视频文件未受影响", Toast.LENGTH_LONG).show()
        }
    }

    fun dismissScan() = repository.resetScanState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenAlbum: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var renameTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onTreeSelected(uri)
    }

    LaunchedEffect(scanState) {
        if (scanState is ScanState.Done || scanState is ScanState.Failed) {
            delay(3_000)
            viewModel.dismissScan()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeHeader(albumCount = albums.size)

            ScanBanner(state = scanState, onDismiss = viewModel::dismissScan)

            if (albums.isEmpty()) {
                EmptyAlbumState(
                    modifier = Modifier.weight(1f),
                    onAddClick = {
                        if (scanState is ScanState.Idle) {
                            picker.launch(null)
                        } else {
                            Toast.makeText(context, "正在扫描，请稍候", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                AlbumGrid(
                    albums = albums,
                    modifier = Modifier.weight(1f),
                    onOpenAlbum = onOpenAlbum,
                    onLongPress = { menuAlbum = it }
                )
            }
        }

        if (albums.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.pagePadding, bottom = 20.dp)
                    .size(Dimens.fabSize)
                    .clip(RoundedCornerShape(Dimens.radiusFab))
                    .background(
                        Brush.linearGradient(
                            listOf(PlayerColors.AccentGradientStart, PlayerColors.AccentGradientEnd)
                        )
                    )
                    .clickable { picker.launch(null) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加专辑",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // ---------- 专辑操作菜单 ----------
    menuAlbum?.let { album ->
        ModalBottomSheet(
            onDismissRequest = { menuAlbum = null },
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
                        text = album.name,
                        style = PlayerType.TopBarTitle,
                        color = PlayerColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "长按触发的专辑操作",
                        style = PlayerType.Caption,
                        color = PlayerColors.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                SheetAction(
                    icon = Icons.Rounded.Refresh,
                    label = "重新扫描",
                    onClick = {
                        menuAlbum = null
                        viewModel.rescan(album)
                    }
                )
                SheetAction(
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    label = "重命名",
                    onClick = {
                        menuAlbum = null
                        renameTarget = album
                    }
                )
                SheetAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "删除专辑",
                    danger = true,
                    onClick = {
                        menuAlbum = null
                        deleteTarget = album
                    }
                )
            }
        }
    }

    // ---------- 重命名 ----------
    renameTarget?.let { album ->
        var text by remember(album.id) { mutableStateOf(album.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = PlayerColors.SurfaceElevated,
            titleContentColor = PlayerColors.TextPrimary,
            textContentColor = PlayerColors.TextSecondary,
            title = { Text("重命名专辑", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(album, text)
                    renameTarget = null
                }) {
                    Text("保存", color = PlayerColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消", color = PlayerColors.TextSecondary)
                }
            }
        )
    }

    // ---------- 删除确认 ----------
    deleteTarget?.let { album ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = PlayerColors.SurfaceElevated,
            titleContentColor = PlayerColors.TextPrimary,
            textContentColor = PlayerColors.TextSecondary,
            title = { Text("删除「${album.name}」？", fontSize = 16.sp) },
            text = {
                Text(
                    text = "将移除该专辑下的所有视频记录与播放进度。\n手机里的视频文件不会被删除，之后可以重新添加。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(album)
                    deleteTarget = null
                }) {
                    Text("删除", color = PlayerColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = PlayerColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) PlayerColors.Accent else PlayerColors.Icon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (danger) PlayerColors.Accent else PlayerColors.TextPrimary
        )
    }
}

@Composable
private fun HomeHeader(albumCount: Int) {
    Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {
        Text(
            text = "影音库",
            style = PlayerType.PageTitle,
            color = PlayerColors.TextPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = if (albumCount == 0) "还没有内容" else "$albumCount 个专辑",
            style = PlayerType.Caption,
            color = PlayerColors.TextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(Dimens.radiusControl))
                    .background(PlayerColors.Surface)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = PlayerColors.TextTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "搜索专辑或视频",
                    fontSize = 12.5.sp,
                    color = PlayerColors.TextTertiary
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Dimens.radiusControl))
                    .background(PlayerColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "筛选",
                    tint = PlayerColors.Icon,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun ScanBanner(state: ScanState, onDismiss: () -> Unit) {
    when (state) {
        is ScanState.Idle -> Unit

        is ScanState.Running -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.pagePadding, vertical = 10.dp)
                    .clip(RoundedCornerShape(Dimens.radiusControl))
                    .background(PlayerColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "正在扫描… 已查看 ${state.scanned} 个文件 · 找到 ${state.found} 个视频",
                    fontSize = 11.5.sp,
                    color = PlayerColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PlayerColors.Accent,
                    trackColor = PlayerColors.TrackInactive
                )
            }
        }

        is ScanState.Done -> BannerRow(
            text = "已添加 ${state.count} 个视频",
            tint = PlayerColors.DoneGreen,
            onDismiss = onDismiss
        )

        is ScanState.Failed -> BannerRow(
            text = state.message,
            tint = PlayerColors.Accent,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun BannerRow(text: String, tint: Color, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding, vertical = 10.dp)
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(PlayerColors.Surface)
            .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).background(tint, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = PlayerColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "关闭",
            tint = PlayerColors.TextTertiary,
            modifier = Modifier
                .size(16.dp)
                .clickable { onDismiss() }
        )
    }
}

@Composable
private fun EmptyAlbumState(
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(PlayerColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = PlayerColors.TextTertiary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(text = "还没有专辑", fontSize = 16.sp, color = PlayerColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "把一个存放视频的文件夹添加为专辑，\n之后在这里就能直接续播上次看到的位置。",
            style = PlayerType.Caption,
            color = PlayerColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(PlayerColors.Accent)
                .clickable(interactionSource = interaction, indication = null, onClick = onAddClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "添加专辑", color = Color.White, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<AlbumEntity>,
    modifier: Modifier = Modifier,
    onOpenAlbum: (String) -> Unit,
    onLongPress: (AlbumEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Dimens.pagePadding,
            end = Dimens.pagePadding,
            top = 20.dp,
            bottom = 96.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGapH),
        verticalArrangement = Arrangement.spacedBy(Dimens.gridGapV)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                onClick = { onOpenAlbum(album.id) },
                onLongClick = { onLongPress(album) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: AlbumEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.albumCoverHeight)
                .clip(RoundedCornerShape(Dimens.radiusCard))
                .background(coverBrush(album.name))
        ) {
            val cover = album.coverPath
            if (cover != null) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Text(
                    text = album.name.take(2).uppercase(),
                    fontSize = 21.sp,
                    color = PlayerColors.CoverText,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Text(
                text = "专辑",
                fontSize = 9.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .background(PlayerColors.BadgeScrim, RoundedCornerShape(Dimens.radiusBadge))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = album.name,
            style = PlayerType.CardTitle,
            color = PlayerColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp)
        )
        Text(
            text = pathHint(album),
            style = PlayerType.Caption,
            color = PlayerColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
    }
}

private fun pathHint(album: AlbumEntity): String {
    val raw = album.rootPath ?: album.treeUri ?: return "路径未授权"
    val decoded = Uri.decode(raw)
    return decoded.trimEnd('/').substringAfterLast('/').ifBlank { "已授权目录" }
}

private fun coverBrush(seed: String): Brush {
    val palettes = PlayerColors.CoverPalettes
    val idx = seed.hashCode().absoluteValue % palettes.size
    return Brush.linearGradient(palettes[idx])
}
