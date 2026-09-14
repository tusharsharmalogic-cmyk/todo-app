package com.example.helloworld.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.helloworld.data.Category
import com.example.helloworld.ui.components.TodoItem
import com.example.helloworld.ui.theme.GradientEnd
import com.example.helloworld.ui.theme.GradientStart
import com.example.helloworld.ui.theme.parseHexColor
import com.example.helloworld.viewmodel.FilterMode
import com.example.helloworld.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TodoViewModel,
    onAddClick: (String) -> Unit,
    onEditClick: (Long) -> Unit,
    onStatsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val todos by viewModel.todos.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val catFilter by viewModel.categoryFilter.collectAsState()
    val lastDeleted by viewModel.lastDeleted.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastDeleted) {
        val item = lastDeleted ?: return@LaunchedEffect
        val res = snackbarHostState.showSnackbar(
            message = "Deleted \"${item.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (res == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        } else {
            viewModel.clearUndo()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Tasks",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(2.dp))
                                val active = todos.count { !it.isDone }
                                val overdue = todos.count { it.isOverdue }
                                Text(
                                    text = "$active active • ${todos.size} total" +
                                        if (overdue > 0) " • $overdue overdue" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            IconButton(onClick = onStatsClick) {
                                Icon(
                                    Icons.Rounded.Assessment,
                                    contentDescription = "Stats",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setSearch,
                            placeholder = { Text("Search tasks...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FilterRow(filter = filter, onFilterChange = viewModel::setFilter)
                Spacer(Modifier.height(6.dp))
                CategoryRow(
                    selected = catFilter,
                    onSelect = viewModel::setCategoryFilter
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddClick(catFilter ?: Category.DEFAULT_ID) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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
                        filter == FilterMode.Active -> "No active tasks"
                        else -> "No tasks yet.\nTap + to add your first task."
                    }
                )
            } else {
                ReorderableTodoList(
                    todos = todos,
                    filter = filter,
                    onEditClick = onEditClick,
                    onToggle = viewModel::toggleDone,
                    onDelete = viewModel::deleteTodo,
                    onMove = viewModel::moveTodo,
                    onClearCompleted = viewModel::clearCompleted
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableTodoList(
    todos: List<com.example.helloworld.data.Todo>,
    filter: FilterMode,
    onEditClick: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClearCompleted: () -> Unit
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(todos, key = { _, t -> t.id }) { index, todo ->
            val isDragging = index == draggingIndex
            val translation = if (isDragging) dragOffset else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = translation }
                    .zIndex(if (isDragging) 1f else 0f)
                    .pointerInput(todo.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                if (itemHeight <= 0f) itemHeight = size.height.toFloat() + 24f
                                if (dragOffset > itemHeight / 2) {
                                    val target = index + 1
                                    if (target < todos.size) {
                                        onMove(index, target)
                                        draggingIndex = target
                                        dragOffset -= itemHeight
                                    }
                                } else if (dragOffset < -itemHeight / 2) {
                                    val target = index - 1
                                    if (target >= 0) {
                                        onMove(index, target)
                                        draggingIndex = target
                                        dragOffset += itemHeight
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            }
                        )
                    }
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TodoItem(
                        todo = todo,
                        onClick = { onEditClick(todo.id) },
                        onToggle = { onToggle(todo.id) },
                        onDelete = { onDelete(todo.id) }
                    )
                }
            }
        }

        if (filter == FilterMode.Completed && todos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onClearCompleted,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear completed")
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRow(filter: FilterMode, onFilterChange: (FilterMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterMode.values().forEach { mode ->
            FilterChip(
                selected = filter == mode,
                onClick = { onFilterChange(mode) },
                label = { Text(mode.name) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRow(selected: String?, onSelect: (String?) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                shape = RoundedCornerShape(12.dp)
            )
        }
        items(Category.PRESETS.size) { idx ->
            val cat = Category.PRESETS[idx]
            val isSel = selected == cat.id
            FilterChip(
                selected = isSel,
                onClick = { onSelect(if (isSel) null else cat.id) },
                label = { Text(cat.name) },
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(parseHexColor(cat.colorHex))
                    )
                }
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