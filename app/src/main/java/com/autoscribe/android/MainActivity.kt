package com.autoscribe.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autoscribe.android.data.Strings
import com.autoscribe.android.engine.WireGuardController
import com.autoscribe.android.ui.screens.BestRoutesScreen
import com.autoscribe.android.ui.screens.ResultsScreen
import com.autoscribe.android.ui.screens.TestScreen
import com.autoscribe.android.ui.screens.TunnelsScreen
import com.autoscribe.android.ui.theme.AutoScribeColors
import com.autoscribe.android.ui.theme.AutoScribeTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDeps.init(this)
        setContent {
            AppRoot(vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val language by vm.language.collectAsState()
    val strings = remember(language) { Strings(language) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var langMenuExpanded by remember { mutableStateOf(false) }

    // Toasts from the ViewModel (profile actions, probe outcomes, run summaries).
    LaunchedEffect(vm) {
        vm.toasts.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Re-check WireGuard install/permission whenever returning to the app
    // (the user may have just installed it or toggled the remote setting).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshWgStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val wgPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshWgStatus() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Best effort: the run notification is suppressed if denied. */ }

    // Ask once for the notification permission (Android 13+) so the
    // foreground-service notification is visible during long runs.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val layoutDirection = if (Strings.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        AutoScribeTheme {
            Scaffold(
                containerColor = AutoScribeColors.Bg,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("AutoScribe", fontSize = 18.sp, color = AutoScribeColors.Text)
                                Text(
                                    strings.t("version_badge"),
                                    fontSize = 11.sp,
                                    color = AutoScribeColors.Muted,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { langMenuExpanded = true }) {
                                Icon(Icons.Filled.Info, contentDescription = strings.t("language"))
                            }
                            DropdownMenu(
                                expanded = langMenuExpanded,
                                onDismissRequest = { langMenuExpanded = false },
                            ) {
                                for ((code, label) in Strings.LANGUAGES) {
                                    DropdownMenuItem(
                                        text = { Text(label + if (code == language) " ✓" else "") },
                                        onClick = {
                                            langMenuExpanded = false
                                            vm.setLanguage(code)
                                        },
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AutoScribeColors.Sidebar,
                            titleContentColor = AutoScribeColors.Text,
                            actionIconContentColor = AutoScribeColors.Text2,
                        ),
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = AutoScribeColors.Sidebar) {
                        val labels = listOf(
                            strings.t("test_tab"),
                            strings.t("tab_results"),
                            strings.t("tab_best"),
                            strings.t("tunnels_tab"),
                        )
                        for ((index, label) in labels.withIndex()) {
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Text(if (tab == index) "●" else "○", fontSize = 10.sp) },
                                label = { Text(label, fontSize = 12.sp) },
                            )
                        }
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    when (tab) {
                        0 -> TestScreen(
                            vm = vm,
                            strings = strings,
                            onRequestWgPermission = {
                                wgPermissionLauncher.launch(WireGuardController.CONTROL_PERMISSION)
                            },
                            onGoToTunnels = { tab = 3 },
                        )
                        1 -> ResultsScreen(vm = vm, strings = strings)
                        2 -> BestRoutesScreen(vm = vm, strings = strings)
                        3 -> TunnelsScreen(
                            vm = vm,
                            strings = strings,
                            onRequestWgPermission = {
                                wgPermissionLauncher.launch(WireGuardController.CONTROL_PERMISSION)
                            },
                        )
                    }
                }
            }
        }
    }
}
