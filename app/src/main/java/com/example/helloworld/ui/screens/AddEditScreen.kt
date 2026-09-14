package com.example.helloworld.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.helloworld.data.Category
import com.example.helloworld.notifications.ReminderScheduler
import com.example.helloworld.ui.theme.GradientEnd
import com.example.helloworld.ui.theme.GradientStart
import com.example.helloworld.ui.theme.parseHexColor
import com.example.helloworld.viewmodel.TodoViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var priority by remember { mutableStateOf(existing?.priority ?: 1) }
    var categoryId by remember {
        mutableStateOf(existing?.categoryId ?: initialCategoryId)
    }
    var dueDate by remember { mutableStateOf<Long?>(existing?.dueDate) }
    var reminderMinutes by remember { mutableStateOf<Int?>(existing?.reminderMinutes) }
    var titleError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEdit) "Edit Task" else "New Task",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = false
                },
                label = { Text("Title") },
                placeholder = { Text("e.g. Buy groceries") },
                isError = titleError,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (titleError) {
                Text(
                    text = "Title cannot be empty",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("Add more details...") },
                minLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Category.PRESETS.size) { idx ->
                    val cat = Category.PRESETS[idx]
                    val isSel = categoryId == cat.id
                    FilterChip(
                        selected = isSel,
                        onClick = { categoryId = cat.id },
                        label = { Text(cat.name) },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(parseHexColor(cat.colorHex))
                            )
                        }
                    )
                }
            }

            // Priority
            Text(
                text = "Priority",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val labels = listOf("Low" to 0, "Medium" to 1, "High" to 2)
                labels.forEach { (label, value) ->
                    val selected = priority == value
                    FilterChip(
                        selected = selected,
                        onClick = { priority = value },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Due date
            Text(
                text = "Due date",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { pickDateTime(context) { dueDate = it } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    Text(
                        text = dueDate?.let { fmt.format(Date(it)) } ?: "Pick date & time"
                    )
                }
                if (dueDate != null) {
                    IconButton(onClick = { dueDate = null; reminderMinutes = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            }

            // Reminder
            if (dueDate != null) {
                Text(
                    text = "Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val opts = listOf<Pair<String, Int?>>(
                        "None" to null,
                        "At time" to 0,
                        "5 min" to 5,
                        "15 min" to 15,
                        "1 hour" to 60
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(opts.size) { idx ->
                            val (label, value) = opts[idx]
                            FilterChip(
                                selected = reminderMinutes == value,
                                onClick = { reminderMinutes = value },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = if (value != null) {
                                    { Icon(Icons.Rounded.Notifications, null,
                                        modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            fun saveAndMaybeStay(stayOpen: Boolean) {
                if (title.isBlank()) {
                    titleError = true
                    return
                }
                val trigger = if (dueDate != null && reminderMinutes != null) {
                    dueDate!! - reminderMinutes!! * 60_000L
                } else null

                if (isEdit && existing != null) {
                    viewModel.updateTodo(
                        existing.id, title, description, priority,
                        categoryId, dueDate, reminderMinutes
                    )
                    if (trigger != null) {
                        ReminderScheduler.schedule(
                            context, existing.id, title, description, trigger
                        )
                    } else {
                        ReminderScheduler.cancel(context, existing.id)
                    }
                    onBack()
                } else {
                    viewModel.addTodo(title, description, priority,
                        categoryId, dueDate, reminderMinutes)
                    // schedule after add: we don't know the id here; use a
                    // delayed lookup via viewModel.allTodos
                    if (trigger != null) {
                        scope.launch {
                            kotlinx.coroutines.delay(200)
                            val added = viewModel.allTodos.value
                                .maxByOrNull { it.createdAt }
                            if (added != null) {
                                ReminderScheduler.schedule(
                                    context, added.id, added.title,
                                    added.description, trigger
                                )
                            }
                        }
                    }
                    if (stayOpen) {
                        title = ""
                        description = ""
                        priority = 1
                        dueDate = null
                        reminderMinutes = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Task added. Add another!")
                        }
                    } else {
                        onBack()
                    }
                }
            }

            Button(
                onClick = { saveAndMaybeStay(false) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (isEdit) "Save Changes" else "Add Task",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!isEdit) {
                OutlinedButton(
                    onClick = { saveAndMaybeStay(true) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save & Add Another", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun pickDateTime(context: Context, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, y, m, d ->
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(
                context,
                { _, hh, mm ->
                    cal.set(Calendar.HOUR_OF_DAY, hh)
                    cal.set(Calendar.MINUTE, mm)
                    cal.set(Calendar.SECOND, 0)
                    onResult(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}