package com.workcheckin.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.workcheckin.app.data.DiaryBackup
import com.workcheckin.app.ui.ProgressScreen
import com.workcheckin.app.ui.WorkLogDataScreen
import com.workcheckin.app.ui.WorkLogDetailScreen
import com.workcheckin.app.ui.WorkLogEditScreen
import com.workcheckin.app.ui.WorkLogScreen
import com.workcheckin.app.ui.ShiftEditScreen
import com.workcheckin.app.ui.ShiftsScreen
import com.workcheckin.app.ui.TodayScreen
import com.workcheckin.app.ui.theme.Peach
import com.workcheckin.app.ui.theme.WorkCheckInTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkCheckInTheme { AppRoot() }
        }
    }

    override fun onStop() {
        super.onStop()
        // สำรองบันทึกงานทุกครั้งที่ออกจากแอป (ถ้าเปิดสำรองอัตโนมัติไว้)
        if (!isChangingConfigurations) {
            val app = application as App
            app.appScope.launch { DiaryBackup.autoBackup(app) }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "วันนี้", Icons.Outlined.CheckCircle),
    Tab("progress", "ประวัติ", Icons.Outlined.BarChart),
    Tab("worklog", "บันทึกงาน", Icons.Outlined.EditNote),
    Tab("shifts", "ตารางเวลา", Icons.Outlined.Schedule),
)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openEditor: (Long) -> Unit = { id -> nav.navigate("edit/$id") }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("today") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Peach),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(onAdd = { openEditor(0) }) }
            composable("progress") { ProgressScreen() }
            composable("shifts") { ShiftsScreen(onEdit = openEditor) }
            composable("worklog") {
                WorkLogScreen(
                    onAdd = { nav.navigate("worklog/edit/0") },
                    onOpen = { id -> nav.navigate("worklog/view/$id") },
                    onData = { nav.navigate("worklog/data") },
                )
            }
            composable(
                "worklog/view/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                WorkLogDetailScreen(
                    id = entry.arguments?.getLong("id") ?: 0,
                    onEdit = { id -> nav.navigate("worklog/edit/$id") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "worklog/edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                WorkLogEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0,
                    onDone = { nav.popBackStack() },
                )
            }
            composable("worklog/data") { WorkLogDataScreen(onBack = { nav.popBackStack() }) }
            composable(
                "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                ShiftEditScreen(
                    shiftId = entry.arguments?.getLong("id") ?: 0,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
