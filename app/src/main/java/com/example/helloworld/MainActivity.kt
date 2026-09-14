package com.example.helloworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.helloworld.ui.screens.AddEditScreen
import com.example.helloworld.ui.screens.HomeScreen
import com.example.helloworld.ui.theme.ModernTodoTheme
import com.example.helloworld.viewmodel.TodoViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TodoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ModernTodoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: TodoViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAddClick = { navController.navigate("edit/-1") },
                onEditClick = { id -> navController.navigate("edit/$id") }
            )
        }
        composable("edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: -1
            AddEditScreen(
                viewModel = viewModel,
                todoId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}