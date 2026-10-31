package com.nishanth.instamapper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nishanth.instamapper.MainViewModel
import com.nishanth.instamapper.Tab
import com.nishanth.instamapper.data.Actions

@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) {
        vm.toast?.let { Actions.toast(ctx, it); vm.toast = null }
    }

    if (vm.showLogin) {
        LoginScreen(onDone = { vm.onLoginClosed() })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == Tab.HOME,
                    onClick = { vm.tab = Tab.HOME },
                    icon = { Icon(Icons.Filled.Search, null) },
                    label = { Text("Analyze") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.SPOTS,
                    onClick = { vm.tab = Tab.SPOTS },
                    icon = { Icon(Icons.Filled.LocationOn, null) },
                    label = { Text("My Spots") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.SETTINGS,
                    onClick = { vm.tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") },
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).imePadding()) {
            when (vm.tab) {
                Tab.HOME -> if (vm.reel != null) ReelScreen(vm) else HomeScreen(vm)
                Tab.SPOTS -> SpotsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
