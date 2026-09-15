package com.tushar.sharma.logic.todo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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

    private val storagePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val allFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestStoragePermission()

        setContent {
            val settings by viewModel.settings.collectAsState()
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
                    var unlocked by remember {
                        mutableStateOf(settings.pinCode.isEmpty())
                    }
                    // Re-lock check when PIN changes
                    val pinEmpty = settings.pinCode.isEmpty()
                    if (pinEmpty && !unlocked) unlocked = true

                    if (!unlocked) {
                        LockScreen(
                            onUnlock = { pin ->
                                val ok = viewModel.verifyPin(pin)
                                if (ok) unlocked = true
                                ok
                            },
                            onForgotPin = {
                                viewModel.clearPin()
                                unlocked = true
                            }
                        )
                    } else {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    allFilesLauncher.launch(intent)
                } catch (_: Exception) {
                    try {
                        allFilesLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    } catch (_: Exception) {}
                }
            }
        } else {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (needed.isNotEmpty()) storagePermLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun AppNavigation(viewModel: TodoViewModel) {
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
            SettingsScreen(viewModel, onBack = { navController.popBackStack() })
        }
    }
}