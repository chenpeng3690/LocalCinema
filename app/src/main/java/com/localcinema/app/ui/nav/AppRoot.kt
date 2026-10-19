package com.localcinema.app.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localcinema.app.R
import com.localcinema.app.data.db.AlbumDao
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.ui.album.AlbumDetailScreen
import com.localcinema.app.ui.home.HomeScreen
import com.localcinema.app.ui.recent.RecentScreen
import com.localcinema.app.ui.settings.JumpButtonSettingsScreen
import com.localcinema.app.ui.settings.SettingsScreen
import com.localcinema.app.ui.theme.Dimens
import com.localcinema.app.ui.theme.PlayerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Library("library", R.string.tab_library, Icons.Rounded.VideoLibrary),
    Recent("recent", R.string.tab_recent, Icons.Rounded.History),
    Settings("settings", R.string.tab_settings, Icons.Rounded.Settings)
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = AppTab.entries.map { it.route }.toSet()
    val showBottomBar = currentRoute in tabRoutes

    // 冷启动直达上次专辑（只在第一次进入 Library 时执行一次）
    val startupViewModel: StartupViewModel = hiltViewModel()
    var startupHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startupHandled) return@LaunchedEffect
        startupHandled = true
        val albumId = startupViewModel.resolveLastAlbum()
        if (albumId != null) {
            navController.navigate("album/$albumId")
        }
    }

    val navigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = PlayerColors.Bg,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onTabClick = { tab -> navigateTab(tab.route) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppTab.Library.route) {
                HomeScreen(
                    onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
                    onOpenRecent = { navigateTab(AppTab.Recent.route) }
                )
            }
            composable(AppTab.Recent.route) { RecentScreen() }
            composable(AppTab.Settings.route) {
                SettingsScreen(
                    onOpenJumpSettings = { side -> navController.navigate("jump/$side") }
                )
            }

            composable(
                route = "jump/{side}",
                arguments = listOf(navArgument("side") { type = NavType.StringType })
            ) {
                JumpButtonSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType })
            ) {
                AlbumDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onTabClick: (AppTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlayerColors.BottomBarBg)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 1.dp, color = PlayerColors.BottomBarDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                AppTabItem(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onTabClick(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AppTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) PlayerColors.Accent else PlayerColors.TextTertiary
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Dimens.iconSizeLarge)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(tab.labelRes),
                color = tint,
                fontSize = 9.5.sp
            )
        }
    }
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val albumDao: AlbumDao,
    private val settingsStore: SettingsStore
) : ViewModel() {

    /**
     * 返回可直达的专辑 id：
     * 开关关闭、没有记录、或专辑已被删除时都返回 null，落回首页。
     */
    suspend fun resolveLastAlbum(): String? {
        val settings = settingsStore.settings.first()
        if (!settings.startIntoLastAlbum) return null
        val albumId = settings.lastAlbumId ?: return null
        return if (albumDao.getById(albumId) != null) albumId else null
    }
}
