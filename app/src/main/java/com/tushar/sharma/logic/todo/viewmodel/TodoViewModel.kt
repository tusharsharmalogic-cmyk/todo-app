package com.tushar.sharma.logic.todo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tushar.sharma.logic.todo.data.AppSettings
import com.tushar.sharma.logic.todo.data.Category
import com.tushar.sharma.logic.todo.data.ReminderConfig
import com.tushar.sharma.logic.todo.data.Todo
import com.tushar.sharma.logic.todo.data.TodoRepository
import com.tushar.sharma.logic.todo.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class FilterMode {
    All, Active, Completed, Today, Date, NoDeadline, Overdue;
    val label: String get() = when (this) {
        All -> "All"; Active -> "Active"; Completed -> "Done"
        Today -> "Today"; Date -> "Date"; NoDeadline -> "No Date"; Overdue -> "Overdue"
    }
}

class TodoViewModel(app: Application) : AndroidViewModel(app) {

    val repo = TodoRepository(app)

    private val _filter = MutableStateFlow(FilterMode.All)
    val filter: StateFlow<FilterMode> = _filter

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _lastDeleted = MutableStateFlow<Todo?>(null)
    val lastDeleted: StateFlow<Todo?> = _lastDeleted

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded

    private val _dateFilterMs = MutableStateFlow<Long?>(null)
    val dateFilterMs: StateFlow<Long?> = _dateFilterMs

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _all = MutableStateFlow<List<Todo>>(emptyList())
    val allTodos: StateFlow<List<Todo>> = _all

    val categories: StateFlow<List<Category>> = repo.settings
        .combine(_all) { s, _ ->
            Category.all(s.customCategories, s.categoryOrder, s.hiddenCategories)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Category.PRESETS)

    init {
        viewModelScope.launch {
            repo.settings.collect { s ->
                _filter.value = FilterMode.values().getOrElse(s.lastFilterOrdinal) { FilterMode.All }
                _categoryFilter.value = s.lastCategoryFilter
                _settingsLoaded.value = true
            }
        }
        viewModelScope.launch {
            repo.todos.collect { list ->
                val processed = processRepeatInstances(list)
                _all.value = processed.sortedBy { it.orderIndex }
            }
        }
    }

    // ---- Repeat instance generation ----
    private suspend fun processRepeatInstances(list: List<Todo>): List<Todo> {
        val now = System.currentTimeMillis()
        val todayStart = dayStart(now)
        val todayEnd = dayEnd(now)
        val todayDay = calDayToOurs(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))

        val parents = list.filter { it.isRepeat && !it.isInstance }
        val existingInstances = list.filter { it.isInstance }
        val result = list.toMutableList()

        for (parent in parents) {
            if (!parent.repeatDays.contains(todayDay)) continue

            val alreadyExists = existingInstances.any {
                it.repeatParentId == parent.id && isSameDay(it.instanceDate ?: 0L, todayStart)
            }
            if (alreadyExists) continue

            // Create today's instance
            val deadline = parent.deadlineMillis?.let { parentDl ->
                // Deadline is relative duration from parent's creation
                val durationMs = parentDl - dayStart(parent.deadlineDate ?: parentDl)
                todayStart + durationMs
            } ?: dayEnd(todayStart)

            val instance = parent.copy(
                id = parent.id * 1000 + todayStart / (24 * 60 * 60 * 1000),
                isDone = false,
                completedAt = null,
                repeatParentId = parent.id,
                instanceDate = todayStart,
                deadlineDate = todayStart,
                deadlineTime = deadline,
                createdAt = now
            )
            result.add(instance)
        }

        // Save if new instances were added
        if (result.size != list.size) {
            repo.save(result)
        }

