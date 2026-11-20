package com.launcher.androidmediabar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.launcher.androidmediabar.ui.theme.AndroidMediaBarTheme
import com.launcher.androidmediabar.ui.xmb.XmbLauncherScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidMediaBarTheme(isInDarkTheme = true) {
                XmbLauncherScreen()
            }
        }
    }
}
