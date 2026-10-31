package com.nishanth.instamapper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.Tab
import com.nishanth.instamapper.data.Actions

private data class NavItem(val tab: Tab, val label: String, val icon: ImageVector, val color: Color)

private val navItems = listOf(
    NavItem(Tab.HOME, "Explore", Icons.Filled.Search, Color(0xFFE1306C)),
    NavItem(Tab.SPOTS, "My Spots", Icons.Filled.LocationOn, Color(0xFF0083B0)),
    NavItem(Tab.SETTINGS, "Settings", Icons.Filled.Settings, Color(0xFF8E2DE2)),
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) {
        vm.toast?.let { Actions.toast(ctx, it); vm.toast = null }
    }

    if (vm.showOnboarding) {
        OnboardingScreen(
            onFinish = { vm.finishOnboarding() },
            onSignIn = { vm.showLogin = true },
        )
        return
    }

    if (vm.showLogin) {
        LoginScreen(onDone = { vm.onLoginClosed() })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = vm.tab == item.tab,
                        onClick = { vm.tab = item.tab },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.label, fontWeight = if (vm.tab == item.tab) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = item.color,
                            indicatorColor = item.color,
                        ),
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).imePadding()) {
            val screen = when (vm.tab) {
                Tab.HOME -> if (vm.reel != null) "reel" else "home"
                Tab.SPOTS -> "spots"
                Tab.SETTINGS -> "settings"
            }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
                },
                label = "screens",
            ) { s ->
                when (s) {
                    "reel" -> ReelScreen(vm)
                    "home" -> HomeScreen(vm)
                    "spots" -> SpotsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
