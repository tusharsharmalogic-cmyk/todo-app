package com.tushar.sharma.logic.todo.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tushar.sharma.logic.todo.data.Category
import com.tushar.sharma.logic.todo.data.Todo
import com.tushar.sharma.logic.todo.ui.components.TodoItem
import com.tushar.sharma.logic.todo.ui.theme.GradientEnd
import com.tushar.sharma.logic.todo.ui.theme.GradientStart
import com.tushar.sharma.logic.todo.ui.theme.parseHexColor
import com.tushar.sharma.logic.todo.viewmodel.FilterMode
import com.tushar.sharma.logic.todo.viewmodel.TodoViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    val dateFilterMs by viewModel.dateFilterMs.collectAsState()

    var searchOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var pendingScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(todos) {
        pendingScroll?.let { (idx, off) -> listState.scrollToItem(idx, off); pendingScroll = null }
    }

    LaunchedEffect(lastDeleted) {
        val item = lastDeleted ?: return@LaunchedEffect
        val res = snackbarHostState.showSnackbar("Deleted \"${item.title}\"", "Undo", duration = SnackbarDuration.Short)
        if (res == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("My Tasks", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = Color.White)
                                val active = viewModel.activeCount()
                                val overdue = viewModel.overdueCount()
                                Text(
                                    "$active active${if (overdue > 0) " • $overdue overdue" else ""}",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            IconButton(onClick = { searchOpen = !searchOpen }, Modifier.size(38.dp)) {
                                Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, null, tint = Color.White)
                            }
                            IconButton(onClick = onStatsClick, Modifier.size(38.dp)) {
                                Icon(Icons.Rounded.Assessment, null, tint = Color.White)
                            }
                            IconButton(onClick = onSettingsClick, Modifier.size(38.dp)) {
                                Icon(Icons.Rounded.Settings, null, tint = Color.White)
                            }
                        }
                        AnimatedVisibility(visible = searchOpen) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp))
                                    .background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.weight(1f)) {
                                            if (query.isEmpty()) Text("Search tasks...", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                                            BasicTextField(value = query, onValueChange = viewModel::setSearch, singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                                cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilterRow(filter = filter, dateFilterMs = dateFilterMs,
                    onFilterChange = { mode ->
                        if (mode == FilterMode.Date) {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                cal.set(y, m, d)
                                viewModel.setDateFilter(cal.timeInMillis)
                                viewModel.setFilter(FilterMode.Date)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        } else {
                            viewModel.setFilter(mode)
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                CategoryRow(categories = categories, selected = catFilter, onSelect = viewModel::setCategoryFilter)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddClick(catFilter ?: Category.DEFAULT_ID) },
                containerColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (todos.isEmpty()) {
                EmptyState(message = when {
                    query.isNotBlank() -> "No tasks match \"$query\""
                    filter == FilterMode.Completed -> "No completed tasks yet"
                    filter == FilterMode.Active -> "No active tasks"
                    filter == FilterMode.Today -> "No tasks due today"
                    filter == FilterMode.Overdue -> "No overdue tasks"
                    filter == FilterMode.NoDeadline -> "No tasks without deadline"
                    filter == FilterMode.Date -> "No tasks on selected date"
                    else -> "No tasks yet.\nTap + to add your first task."
                })
            } else {
                TodoList(
                    todos = todos, categories = categories, filter = filter, listState = listState,
                    onEditClick = onEditClick,
                    onToggle = { id ->
                        pendingScroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        viewModel.toggleDone(id)
                    },
                    onDelete = viewModel::deleteTodo,
                    onMove = { fromId, toId -> viewModel.swapOrder(fromId, toId) },
                    onClearCompleted = viewModel::clearCompleted
                )
            }
        }
    }
}

@Composable
fun TodoList(
    todos: List<Todo>, categories: List<Category>, filter: FilterMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEditClick: (Long) -> Unit, onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit, onMove: (Long, Long) -> Unit, onClearCompleted: () -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(todos, key = { _, t -> t.id }) { index, todo ->
            val isOverdueFilter = filter == FilterMode.Overdue
            val isDateFilter = filter == FilterMode.Date
            TodoItem(
                todo = todo, categories = categories,
                onClick = if (!isOverdueFilter) { { onEditClick(todo.id) } } else null,
                onToggle = if (!isOverdueFilter) { { onToggle(todo.id) } } else null,
                onDelete = if (!isOverdueFilter) { { onDelete(todo.id) } } else null,
                onMoveUp = if (!todo.isDone && !isOverdueFilter && index > 0 && !todos[index - 1].isDone) {
                    { onMove(todo.id, todos[index - 1].id) }
                } else null,
                onMoveDown = if (!todo.isDone && !isOverdueFilter && index < todos.size - 1 && !todos[index + 1].isDone) {
                    { onMove(todo.id, todos[index + 1].id) }
                } else null,
                readOnly = isOverdueFilter
            )
        }
        if (filter == FilterMode.Completed && todos.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onClearCompleted, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear completed")
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRow(filter: FilterMode, dateFilterMs: Long?, onFilterChange: (FilterMode) -> Unit) {
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FilterMode.values()) { mode ->
            val label = if (mode == FilterMode.Date && dateFilterMs != null && filter == FilterMode.Date)
                dateFmt.format(Date(dateFilterMs)) else mode.label
            FilterChip(selected = filter == mode, onClick = { onFilterChange(mode) },
                label = { Text(label) }, shape = RoundedCornerShape(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRow(categories: List<Category>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) },
                label = { Text("All") }, shape = RoundedCornerShape(12.dp))
        }
        items(categories, key = { it.id }) { cat ->
            val isSel = selected == cat.id
            FilterChip(selected = isSel, onClick = { onSelect(if (isSel) null else cat.id) },
                label = { Text(cat.name) }, shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(parseHexColor(cat.colorHex)))
                })
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📝", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
