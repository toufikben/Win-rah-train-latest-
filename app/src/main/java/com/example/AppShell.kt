package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.prefs.Prefs
import com.example.design.TrainGlyph
import com.example.design.Wr
import com.example.design.WrType
import com.example.feature.board.BoardScreen
import com.example.feature.guide.GuideScreen
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.line.LineScreen
import com.example.feature.line.LineViewModel
import com.example.feature.map.MapScreen
import com.example.feature.map.MapViewModel
import com.example.feature.more.AboutScreen
import com.example.feature.more.AppearanceScreen
import com.example.feature.more.CommuteScreen
import com.example.feature.more.FavoritesScreen
import com.example.feature.more.HowToScreen
import com.example.feature.more.MoreScreen
import com.example.feature.more.MoreViewModel
import com.example.feature.more.SoundsScreen
import com.example.feature.more.SupportScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.station.StationScreen
import com.example.feature.station.StationViewModel
import com.example.ui.screens.DiagnosticsScreen
import com.example.viewmodel.TrainViewModel
import dz.winrah.trainradar.BuildConfig
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "قطاري", TrainGlyph),
    Tab("line", "الخط", Icons.Rounded.Timeline),
    Tab("map", "الخريطة", Icons.Rounded.Map),
    Tab("station", "المحطة", Icons.Rounded.Place),
    Tab("more", "المزيد", Icons.Rounded.Menu),
)

/** The whole app: first-run welcome, then five tabs and their detail screens. */
@Composable
fun AppShell(prefs: Prefs, trainVm: TrainViewModel) {
    val context = LocalContext.current
    val container = (context.applicationContext as WinRahApplication).container
    val scope = rememberCoroutineScope()
    if (!prefs.onboarded) {
        OnboardingScreen(onDone = { scope.launch { container.prefs.setOnboarded() } })
        return
    }

    val nav = rememberNavController()
    val home: HomeViewModel = viewModel()
    val more: MoreViewModel = viewModel()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route?.substringBefore('/') ?: "home"
    val c = Wr.colors

    // Ask once for notifications (alerts) and approximate location (nearest station).
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { nearest(context, home) }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) nearest(context, home) else perms.launch(wanted.toTypedArray())
    }

    val pendingRestore by trainVm.pendingRestore.collectAsState()
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { /* an explicit answer is required */ },
            title = { Text("مشاركة سابقة لم تكتمل") },
            text = { Text("كنت تشارك موقعك من القطار ولم تُنهِ المشاركة. هل ما زلت في القطار وتريد استئنافها؟") },
            confirmButton = { TextButton(onClick = { trainVm.answerPendingRestore(resume = true) }) { Text("استئناف") } },
            dismissButton = { TextButton(onClick = { trainVm.answerPendingRestore(resume = false) }) { Text("إنهاء") } },
        )
    }

    Scaffold(
        containerColor = c.ground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route in TABS.map { it.route }) NavigationBar(containerColor = c.card) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { go(nav, t.route, homeStation(home)) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, style = WrType.tag) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.ink, selectedTextColor = c.ink, indicatorColor = c.signalSoft,
                            unselectedIconColor = c.inkMuted, unselectedTextColor = c.inkMuted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(c.ground).padding(pad)) {
            NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
                composable("home") {
                    Safe {
                        HomeScreen(
                            vm = home,
                            onOpenStation = { nav.navigate("station/$it") },
                            onOpenFavorites = { nav.navigate("favorites") },
                            onOpenGuide = { nav.navigate("guide/$it") },
                            onBoard = { nav.navigate("board") },
                            onOpenNotifications = { nav.navigate("sounds") },
                        )
                    }
                }
                composable("line") { Safe { LineScreen(viewModel<LineViewModel>(), onOpenStation = { nav.navigate("station/$it") }) } }
                composable("map") { MapScreen(viewModel<MapViewModel>(), onOpenStation = { nav.navigate("station/$it") }) }
                composable("station/{id}") { e ->
                    val id = e.arguments?.getString("id") ?: return@composable
                    Safe { StationScreen(viewModel<StationViewModel>(), id, onOpenGuide = { nav.navigate("guide/$it") }) }
                }
                composable("guide/{id}") { e -> Safe { GuideScreen(container.network.station(e.arguments?.getString("id"))) } }
                composable("board") { Safe { BoardScreen(trainVm) } }
                composable("more") { Safe { MoreScreen(more, open = { nav.navigate(it) }) } }
                composable("favorites") { Safe { FavoritesScreen(more, onOpenSupport = { nav.navigate("support") }) } }
                composable("commute") { Safe { CommuteScreen(more) } }
                composable("sounds") { Safe { SoundsScreen(more) } }
                composable("appearance") { Safe { AppearanceScreen(more) } }
                composable("support") { Safe { SupportScreen(more) } }
                composable("howto") { Safe { HowToScreen() } }
                composable("about") { Safe { AboutScreen() } }
                composable("diagnostics") {
                    if (BuildConfig.DIAGNOSTICS_ENABLED) Safe { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
                    else Safe { AboutScreen() }
                }
            }
        }
    }
}

/** Screens draw edge-to-edge; this keeps their content clear of the status bar. */
@Composable
private fun Safe(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
}

private fun homeStation(home: HomeViewModel): String? = home.state.value.station?.id

private fun go(nav: NavHostController, tab: String, station: String?) {
    val dest = if (tab == "station") station?.let { "station/$it" } ?: "favorites" else tab
    nav.navigate(dest) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@SuppressLint("MissingPermission")
private fun nearest(context: android.content.Context, home: HomeViewModel) {
    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!ok) return
    val lm = context.getSystemService(LocationManager::class.java)
    val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return
    home.setNearest(loc.latitude, loc.longitude)
}
