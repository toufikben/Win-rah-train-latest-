package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.example.design.WinRahTheme
import com.example.feature.more.AboutScreen
import com.example.feature.more.HowToScreen
import com.example.feature.onboarding.OnboardingScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screens that need no network, rendered in both themes for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent {
            WinRahTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test fun onboarding() = shoot("onboarding") { OnboardingScreen(onDone = {}) }
    @Test fun about() = shoot("about") { AboutScreen() }
    @Test fun howToDark() = shoot("howto_dark", dark = true) { HowToScreen() }
}
