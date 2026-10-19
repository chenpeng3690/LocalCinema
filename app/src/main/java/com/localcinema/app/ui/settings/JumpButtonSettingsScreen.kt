package com.localcinema.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localcinema.app.data.prefs.JumpButtonConfig
import com.localcinema.app.data.prefs.JumpPresets
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.ui.common.PlayerSwitch
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import com.localcinema.app.ui.theme.PlayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class JumpButtonSettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val isLeft: Boolean = savedStateHandle.get<String>("side") != "right"

    val config: StateFlow<JumpButtonConfig> = store.settings
        .map { if (isLeft) it.jumpLeft else it.jumpRight }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            if (isLeft) JumpButtonConfig.DEFAULT_LEFT else JumpButtonConfig.DEFAULT_RIGHT
        )

    fun update(transform: (JumpButtonConfig) -> JumpButtonConfig) {
        val next = transform(config.value).normalized()
        viewModelScope.launch {
            if (isLeft) store.setJumpLeft(next) else store.setJumpRight(next)
        }
    }

    fun reset() {
        val defaults = if (isLeft) JumpButtonConfig.DEFAULT_LEFT else JumpButtonConfig.DEFAULT_RIGHT
        viewModelScope.launch {
            if (isLeft) store.setJumpLeft(defaults) else store.setJumpRight(defaults)
        }
    }
}

