/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.soniclab.app.di.AppContainer
import com.soniclab.app.ui.common.MiniPlayerBar
import com.soniclab.app.ui.screens.AboutScreen
import com.soniclab.app.ui.screens.EqualizerScreen
import com.soniclab.app.ui.screens.LibraryScreen
import com.soniclab.app.ui.screens.PlayerScreen
import com.soniclab.app.ui.screens.SettingsScreen
import com.soniclab.app.ui.screens.StudioScreen
import com.soniclab.core.permission.AudioPermissions
import com.soniclab.player.AudioEnhanceBridge
import com.soniclab.player.AudioHeadroomBridge
import com.soniclab.player.DitherBridge
import android.app.Activity

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    NavItem("home", "Perpustakaan", Icons.Rounded.LibraryMusic),
    NavItem("settings", "Pengaturan", Icons.Rounded.Settings)
)

@Composable
fun SonicLabAppRoot(container: AppContainer) {
    val navController = rememberNavController()

    val context = LocalContext.current
    val activity = context as? Activity
    val latestContainer by rememberUpdatedState(container)

    // Refresh the library whenever audio access is (or becomes) available.
    val refreshIfGranted: () -> Unit = {
        if (AudioPermissions.hasAudioAccess(context)) {
            latestContainer.libraryRepository.refresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshIfGranted() }

    // Request permissions once on first composition.
    LaunchedEffect(Unit) {
        if (activity != null) {
            val needed = buildList {
                if (!AudioPermissions.hasAudioAccess(context)) addAll(AudioPermissions.audioPermissions())
                if (!AudioPermissions.hasNotificationAccess(context)) add(AudioPermissions.notificationPermission())
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            } else {
                refreshIfGranted()
            }
            // Notification is non-critical; request audio separately so the
            // library refresh only depends on the audio result.
        }
    }

    // Re-check on resume (e.g. returning from system settings "Allow" screen),
    // and drop any stale "permission not granted" error once access exists.
    // Keep the player's crossfade setting in sync with preferences.
    val crossfadeSeconds by container.settingsRepository.crossfadeSeconds
        .collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(crossfadeSeconds) {
        container.playerController.setCrossfadeMs(crossfadeSeconds * 1000L)
    }

    // Apply the on-device AI enhancer to the real-time playback path.
    val aiEnhanceEnabled by container.settingsRepository.aiEnhanceEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(aiEnhanceEnabled) {
        AudioEnhanceBridge.enabled = aiEnhanceEnabled
    }

    // Keep the ReplayGain-style auto normalization in sync with preferences.
    val autoNormalizeEnabled by container.settingsRepository.autoNormalizeEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(autoNormalizeEnabled) {
        container.playerController.setAutoNormalize(autoNormalizeEnabled)
    }

    // Direct output: rebuild the service's sink without the DSP chain.
    val directOutputEnabled by container.settingsRepository.directOutputEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(directOutputEnabled) {
        container.playerController.setDirectOutput(directOutputEnabled)
    }

    // Output Audio: hi-res float output (rebuilds the player), dither & headroom live.
    val hiResOutputEnabled by container.settingsRepository.hiResOutputEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(hiResOutputEnabled) {
        container.playerController.setHiResOutput(hiResOutputEnabled)
    }

    val ditherEnabled by container.settingsRepository.ditherEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(ditherEnabled) {
        container.playerController.setDitherEnabled(ditherEnabled)
    }

    val headroomDb by container.settingsRepository.headroomDb
        .collectAsStateWithLifecycle(initialValue = 0f)
    LaunchedEffect(headroomDb) {
        container.playerController.setHeadroomDb(headroomDb)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshIfGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            Column {
                if (currentRoute != "player") {
                    MiniPlayerBar(container) { navController.navigate("player") }
                }
                BottomBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 }
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 24 }
            }
        ) {
            composable("home") {
                LibraryScreen(
                    container,
                    onOpenPlayer = { navController.navigate("player") },
                    onOpenStudio = { track ->
                        navController.navigate(
                            "studio?uri=${Uri.encode(track.uri.toString())}&title=${Uri.encode(track.title)}"
                        )
                    }
                )
            }
            composable("player") {
                PlayerScreen(
                    container,
                    onOpenEqualizer = { navController.navigate("equalizer") }
                )
            }
            composable(
                "studio?uri={uri}&title={title}",
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                StudioScreen(
                    container,
                    uri = entry.arguments?.getString("uri")?.takeIf { it.isNotBlank() },
                    title = entry.arguments?.getString("title")?.takeIf { it.isNotBlank() }
                )
            }
            composable("equalizer") {
                EqualizerScreen(container)
            }
            composable("settings") {
                SettingsScreen(container, onOpenAbout = { navController.navigate("about") })
            }
            composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationBar {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
