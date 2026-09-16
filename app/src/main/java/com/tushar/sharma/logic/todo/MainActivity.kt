package com.tushar.sharma.logic.todo

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tushar.sharma.logic.todo.notifications.ReminderScheduler
import com.tushar.sharma.logic.todo.ui.screens.AddEditScreen
import com.tushar.sharma.logic.todo.ui.screens.HomeScreen
import com.tushar.sharma.logic.todo.ui.screens.LockScreen
import com.tushar.sharma.logic.todo.ui.screens.SettingsScreen
import com.tushar.sharma.logic.todo.ui.screens.StatsScreen
import com.tushar.sharma.logic.todo.ui.theme.ModernTodoTheme
import com.tushar.sharma.logic.todo.viewmodel.TodoViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TodoViewModel by viewModels()

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.exportData { jsonText ->
                    try {
                        contentResolver.openOutputStream(uri, "wt")?.use {
                            it.bufferedWriter().write(jsonText)
                        }
                        android.widget.Toast.makeText(this, "Backup saved", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val text = contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    }
                    if (text != null) {
                        viewModel.importData(text) { success ->
                            val msg = if (success) "Data restored" else "Invalid backup file"
                            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    android.widget.Toast.makeText(this, "Import failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by viewModel.settings.collectAsState()
            val settingsLoaded by viewModel.settingsLoaded.collectAsState()

            ModernTodoTheme(
                themeMode = settings.themeMode,
                accentHex = settings.accentHex,
                dynamicColor = settings.dynamicColor,
                fontScale = settings.fontScale
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var unlocked by remember { mutableStateOf<Boolean?>(null) }

                    LaunchedEffect(settingsLoaded, settings.pinCode) {
                        if (settingsLoaded && unlocked == null) {
                            unlocked = settings.pinCode.isEmpty()
                        }
                    }

                    when (unlocked) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                        false -> LockScreen(
                            onUnlock = { pin ->
                                val ok = viewModel.verifyPin(pin)
                                if (ok) unlocked = true
                                ok
                            }
                        )
                        else -> AppNavigation(
                            viewModel = viewModel,
                            onExport = { exportLauncher.launch("todo_backup.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: TodoViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAddClick = { catId -> navController.navigate("edit/-1?cat=$catId") },
                onEditClick = { id -> navController.navigate("edit/$id?cat=default") },
                onStatsClick = { navController.navigate("stats") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable(
            route = "edit/{id}?cat={cat}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("cat") { type = NavType.StringType; defaultValue = "default" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: -1L
            val cat = backStackEntry.arguments?.getString("cat") ?: "default"
            AddEditScreen(
                viewModel = viewModel,
                todoId = id,
                initialCategoryId = cat,
                onBack = { navController.popBackStack() }
            )
        }
        composable("stats") {
            StatsScreen(viewModel, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onExport = onExport,
                onImport = onImport
            )
        }
    }
}