@Composable
fun JumpButtonSettingsScreen(
    onBack: () -> Unit,
    viewModel: JumpButtonSettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val title = if (viewModel.isLeft) "快退按钮" else "快进按钮"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 顶栏
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
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        PreviewPanel(config = config, isLeft = viewModel.isLeft)

        Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {
            // 开关组
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(Dimens.radiusCard))
                    .background(PlayerColors.Surface)
            ) {
                SwitchRow(
                    title = "启用该按钮",
                    checked = config.enabled
                ) { value -> viewModel.update { it.copy(enabled = value) } }

                HorizontalDivider(color = PlayerColors.Divider)

                SwitchRow(
                    title = "常驻显示",
                    subtitle = "控件自动隐藏后，此按钮仍保留在画面上",
                    checked = config.constantVisible
                ) { value -> viewModel.update { it.copy(constantVisible = value) } }

                HorizontalDivider(color = PlayerColors.Divider)

                SwitchRow(
                    title = "长按连续触发",
                    subtitle = "按住不放，每 300ms 触发一次",
                    checked = config.longPressRepeat
                ) { value -> viewModel.update { it.copy(longPressRepeat = value) } }
            }

            ConfigSlider(
                label = if (viewModel.isLeft) "每次回退秒数" else "每次快进秒数",
                valueLabel = "${config.seconds} 秒",
                value = config.seconds.toFloat(),
                range = 1f..120f,
                ticks = listOf("1s", "30s", "60s", "120s")
            ) { value ->
                viewModel.update { it.copy(seconds = value.roundToInt()) }
            }

            ConfigSlider(
                label = "按钮大小",
                valueLabel = "${config.sizeDp} dp",
                value = config.sizeDp.toFloat(),
                range = 36f..96f,
                ticks = listOf("36", "96")
            ) { value ->
                viewModel.update { it.copy(sizeDp = value.roundToInt()) }
            }

            ConfigSlider(
                label = "不透明度",
                valueLabel = "${(config.opacity * 100).roundToInt()}%",
                value = config.opacity * 100f,
                range = 10f..100f,
                ticks = listOf("10%", "100%")
            ) { value ->
                viewModel.update { it.copy(opacity = value / 100f) }
            }

            // 位置
            Text(
                text = "位置",
                fontSize = 12.sp,
                color = PlayerColors.TextPrimary,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = "选中格子后也可用下方滑块微调，位置按屏幕比例保存",
                fontSize = 9.5.sp,
                color = PlayerColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                NineGrid(config) { cell ->
                    val col = cell % 3
                    val row = cell / 3
                    viewModel.update {
                        it.copy(
                            xRatio = JumpPresets.xRatios[col],
                            yRatio = JumpPresets.yRatios[row]
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "水平 ${(config.xRatio * 100).roundToInt()}%",
                        fontSize = 10.5.sp,
                        color = PlayerColors.TextSecondary
                    )
                    Slider(
                        value = config.xRatio,
                        onValueChange = { value -> viewModel.update { it.copy(xRatio = value) } },
                        valueRange = 0.05f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = PlayerColors.Accent,
                            inactiveTrackColor = PlayerColors.PlayerTrackInactive
                        ),
                        modifier = Modifier.height(22.dp)
                    )
                    Text(
                        text = "垂直 ${(config.yRatio * 100).roundToInt()}%",
                        fontSize = 10.5.sp,
                        color = PlayerColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(
                        value = config.yRatio,
                        onValueChange = { value -> viewModel.update { it.copy(yRatio = value) } },
                        valueRange = 0.10f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = PlayerColors.Accent,
                            inactiveTrackColor = PlayerColors.PlayerTrackInactive
                        ),
                        modifier = Modifier.height(22.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 28.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerColors.Surface)
                    .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                    .clickable { viewModel.reset() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "恢复默认设置", fontSize = 12.5.sp, color = PlayerColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PreviewPanel(config: JumpButtonConfig, isLeft: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding)
            .height(158.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF2A2F38), Color(0xFF14171D)))
            )
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
    ) {
        val width = maxWidth
        val height = maxHeight
        val size = config.sizeDp.dp
        val x = width * config.xRatio - size / 2
        val y = height * config.yRatio - size / 2

        // 参考线
        Box(
            modifier = Modifier
                .offset(x = width * config.xRatio)
                .width(1.dp)
                .height(height)
                .background(Color(0x73FF6B45))
        )
        Box(
            modifier = Modifier
                .offset(y = height * config.yRatio)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x40FF6B45))
        )

        Text(
            text = if (config.constantVisible) "实时预览 · 常驻已开启" else "实时预览",
            fontSize = 9.sp,
            color = Color(0xFF7C7C88),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        )

        Box(
            modifier = Modifier
                .offset(x = x, y = y)
                .size(size)
                .graphicsLayer(alpha = if (config.enabled) config.opacity else 0.25f)
                .clip(CircleShape)
                .background(PlayerColors.PlayerJumpBg)
                .border(1.dp, Color(0x47FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isLeft) "«" else "»",
                    color = Color.White,
                    fontSize = (config.sizeDp * 0.26f).sp,
                    lineHeight = (config.sizeDp * 0.26f).sp
                )
                Text(
                    text = config.seconds.toString(),
                    color = Color.White,
                    fontSize = (config.sizeDp * 0.22f).sp
                )
            }
        }

        if (!config.enabled) {
            Text(
                text = "按钮已禁用",
                fontSize = 9.5.sp,
                color = PlayerColors.TextTertiary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun NineGrid(config: JumpButtonConfig, onSelect: (Int) -> Unit) {
    val current = JumpPresets.cellOf(config)
    Column(
        modifier = Modifier
            .width(132.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { col ->
                    val cell = row * 3 + col
                    val selected = current == cell
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (selected) PlayerColors.AccentSoft else Color(0xFF22222A)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) PlayerColors.Accent else Color(0xFF303040),
                                shape = RoundedCornerShape(7.dp)
                            )
                            .clickable { onSelect(cell) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 48.dp else 58.dp)
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
private fun ConfigSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    ticks: List<String>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, color = PlayerColors.TextPrimary)
            Text(text = valueLabel, fontSize = 12.5.sp, color = PlayerColors.Accent)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = PlayerColors.Accent,
                inactiveTrackColor = PlayerColors.PlayerTrackInactive
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ticks.forEach { tick ->
                Text(text = tick, fontSize = 9.sp, color = Color(0xFF5E5E6A))
            }
        }
    }
}
