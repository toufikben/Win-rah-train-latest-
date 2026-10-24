package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.design.WinRahTheme
import com.example.viewmodel.TrainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TrainViewModel = viewModel()
            LifecycleStartEffect(viewModel) {
                viewModel.setAppVisible(true)
                onStopOrDispose { viewModel.setAppVisible(false) }
            }
            val prefs by (application as WinRahApplication).container.prefs.prefs.collectAsState(initial = null)
            val p = prefs ?: return@setContent // DataStore answers within a frame; avoids a first-run flash.
            val dark = when (p.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            WinRahTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppShell(p, viewModel)
                }
            }
        }
    }
}

