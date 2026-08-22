package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.TrainBottomNav
import com.example.ui.screens.AnimatedSplashScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.OnboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TrainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TrainViewModel = viewModel()
            val selectedTheme by viewModel.selectedTheme.collectAsState()

            val isDarkTheme = when (selectedTheme) {
                "light" -> false
                "dark", "amoled" -> true
                else -> isSystemInDarkTheme()
            }

            var isSplashVisible by rememberSaveable { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Crossfade(
                    targetState = isSplashVisible,
                    animationSpec = tween(600),
                    label = "splash_crossfade"
                ) { showSplash ->
                    if (showSplash) {
                        AnimatedSplashScreen(
                            onSplashFinished = { isSplashVisible = false }
                        )
                    } else {
                        var currentRoute by rememberSaveable { mutableStateOf("radar") }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                TrainBottomNav(
                                    currentRoute = currentRoute,
                                    onNavigate = { route -> currentRoute = route }
                                )
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                when (currentRoute) {
                                    "radar" -> HomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToMap = { currentRoute = "map" },
                                        onNavigateToOnboard = { currentRoute = "onboard" }
                                    )
                                    "map" -> MapScreen(viewModel = viewModel)
                                    "onboard" -> OnboardScreen(viewModel = viewModel)
                                    "settings" -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

