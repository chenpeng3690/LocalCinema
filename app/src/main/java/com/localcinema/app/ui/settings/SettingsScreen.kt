package com.localcinema.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.ui.common.PlayerSwitch
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
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
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            SettingValueRow(
                title = "硬件解码",
                value = "自动",
                onClick = { toast(context, "解码设置将在阶段三接入") }
            )
            SettingDivider()
            SettingValueRow(
                title = "音调补偿",
                value = "开启",
                onClick = { toast(context, "音调补偿将在阶段三接入") }
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
                value = "0.1.0",
                onClick = null
            )
            SettingDivider()
            SettingValueRow(
                title = "开源许可",
                value = "ExoPlayer / VLC",
                onClick = { toast(context, "许可说明将在发布前补全") }
            )
        }

        Spacer(Modifier.height(28.dp))
    }
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