        return result
    }

    fun calDayToOurs(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7
    }

    fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayEnd(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    fun isSameDay(a: Long, b: Long): Boolean = dayStart(a) == dayStart(b)

    // ---- Filtered todos ----
    val todos: StateFlow<List<Todo>> =
        combine(_all, _filter, _categoryFilter, _searchQuery, _dateFilterMs) { list, f, cat, q, dateMs ->
            var result = list

            if (cat != null) result = result.filter { it.categoryId == cat }
            if (q.isNotBlank()) result = result.filter {
                it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
            }

            val now = System.currentTimeMillis()
            val todayStart = dayStart(now)
            val todayEnd = dayEnd(now)

            result = when (f) {
                FilterMode.All -> result.filter { !it.isOverdue }
                FilterMode.Active -> result.filter { !it.isDone && !it.isOverdue }
                FilterMode.Completed -> result.filter { it.isDone }
                FilterMode.Today -> result.filter { todo ->
                    !todo.isDone && !todo.isOverdue &&
                        todo.deadlineMillis?.let { it in todayStart..todayEnd } == true
                }
                FilterMode.Date -> {
                    if (dateMs != null) {
                        val ds = dayStart(dateMs); val de = dayEnd(dateMs)
                        val calDay = Calendar.getInstance().apply { timeInMillis = dateMs }
                        val dayOfWeek = calDayToOurs(calDay.get(Calendar.DAY_OF_WEEK))
                        result.filter { todo ->
                            if (todo.isRepeat && !todo.isInstance) {
                                todo.repeatDays.contains(dayOfWeek)
                            } else {
                                todo.deadlineMillis?.let { it in ds..de } == true
                            }
                        }
                    } else result
                }
                FilterMode.NoDeadline -> result.filter {
                    !it.isRepeat && it.deadlineDate == null && !it.isDone
                }
                FilterMode.Overdue -> result.filter { it.isOverdue }
            }

            result.sortedWith(
                compareBy<Todo> { if (it.isDone) 1 else 0 }.thenBy { it.orderIndex }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(mode: FilterMode) {
        _filter.value = mode
        viewModelScope.launch { repo.saveSettings(settings.value.copy(lastFilterOrdinal = mode.ordinal)) }
    }

    fun setDateFilter(ms: Long?) { _dateFilterMs.value = ms }

    fun setCategoryFilter(id: String?) {
        _categoryFilter.value = id
        viewModelScope.launch { repo.saveSettings(settings.value.copy(lastCategoryFilter = id)) }
    }

    fun setSearch(q: String) { _searchQuery.value = q }

    fun getById(id: Long): Todo? = _all.value.find { it.id == id }

    private fun nextOrder(): Int = (_all.value.maxOfOrNull { it.orderIndex } ?: -1) + 1

    fun checkRepeatConflict(deadlineMs: Long?, repeatDays: Set<Int>): Boolean {
        if (repeatDays.size < 2 || deadlineMs == null) return false
        val dl = deadlineMs
        val durationDays = ((dl - dayStart(dl)) / (24 * 60 * 60 * 1000)).toInt() + 1
        val sortedDays = repeatDays.sorted()
        for (i in sortedDays.indices) {
            for (j in i + 1 until sortedDays.size) {
                val diff = sortedDays[j] - sortedDays[i]
                val diffWrapped = minOf(diff, 7 - diff)
                if (diffWrapped < durationDays) return true
            }
        }
        return false
    }

    fun defaultDeadline(): Long {
        val days = settings.value.defaultDeadlineDays
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun addTodo(
        title: String, description: String, priority: Int, categoryId: String,
        deadlineDate: Long?, deadlineTime: Long?,
        reminder: ReminderConfig?, repeatDays: Set<Int>
    ): Long {
        val item = Todo(
            title = title.trim(), description = description.trim(),
            priority = priority, categoryId = categoryId,
            deadlineDate = deadlineDate, deadlineTime = deadlineTime,
            reminder = reminder, repeatDays = repeatDays,
            orderIndex = nextOrder()
        )
        viewModelScope.launch { repo.save(_all.value + item) }
        scheduleReminderFor(item)
        return item.id
    }

    fun updateTodo(
        id: Long, title: String, description: String, priority: Int, categoryId: String,
        deadlineDate: Long?, deadlineTime: Long?,
        reminder: ReminderConfig?, repeatDays: Set<Int>
    ) {
        viewModelScope.launch {
            val updated = _all.value.map {
                if (it.id == id) it.copy(
                    title = title.trim(), description = description.trim(),
                    priority = priority, categoryId = categoryId,
                    deadlineDate = deadlineDate, deadlineTime = deadlineTime,
                    reminder = reminder, repeatDays = repeatDays
                ) else it
            }
            repo.save(updated)
            updated.find { it.id == id }?.let { scheduleReminderFor(it) }
        }
    }

    private fun scheduleReminderFor(todo: Todo) {
        val ctx = getApplication<Application>()
        val r = todo.reminder ?: run { ReminderScheduler.cancel(ctx, todo.id); return }
        if (r.triggerAt > System.currentTimeMillis()) {
            ReminderScheduler.schedule(ctx, todo.id, todo.title, todo.description, r.triggerAt)
        }
    }

    fun toggleDone(id: Long) {
        viewModelScope.launch {
            val item = _all.value.find { it.id == id } ?: return@launch
            val nowDone = !item.isDone
            val ctx = getApplication<Application>()
            if (nowDone) ReminderScheduler.cancel(ctx, id) else scheduleReminderFor(item)
            repo.save(_all.value.map {
                if (it.id == id) it.copy(
                    isDone = nowDone,
                    completedAt = if (nowDone) System.currentTimeMillis() else null
                ) else it
            })
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            _lastDeleted.value = _all.value.find { it.id == id }
            ReminderScheduler.cancel(getApplication(), id)
            repo.save(_all.value.filterNot { it.id == id })
        }
    }

    fun undoDelete() {
        val item = _lastDeleted.value ?: return
        viewModelScope.launch { repo.save(_all.value + item); _lastDeleted.value = null }
    }

    fun clearUndo() { _lastDeleted.value = null }

    fun clearCompleted() {
        viewModelScope.launch { repo.save(_all.value.filterNot { it.isDone }) }
    }

    fun swapOrder(idA: Long, idB: Long) {
        val sorted = _all.value.sortedBy { it.orderIndex }.toMutableList()
        val aIdx = sorted.indexOfFirst { it.id == idA }
        val bIdx = sorted.indexOfFirst { it.id == idB }
        if (aIdx < 0 || bIdx < 0 || aIdx == bIdx) return
        val tmp = sorted[aIdx]; sorted[aIdx] = sorted[bIdx]; sorted[bIdx] = tmp
        val renumbered = sorted.mapIndexed { i, t -> t.copy(orderIndex = i) }
        _all.value = renumbered
        viewModelScope.launch { repo.save(renumbered) }
    }

    fun updateSettings(s: AppSettings) { viewModelScope.launch { repo.saveSettings(s) } }

    fun addCategory(name: String, colorHex: String) {
        val cat = Category(id = "custom_" + System.currentTimeMillis(), name = name.trim(), colorHex = colorHex)
        viewModelScope.launch { repo.saveSettings(settings.value.copy(customCategories = settings.value.customCategories + cat)) }
    }

    fun deleteCategory(id: String) {
        val s = settings.value
        val isPreset = Category.PRESETS.any { it.id == id }
        val remaining = Category.all(s.customCategories, s.categoryOrder, s.hiddenCategories).filterNot { it.id == id }
        val newDefaultId = if (id == Category.DEFAULT_ID) remaining.firstOrNull()?.id else null
        viewModelScope.launch {
            val mapped = _all.value.map { t ->
                when { t.categoryId != id -> t; newDefaultId != null -> t.copy(categoryId = newDefaultId); else -> t.copy(categoryId = Category.DEFAULT_ID) }
            }
            repo.save(mapped)
            repo.saveSettings(s.copy(
                customCategories = s.customCategories.filterNot { it.id == id },
                hiddenCategories = if (isPreset) s.hiddenCategories + id else s.hiddenCategories,
                categoryOrder = s.categoryOrder.filterNot { it == id }
            ))
        }
    }

    fun moveCategory(fromIndex: Int, toIndex: Int) {
        val list = categories.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex); list.add(toIndex, item)
        viewModelScope.launch { repo.saveSettings(settings.value.copy(categoryOrder = list.map { it.id })) }
    }

    val isLockEnabled: Boolean get() = settings.value.pinCode.isNotEmpty()
    fun setPin(pin: String) { viewModelScope.launch { repo.saveSettings(settings.value.copy(pinCode = pin)) } }
    fun clearPin() { viewModelScope.launch { repo.saveSettings(settings.value.copy(pinCode = "")) } }
    fun verifyPin(pin: String): Boolean = settings.value.pinCode == pin

    suspend fun exportData(): String = repo.exportBackupJson()
    fun importData(text: String, onResult: (Boolean) -> Unit) { viewModelScope.launch { onResult(repo.importBackupJson(text)) } }

    fun totalCount(): Int = _all.value.size
    fun activeCount(): Int = _all.value.count { !it.isDone && !it.isOverdue }
    fun completedCount(): Int = _all.value.count { it.isDone }
    fun overdueCount(): Int = _all.value.count { it.isOverdue }
}
