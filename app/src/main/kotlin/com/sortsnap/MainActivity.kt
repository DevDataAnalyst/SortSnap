package com.sortsnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortsnap.data.settings.AppSettings
import com.sortsnap.data.settings.SettingsRepository
import com.sortsnap.ui.SortSnapRoot
import com.sortsnap.ui.theme.SortSnapTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            SortSnapTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                SortSnapRoot(onAccessChanged = { (application as SortSnapApp).onPhotoAccessMaybeChanged() })
            }
        }
    }
}
