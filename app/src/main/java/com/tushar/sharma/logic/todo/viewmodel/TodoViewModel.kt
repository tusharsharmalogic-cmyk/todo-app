package com.tushar.sharma.logic.todo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tushar.sharma.logic.todo.data.AppSettings
import com.tushar.sharma.logic.todo.data.Category
import com.tushar.sharma.logic.todo.data.Todo
import com.tushar.sharma.logic.todo.data.TodoRepository
import com.tushar.sharma.logic.todo.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FilterMode { All, Active, Completed }

class TodoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TodoRepository(app)

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
                _filter.value = FilterMode.values()
                    .getOrElse(s.lastFilterOrdinal) { FilterMode.All }
                _categoryFilter.value = s.lastCategoryFilter
                _settingsLoaded.value = true
            }
        }
        viewModelScope.launch {
            repo.todos.collect { list ->
                _all.value = list.sortedBy { it.orderIndex }
            }
        }
    }

    val todos: StateFlow<List<Todo>> =
        combine(_all, _filter, _categoryFilter, _searchQuery) { list, f, cat, q ->
            var result = list
            result = when (f) {
                FilterMode.All -> result
                FilterMode.Active -> result.filter { !it.isDone }
                FilterMode.Completed -> result.filter { it.isDone }
            }
            if (cat != null) result = result.filter { it.categoryId == cat }
            if (q.isNotBlank()) result = result.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true)
            }
            // Sort: active first (user order), completed neeche
            result.sortedWith(
                compareBy<Todo> { if (it.isDone) 1 else 0 }
                    .thenBy { it.orderIndex }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(mode: FilterMode) {
        _filter.value = mode
        viewModelScope.launch {
            repo.saveSettings(settings.value.copy(lastFilterOrdinal = mode.ordinal))
        }
    }

    fun setCategoryFilter(id: String?) {
        _categoryFilter.value = id
        viewModelScope.launch {
            repo.saveSettings(settings.value.copy(lastCategoryFilter = id))
        }
    }

    fun setSearch(q: String) { _searchQuery.value = q }

    fun getById(id: Long): Todo? = _all.value.find { it.id == id }

    private fun nextOrder(): Int =
        (_all.value.maxOfOrNull { it.orderIndex } ?: -1) + 1

    fun addTodo(
        title: String,
        description: String,
        priority: Int,
        categoryId: String,
        dueDate: Long?,
        reminderMinutes: Int?
    ): Long {
        val item = Todo(
            title = title.trim(),
            description = description.trim(),
            priority = priority,
            categoryId = categoryId,
            dueDate = dueDate,
            reminderMinutes = reminderMinutes,
            orderIndex = nextOrder()
        )
        viewModelScope.launch { repo.save(_all.value + item) }
        return item.id
    }

    fun updateTodo(
        id: Long,
        title: String,
        description: String,
        priority: Int,
        categoryId: String,
        dueDate: Long?,
        reminderMinutes: Int?
    ) {
        viewModelScope.launch {
            repo.save(_all.value.map {
                if (it.id == id) it.copy(
                    title = title.trim(),
                    description = description.trim(),
                    priority = priority,
                    categoryId = categoryId,
                    dueDate = dueDate,
                    reminderMinutes = reminderMinutes
                ) else it
            })
        }
    }

    fun toggleDone(id: Long) {
        viewModelScope.launch {
            val item = _all.value.find { it.id == id } ?: return@launch
            val nowDone = !item.isDone
            if (nowDone) {
                // Completed → cancel any pending reminder
                ReminderScheduler.cancel(getApplication(), id)
            } else if (item.dueDate != null && item.reminderMinutes != null) {
                // Un-done → re-schedule if reminder is still in future
                val trigger = item.dueDate - item.reminderMinutes * 60_000L
                ReminderScheduler.schedule(
                    getApplication(), id, item.title, item.description, trigger
                )
            }
            repo.save(_all.value.map { if (it.id == id) it.copy(isDone = nowDone) else it })
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            val item = _all.value.find { it.id == id }
            _lastDeleted.value = item
            ReminderScheduler.cancel(getApplication(), id)
            repo.save(_all.value.filterNot { it.id == id })
        }
    }

    fun undoDelete() {
        val item = _lastDeleted.value ?: return
        viewModelScope.launch {
            repo.save(_all.value + item)
            _lastDeleted.value = null
        }
    }

    fun clearUndo() { _lastDeleted.value = null }

    fun clearCompleted() {
        viewModelScope.launch {
            repo.save(_all.value.filterNot { it.isDone })
        }
    }

    // Swap positions between two todos (by id) — clean reorder logic
    fun swapOrder(idA: Long, idB: Long) {
        val sorted = _all.value.sortedBy { it.orderIndex }.toMutableList()
        val aIdx = sorted.indexOfFirst { it.id == idA }
        val bIdx = sorted.indexOfFirst { it.id == idB }
        if (aIdx < 0 || bIdx < 0 || aIdx == bIdx) return
        val tmp = sorted[aIdx]
        sorted[aIdx] = sorted[bIdx]
        sorted[bIdx] = tmp
        // Renumber sequentially for clean state
        val renumbered = sorted.mapIndexed { i, t -> t.copy(orderIndex = i) }
        _all.value = renumbered
        viewModelScope.launch { repo.save(renumbered) }
    }

    fun updateSettings(s: AppSettings) {
        viewModelScope.launch { repo.saveSettings(s) }
    }

    fun addCategory(name: String, colorHex: String) {
        val cat = Category(
            id = "custom_" + System.currentTimeMillis(),
            name = name.trim(),
            colorHex = colorHex
        )
        viewModelScope.launch {
            repo.saveSettings(
                settings.value.copy(
                    customCategories = settings.value.customCategories + cat
                )
            )
        }
    }

    fun deleteCategory(id: String) {
        val s = settings.value
        val isPreset = Category.PRESETS.any { it.id == id }
        // If we're deleting default, promote the first remaining category as new default
        val remainingAfterDelete = Category.all(
            s.customCategories, s.categoryOrder, s.hiddenCategories
        ).filterNot { it.id == id }
        val newDefaultId = if (id == Category.DEFAULT_ID)
            remainingAfterDelete.firstOrNull()?.id
        else null

        viewModelScope.launch {
            val mapped = _all.value.map { t ->
                when {
                    t.categoryId != id -> t
                    newDefaultId != null -> t.copy(categoryId = newDefaultId)
                    else -> t.copy(categoryId = Category.DEFAULT_ID)
                }
            }
            repo.save(mapped)
            repo.saveSettings(
                s.copy(
                    customCategories = s.customCategories.filterNot { it.id == id },
                    hiddenCategories = if (isPreset) s.hiddenCategories + id else s.hiddenCategories,
                    categoryOrder = s.categoryOrder.filterNot { it == id }
                )
            )
        }
    }

    /** Move category at [fromIndex] to [toIndex] within the visible list. */
    fun moveCategory(fromIndex: Int, toIndex: Int) {
        val list = categories.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        viewModelScope.launch {
            repo.saveSettings(settings.value.copy(categoryOrder = list.map { it.id }))
        }
    }

    // -------- App Lock (PIN) --------

    val isLockEnabled: Boolean get() = settings.value.pinCode.isNotEmpty()

    fun setPin(pin: String) {
        viewModelScope.launch {
            repo.saveSettings(settings.value.copy(pinCode = pin))
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            repo.saveSettings(settings.value.copy(pinCode = ""))
        }
    }

    fun verifyPin(pin: String): Boolean = settings.value.pinCode == pin

    // -------- Device storage sync --------

    val isDeviceStorageEnabled: Boolean get() = repo.isDeviceStorageEnabled

    fun setDeviceStorageEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setDeviceStorageEnabled(enabled) }
    }

    // Stats
    fun totalCount(): Int = _all.value.size
    fun activeCount(): Int = _all.value.count { !it.isDone }
    fun completedCount(): Int = _all.value.count { it.isDone }
    fun overdueCount(): Int = _all.value.count { it.isOverdue }
}