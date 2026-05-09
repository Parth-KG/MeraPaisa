package com.merapaisa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.merapaisa.ui.theme.MeraPaisaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val widgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val widgetComponent = android.content.ComponentName(this, com.merapaisa.widget.DebtWidgetReceiver::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isNotEmpty()) {
            val intent = android.content.Intent(this, com.merapaisa.widget.DebtWidgetReceiver::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            sendBroadcast(intent)
        }

        setContent {
            val context = this
            val themeName by ThemeStore.getTheme(context).collectAsState(initial = "Midnight")
            val currentTheme = getThemeByName(themeName)
            var showSplash by remember { mutableStateOf(true) }

            MeraPaisaTheme {
                CompositionLocalProvider(LocalAppTheme provides currentTheme) {
                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    } else {
                        val viewModel: MainViewModel = viewModel()
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}