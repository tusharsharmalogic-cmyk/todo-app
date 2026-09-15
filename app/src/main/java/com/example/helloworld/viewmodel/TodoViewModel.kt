package com.example.helloworld.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.AppSettings
import com.example.helloworld.data.Category
import com.example.helloworld.data.Todo
import com.example.helloworld.data.TodoRepository
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

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _all = MutableStateFlow<List<Todo>>(emptyList())
    val allTodos: StateFlow<List<Todo>> = _all

    val categories: StateFlow<List<Category>> = repo.settings
        .combine(_all) { s, _ -> Category.all(s.customCategories) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Category.PRESETS)

    init {
        viewModelScope.launch {
            repo.settings.collect { s ->
                _filter.value = FilterMode.values()
                    .getOrElse(s.lastFilterOrdinal) { FilterMode.All }
                _categoryFilter.value = s.lastCategoryFilter
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
            result
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
            repo.save(_all.value.map { if (it.id == id) it.copy(isDone = !it.isDone) else it })
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            val item = _all.value.find { it.id == id }
            _lastDeleted.value = item
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

    // Sirf in-memory swap — disk save nahi, no IO on every drag event
    fun moveTodo(from: Int, to: Int) {
        val list = _all.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        // orderIndex update karo but disk pe mat likho
        _all.value = list.mapIndexed { idx, t -> t.copy(orderIndex = idx) }
    }

    // Drag end hone par call karo — tabhi disk pe save hoga
    fun saveOrder() {
        val snapshot = _all.value
        viewModelScope.launch { repo.save(snapshot) }
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
        // reassign todos with this category to default
        viewModelScope.launch {
            repo.save(_all.value.map {
                if (it.categoryId == id) it.copy(categoryId = Category.DEFAULT_ID) else it
            })
            repo.saveSettings(
                settings.value.copy(
                    customCategories = settings.value.customCategories.filterNot { it.id == id }
                )
            )
        }
    }

    // Stats
    fun totalCount(): Int = _all.value.size
    fun activeCount(): Int = _all.value.count { !it.isDone }
    fun completedCount(): Int = _all.value.count { it.isDone }
    fun overdueCount(): Int = _all.value.count { it.isOverdue }
}