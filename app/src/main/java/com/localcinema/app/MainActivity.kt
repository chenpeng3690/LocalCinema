package com.localcinema.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.localcinema.app.data.prefs.AppSettings
import com.localcinema.app.data.prefs.SettingsStore
import com.localcinema.app.ui.nav.AppRoot
import com.localcinema.app.ui.theme.LocalCinemaTheme
import com.localcinema.app.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsStore.settings.collectAsState(initial = AppSettings())
            val dark = ThemeMode.isDark(settings.themeMode, isSystemInDarkTheme())

            // 系统栏图标明暗要跟着主题走，否则浅色主题下白字状态栏会看不见
            LaunchedEffect(dark) {
                val transparent = Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    }
                )
            }

            LocalCinemaTheme(mode = settings.themeMode) {
                AppRoot()
            }
        }
    }
}
