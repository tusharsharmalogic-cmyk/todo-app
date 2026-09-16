package com.tushar.sharma.logic.todo.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tushar.sharma.logic.todo.data.*
import com.tushar.sharma.logic.todo.notifications.ReminderScheduler
import com.tushar.sharma.logic.todo.ui.theme.GradientEnd
import com.tushar.sharma.logic.todo.ui.theme.GradientStart
import com.tushar.sharma.logic.todo.ui.theme.parseHexColor
import com.tushar.sharma.logic.todo.viewmodel.TodoViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    viewModel: TodoViewModel,
    todoId: Long,
    initialCategoryId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEdit = todoId != -1L
    val existing = remember(todoId) { if (isEdit) viewModel.getById(todoId) else null }
    val settings by viewModel.settings.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var priority by remember { mutableStateOf(existing?.priority ?: 1) }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: initialCategoryId) }

    // Deadline
    var deadlineDate by remember { mutableStateOf<Long?>(existing?.deadlineDate ?: viewModel.defaultDeadline()) }
    var deadlineTime by remember { mutableStateOf<Long?>(existing?.deadlineTime) }

    // Reminder
    var reminderDate by remember { mutableStateOf<Long?>(existing?.reminder?.triggerAt) }
    var reminderRepeat by remember { mutableStateOf(existing?.reminder?.repeat ?: ReminderRepeat.None) }
    var reminderRepeatDays by remember { mutableStateOf(existing?.reminder?.repeatDays ?: emptySet()) }

    // Repeat task
    var repeatDays by remember { mutableStateOf(existing?.repeatDays ?: emptySet<Int>()) }

    var titleError by remember { mutableStateOf(false) }
    var repeatConflictError by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val titleFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val fullFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    fun buildReminder(): ReminderConfig? {
        val triggerMs = reminderDate ?: return null
        return ReminderConfig(
            triggerAt = triggerMs,
            repeat = reminderRepeat,
            repeatDays = reminderRepeatDays
        )
    }

    fun resetFields() {
        title = ""; description = ""; priority = 1
        deadlineDate = viewModel.defaultDeadline(); deadlineTime = null
        reminderDate = null; reminderRepeat = ReminderRepeat.None; reminderRepeatDays = emptySet()
        repeatDays = emptySet()
        titleFocus.requestFocus()
    }

    fun saveAction(stayOpen: Boolean) {
        if (title.isBlank()) { titleError = true; return }

        val conflictFound = viewModel.checkRepeatConflict(deadlineDate, repeatDays)
        if (conflictFound) { repeatConflictError = true; return }

        val reminder = buildReminder()

        if (isEdit && existing != null) {
            viewModel.updateTodo(existing.id, title, description, priority, categoryId,
                deadlineDate, deadlineTime, reminder, repeatDays)
            onBack()
        } else {
            val newId = viewModel.addTodo(title, description, priority, categoryId,
                deadlineDate, deadlineTime, reminder, repeatDays)
            if (stayOpen) {
                resetFields()
                scope.launch { snackbarHostState.showSnackbar("Task added!") }
            } else onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Task" else "New Task", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))))

            // Title
            OutlinedTextField(
                value = title, onValueChange = { title = it; titleError = false },
                label = { Text("Title") }, isError = titleError, singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveAction(!isEdit) })
            )
            if (titleError) Text("Title cannot be empty",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)

            // Description
            OutlinedTextField(value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") }, minLines = 3,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())

            // Category
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Category", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddCategory = true }) { Icon(Icons.Rounded.Add, null) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.size) { idx ->
                    val cat = categories[idx]
                    FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id },
                        label = { Text(cat.name) }, shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(parseHexColor(cat.colorHex)))
                        })
                }
            }

            // Priority
            Text("Priority", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Low" to 0, "Medium" to 1, "High" to 2).forEach { (label, value) ->
                    val selected = priority == value
                    FilterChip(selected = selected, onClick = { priority = value },
                        label = { Text(label) }, shape = RoundedCornerShape(12.dp),
                        leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null,
                        modifier = Modifier.weight(1f))
                }
            }

            // ---- DEADLINE ----
            Text("Deadline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pickDate(context, deadlineDate) { deadlineDate = it } },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.DateRange, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(deadlineDate?.let { dateFmt.format(Date(it)) } ?: "Pick date")
                }
                OutlinedButton(
                    onClick = { pickTime(context) { deadlineTime = it } },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(deadlineTime?.let { timeFmt.format(Date(it)) } ?: "Time")
                }
                if (deadlineTime != null) {
                    IconButton(onClick = { deadlineTime = null }) { Icon(Icons.Rounded.Close, null) }
                }
            }
            if (deadlineDate == null) {
                Text("No deadline — task will appear in 'No Date' filter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Overdue after: ${deadlineTime?.let { fullFmt.format(Date(it)) } ?: (deadlineDate?.let { dateFmt.format(Date(it)) } + " 23:59")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- REMINDER ----
            Text("Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pickDateTime(context, reminderDate) { reminderDate = it } },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Notifications, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(reminderDate?.let { fullFmt.format(Date(it)) } ?: "Set reminder")
                }
                if (reminderDate != null) {
                    IconButton(onClick = { reminderDate = null; reminderRepeat = ReminderRepeat.None }) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }
            }

            if (reminderDate != null) {
                Text("Repeat reminder", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val opts = listOf("None" to ReminderRepeat.None, "Daily" to ReminderRepeat.Daily,
                        "Weekly" to ReminderRepeat.Weekly)
                    items(opts.size) { idx ->
                        val (label, value) = opts[idx]
                        FilterChip(selected = reminderRepeat == value,
                            onClick = { reminderRepeat = value; if (value != ReminderRepeat.Weekly) reminderRepeatDays = emptySet() },
                            label = { Text(label) }, shape = RoundedCornerShape(12.dp))
                    }
                }
                if (reminderRepeat == ReminderRepeat.Weekly) {
                    Text("Repeat on days:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DaySelector(selected = reminderRepeatDays, onToggle = { day ->
                        reminderRepeatDays = if (day in reminderRepeatDays) reminderRepeatDays - day else reminderRepeatDays + day
                    })
                }
                Text("Reminder fires only on days within the deadline window",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- REPEAT TASK ----
            Text("Repeat Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Task repeats on selected days each week",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DaySelector(selected = repeatDays, onToggle = { day ->
                val newSet = if (day in repeatDays) repeatDays - day else repeatDays + day
                repeatDays = newSet
                repeatConflictError = false
            })
            if (repeatConflictError) {
                Text("⚠ Conflict: selected repeat days overlap within deadline duration. Reduce days or change deadline.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = { saveAction(false) }, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (isEdit) "Save Changes" else "Add Task", style = MaterialTheme.typography.titleMedium)
            }
            if (!isEdit) {
                OutlinedButton(onClick = { saveAction(true) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp))
                    Text("Save & Add Another", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onConfirm = { name, color -> viewModel.addCategory(name, color); showAddCategory = false }
        )
    }
}

@Composable
fun DaySelector(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val days = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { (day, label) ->
            val isSel = day in selected
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(Category.COLOR_CHOICES.first()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New Category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Category.COLOR_CHOICES.size) { i ->
                        val hex = Category.COLOR_CHOICES[i]; val sel = color == hex
                        Box(Modifier.size(36.dp).clip(CircleShape).background(parseHexColor(hex))
                            .clickable { color = hex }, contentAlignment = Alignment.Center) {
                            if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, color) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun pickDate(context: Context, current: Long?, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { if (current != null) timeInMillis = current }
    DatePickerDialog(context, { _, y, m, d ->
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }
        onResult(c.timeInMillis)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

private fun pickTime(context: Context, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    TimePickerDialog(context, { _, hh, mm ->
        cal.set(Calendar.HOUR_OF_DAY, hh); cal.set(Calendar.MINUTE, mm); cal.set(Calendar.SECOND, 0)
        onResult(cal.timeInMillis)
    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
}

private fun pickDateTime(context: Context, current: Long?, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { if (current != null) timeInMillis = current }
    DatePickerDialog(context, { _, y, m, d ->
        cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
        TimePickerDialog(context, { _, hh, mm ->
            cal.set(Calendar.HOUR_OF_DAY, hh); cal.set(Calendar.MINUTE, mm); cal.set(Calendar.SECOND, 0)
            onResult(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}
