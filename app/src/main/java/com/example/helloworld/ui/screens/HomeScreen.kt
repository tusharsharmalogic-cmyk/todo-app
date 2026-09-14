package com.example.helloworld.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.helloworld.ui.components.TodoItem
import com.example.helloworld.ui.theme.GradientEnd
import com.example.helloworld.ui.theme.GradientStart
import com.example.helloworld.viewmodel.FilterMode
import com.example.helloworld.viewmodel.TodoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TodoViewModel,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit
) {
    val todos by viewModel.todos.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(
                            Brush.linearGradient(listOf(GradientStart, GradientEnd))
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column {
                        Text(
                            text = "My Tasks",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        val active = todos.count { !it.isDone }
                        Text(
                            text = "$active active • ${todos.size} total",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setSearch,
                            placeholder = { Text("Search tasks...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                focusedBorderColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilterRow(filter = filter, onFilterChange = viewModel::setFilter)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (todos.isEmpty()) {
                EmptyState(
                    message = when {
                        query.isNotBlank() -> "No tasks match \"$query\""
                        filter == FilterMode.Completed -> "No completed tasks yet"
                        filter == FilterMode.Active -> "No active tasks 🎉"
                        else -> "No tasks yet.\nTap + to add your first task."
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(todos, key = { it.id }) { todo ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            TodoItem(
                                todo = todo,
                                onClick = { onEditClick(todo.id) },
                                onToggle = { viewModel.toggleDone(todo.id) },
                                onDelete = { viewModel.deleteTodo(todo.id) }
                            )
                        }
                    }
                    if (filter == FilterMode.Completed && todos.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.clearCompleted() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Clear completed")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun FilterRow(filter: FilterMode, onFilterChange: (FilterMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterMode.values().forEach { mode ->
            val selected = filter == mode
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(mode) },
                label = { Text(mode.name) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "📝",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}