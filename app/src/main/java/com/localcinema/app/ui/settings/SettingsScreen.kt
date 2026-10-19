package com.localcinema.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localcinema.app.data.prefs.AppSettings
import com.localcinema.app.data.prefs.PlaylistMode
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.ui.common.PlayerSwitch
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
import com.localcinema.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setStartIntoLastAlbum(value: Boolean) {
        viewModelScope.launch { store.setStartIntoLastAlbum(value) }
    }

    fun setAutoResumeLastVideo(value: Boolean) {
        viewModelScope.launch { store.setAutoResumeLastVideo(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { store.setKeepScreenOn(value) }
    }

    fun setBackgroundPlay(value: Boolean) {
        viewModelScope.launch { store.setBackgroundPlay(value) }
    }

    fun setNextCountdown(value: Boolean) {
        viewModelScope.launch { store.setNextCountdown(value) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    fun setPlaylistMode(mode: String) {
        viewModelScope.launch { store.setPlaylistMode(mode) }
    }

    fun setLongPressSpeed(percent: Int) {
        viewModelScope.launch { store.setLongPressSpeedPercent(percent) }
    }

    fun setSubtitleFontSize(size: Float) {
        viewModelScope.launch { store.setSubtitleFontSize(size) }
    }

    fun setSubtitleColor(argb: Long) {
        viewModelScope.launch { store.setSubtitleColor(argb) }
    }

    fun resetSubtitleDelay() {
        viewModelScope.launch { store.setSubtitleDelay(0L) }
    }

    /** 直接在列表页开关悬浮按钮，不用进二级页 */
    fun setJumpEnabled(isLeft: Boolean, enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            if (isLeft) {
                store.setJumpLeft(current.jumpLeft.copy(enabled = enabled))
            } else {
                store.setJumpRight(current.jumpRight.copy(enabled = enabled))
            }
        }
    }
}

/** 通用单选弹窗 */
private data class Choice<T>(val label: String, val value: T)

@Composable
fun SettingsScreen(
    onOpenJumpSettings: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var modeDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var fontSizeDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {
            Text(
                text = "设置",
                style = PlayerType.PageTitle,
                color = PlayerColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "全部设置即时生效 · 数据仅保存在本机",
                style = PlayerType.Caption,
                color = PlayerColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        SettingGroup(title = "播放") {
            SettingSwitchRow(
                title = "保持屏幕常亮",
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn
            )
            SettingDivider()
            SettingSwitchRow(
                title = "后台播放",
                subtitle = "退到后台或锁屏后继续播放",
                checked = settings.backgroundPlay,
                onCheckedChange = viewModel::setBackgroundPlay
            )
            SettingDivider()
            SettingValueRow(
                title = "播放模式",
                value = PlaylistMode.label(settings.playlistMode),
                onClick = { modeDialog = true }
            )
            SettingDivider()
            SettingSwitchRow(
                title = "播完显示下一集倒计时",
                subtitle = "关闭后播完直接跳下一集",
                checked = settings.nextCountdown,
                onCheckedChange = viewModel::setNextCountdown
            )
            SettingDivider()
            SettingValueRow(
                title = "长按画面加速",
                value = if (settings.longPressSpeedPercent <= 0) {
                    "已关闭"
                } else {
                    String.format("%.1fx", settings.longPressSpeedPercent / 100f)
                },
                onClick = { speedDialog = true }
            )
            SettingDivider()
            SettingValueRow(
                title = "硬件解码",
                value = "自动",
                onClick = { toast(context, "当前跟随系统选择，遇到花屏可先试软解") }
            )
        }

        SettingGroup(title = "播放控制") {
            SettingSwitchNavRow(
                title = "快退按钮",
                subtitle = with(settings.jumpLeft) {
                    "${seconds}s · ${if (constantVisible) "常驻" else "非常驻"}"
                },
                checked = settings.jumpLeft.enabled,
                onCheckedChange = { viewModel.setJumpEnabled(isLeft = true, enabled = it) },
                onOpenDetail = { onOpenJumpSettings("left") }
            )
            SettingDivider()
            SettingSwitchNavRow(
                title = "快进按钮",
                subtitle = with(settings.jumpRight) {
                    "${seconds}s · ${if (constantVisible) "常驻" else "非常驻"}"
                },
                checked = settings.jumpRight.enabled,
                onCheckedChange = { viewModel.setJumpEnabled(isLeft = false, enabled = it) },
                onOpenDetail = { onOpenJumpSettings("right") }
            )
        }

        SettingGroup(title = "外观") {
            SettingValueRow(
                title = "主题",
                value = ThemeMode.label(settings.themeMode),
                onClick = { themeDialog = true }
            )
        }

        SettingGroup(title = "字幕") {
            SettingValueRow(
                title = "默认字号",
                value = "${settings.subtitleFontSize.toInt()}sp",
                onClick = { fontSizeDialog = true }
            )
            SettingDivider()
            SettingValueRow(
                title = "默认颜色",
                value = subtitleColorLabel(settings.subtitleColorArgb),
                onClick = { colorDialog = true }
            )
            SettingDivider()
            SettingValueRow(
                title = "字幕延迟",
                value = "归零",
                onClick = {
                    viewModel.resetSubtitleDelay()
                    toast(context, "字幕延迟已归零")
                }
            )
        }

        SettingGroup(title = "启动与恢复") {
            SettingSwitchRow(
                title = "启动直达上次专辑",
                subtitle = "冷启动后直接进入上次浏览的专辑",
                checked = settings.startIntoLastAlbum,
                onCheckedChange = viewModel::setStartIntoLastAlbum
            )
            SettingDivider()
            SettingSwitchRow(
                title = "自动续播上次视频",
                subtitle = "进入专辑后自动打开上次播放的视频",
                checked = settings.autoResumeLastVideo,
                onCheckedChange = viewModel::setAutoResumeLastVideo
            )
        }

        SettingGroup(title = "关于") {
            SettingValueRow(
                title = "版本",
                value = "0.2.0",
                onClick = null
            )
            SettingDivider()
            SettingValueRow(
                title = "开源许可",
                value = "ExoPlayer / Coil",
                onClick = {
                    toast(context, "ExoPlayer 为 Apache-2.0，Coil 为 Apache-2.0")
                }
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    if (modeDialog) {
        ChoiceDialog(
            title = "播放模式",
            choices = PlaylistMode.ALL.map { Choice(PlaylistMode.label(it), it) },
            selected = settings.playlistMode,
            onDismiss = { modeDialog = false }
        ) { mode ->
            viewModel.setPlaylistMode(mode)
            modeDialog = false
        }
    }

    if (speedDialog) {
        ChoiceDialog(
            title = "长按画面加速",
            choices = listOf(
                Choice("关闭", 0),
                Choice("1.5x", 150),
                Choice("2.0x", 200),
                Choice("3.0x", 300)
            ),
            selected = settings.longPressSpeedPercent,
            onDismiss = { speedDialog = false }
        ) { percent ->
            viewModel.setLongPressSpeed(percent)
            speedDialog = false
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = "主题",
            choices = ThemeMode.ALL.map { Choice(ThemeMode.label(it), it) },
            selected = settings.themeMode,
            onDismiss = { themeDialog = false }
        ) { mode ->
            viewModel.setThemeMode(mode)
            themeDialog = false
        }
    }

    if (fontSizeDialog) {
        ChoiceDialog(
            title = "字幕默认字号",
            choices = listOf(12f, 14f, 16f, 18f, 20f, 24f).map { Choice("${it.toInt()}sp", it) },
            selected = settings.subtitleFontSize,
            onDismiss = { fontSizeDialog = false }
        ) { size ->
            viewModel.setSubtitleFontSize(size)
            fontSizeDialog = false
        }
    }

    if (colorDialog) {
        ChoiceDialog(
            title = "字幕默认颜色",
            choices = SUBTITLE_COLOR_CHOICES.map { (label, argb) -> Choice(label, argb) },
            selected = settings.subtitleColorArgb,
            onDismiss = { colorDialog = false }
        ) { argb ->
            viewModel.setSubtitleColor(argb)
            colorDialog = false
        }
    }
}

private val SUBTITLE_COLOR_CHOICES = listOf(
    "白色" to 0xFFFFFFFF,
    "黄色" to 0xFFFFE082,
    "青色" to 0xFF80DEEA,
    "绿色" to 0xFFA5D6A7
)

private fun subtitleColorLabel(argb: Long): String =
    SUBTITLE_COLOR_CHOICES.firstOrNull { it.second == argb }?.first ?: "自定义"

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlayerColors.SurfaceElevated,
        titleContentColor = PlayerColors.TextPrimary,
        textContentColor = PlayerColors.TextSecondary,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onPick(choice.value) }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = choice.label,
                            fontSize = 13.sp,
                            color = if (choice.value == selected) {
                                PlayerColors.Accent
                            } else {
                                PlayerColors.TextPrimary
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (choice.value == selected) {
                            Text(text = "✓", fontSize = 13.sp, color = PlayerColors.Accent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = PlayerColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun SettingGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            color = PlayerColors.GroupLabel,
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 7.dp)
        )
        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.pagePadding)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusCard))
                .background(PlayerColors.Surface)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 48.dp else 56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, color = PlayerColors.TextPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 9.5.sp,
                    color = PlayerColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        PlayerSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingValueRow(
    title: String,
    value: String,
    onClick: (() -> Unit)?
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = PlayerColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, fontSize = 11.5.sp, color = PlayerColors.TextSecondary)
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text(text = "›", fontSize = 14.sp, color = PlayerColors.Chevron)
        }
    }
}

@Composable
private fun SettingSwitchNavRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenDetail: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onOpenDetail
                )
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 12.sp, color = PlayerColors.TextPrimary)
                Text(
                    text = if (checked) subtitle else "已关闭",
                    fontSize = 9.5.sp,
                    color = PlayerColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(text = "›", fontSize = 14.sp, color = PlayerColors.Chevron)
        }
        Spacer(Modifier.width(10.dp))
        PlayerSwitch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(14.dp))
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        thickness = 1.dp,
        color = PlayerColors.Divider
    )
}

private fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
