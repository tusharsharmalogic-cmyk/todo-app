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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    val categories by viewModel.categories.collectAsState()

    var searchOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

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
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Tasks",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val active = todos.count { !it.isDone }
                                val overdue = todos.count { it.isOverdue }
                                Text(
                                    text = "$active active • ${todos.size} total" +
                                        if (overdue > 0) " • $overdue overdue" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            IconButton(
                                onClick = { searchOpen = !searchOpen },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onStatsClick,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Assessment,
                                    contentDescription = "Stats",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White
                                )
                            }
                        }
                        AnimatedVisibility(visible = searchOpen) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(21.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.weight(1f)) {
                                            if (query.isEmpty()) {
                                                Text(
                                                    "Search tasks...",
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            BasicTextField(
                                                value = query,
                                                onValueChange = viewModel::setSearch,
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Color.White
                                                ),
                                                cursorBrush = SolidColor(Color.White),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilterRow(filter = filter, onFilterChange = viewModel::setFilter)
                Spacer(Modifier.height(4.dp))
                CategoryRow(
                    categories = categories,
                    selected = catFilter,
                    onSelect = viewModel::setCategoryFilter
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddClick(catFilter ?: Category.DEFAULT_ID) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp)
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
                    categories = categories,
                    filter = filter,
                    onEditClick = onEditClick,
                    onToggle = viewModel::toggleDone,
                    onDelete = viewModel::deleteTodo,
                    onMove = viewModel::moveTodo,
                    onSaveOrder = viewModel::saveOrder,
                    onClearCompleted = viewModel::clearCompleted
                )
            }
        }
    }
}

@Composable
fun ReorderableTodoList(
    todos: List<com.example.helloworld.data.Todo>,
    categories: List<Category>,
    filter: FilterMode,
    onEditClick: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSaveOrder: () -> Unit,
    onClearCompleted: () -> Unit
) {
    val listState = rememberLazyListState()

    // Fix: mutableLongStateOf use karo Long ke liye (performance better)
    var draggingId by remember { mutableLongStateOf(-1L) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val spacingPx = with(density) { 10.dp.toPx() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(todos, key = { _, t -> t.id }) { index, todo ->
            val isDragging = draggingId == todo.id

            // Fix: AnimatedVisibility HATA DIYA - yahi reorder jitter ka main reason tha
            // Har reorder pe enter/exit animation trigger ho raha tha
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        // Fix: dragging item ko thoda scale up karo - better visual feedback
                        scaleX = if (isDragging) 1.03f else 1f
                        scaleY = if (isDragging) 1.03f else 1f
                        shadowElevation = if (isDragging) 8f else 0f
                    }
                    .pointerInput(todo.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = todo.id
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y

                                val info = listState.layoutInfo
                                val myInfo = info.visibleItemsInfo
                                    .firstOrNull { it.key == todo.id }
                                    ?: return@detectDragGesturesAfterLongPress

                                val currentIdx = todos.indexOfFirst { it.id == todo.id }
                                if (currentIdx < 0) return@detectDragGesturesAfterLongPress

                                val goingDown = dragOffset > 0f
                                val neighborIndex = currentIdx + if (goingDown) 1 else -1
                                if (neighborIndex !in todos.indices)
                                    return@detectDragGesturesAfterLongPress

                                val neighborId = todos[neighborIndex].id
                                val neighborInfo = info.visibleItemsInfo
                                    .firstOrNull { it.key == neighborId }
                                    ?: return@detectDragGesturesAfterLongPress

                                val myCenter = myInfo.offset + myInfo.size / 2f + dragOffset
                                val neighborCenter = neighborInfo.offset + neighborInfo.size / 2f

                                // Fix: threshold add kiya - items tabhi swap honge jab
                                // center clearly cross ho jaye (jitter reduce hoga)
                                val threshold = neighborInfo.size * 0.3f

                                if (goingDown && myCenter > neighborCenter + threshold) {
                                    onMove(currentIdx, neighborIndex)
                                    dragOffset -= (myInfo.size + spacingPx)
                                } else if (!goingDown && myCenter < neighborCenter - threshold) {
                                    onMove(currentIdx, neighborIndex)
                                    dragOffset += (myInfo.size + spacingPx)
                                }
                            },
                            onDragEnd = {
                                draggingId = -1L
                                dragOffset = 0f
                                onSaveOrder()
                            },
                            onDragCancel = {
                                draggingId = -1L
                                dragOffset = 0f
                                onSaveOrder()
                            }
                        )
                    }
            ) {
                TodoItem(
                    todo = todo,
                    categories = categories,
                    onClick = { onEditClick(todo.id) },
                    onToggle = { onToggle(todo.id) },
                    onDelete = { onDelete(todo.id) }
                )
            }
        }

        if (filter == FilterMode.Completed && todos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onClearCompleted,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
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
fun CategoryRow(
    categories: List<Category>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
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
        items(categories, key = { it.id }) { cat ->
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