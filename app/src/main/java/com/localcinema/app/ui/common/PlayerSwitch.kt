package com.localcinema.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localcinema.app.ui.theme.PlayerColors

/**
 * 自绘开关：38×22dp，圆角 11dp，开启橙底白点靠右。
 * 不使用 Material 默认 Switch。
 */
@Composable
fun PlayerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(if (checked) PlayerColors.Accent else PlayerColors.SwitchOff)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 18.dp else 2.dp, y = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
