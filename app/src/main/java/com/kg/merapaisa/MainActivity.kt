package com.kg.merapaisa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kg.merapaisa.ui.MainScreen
import com.kg.merapaisa.ui.MainViewModel
import com.kg.merapaisa.ui.theme.MeraPaisaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash window covers startup on its own; no artificial delay on top.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = this
            val themeName by ThemeStore.getTheme(context).collectAsState(initial = "Midnight")
            val currentTheme = getThemeByName(themeName)

            MeraPaisaTheme(theme = currentTheme) {
                CompositionLocalProvider(LocalAppTheme provides currentTheme) {
                    val viewModel: MainViewModel = viewModel()
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
