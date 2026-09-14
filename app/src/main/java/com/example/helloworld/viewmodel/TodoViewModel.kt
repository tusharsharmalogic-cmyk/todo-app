package com.example.helloworld.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val todos: StateFlow<List<Todo>> = repo.todos
        .combine(_filter) { list, f ->
            when (f) {
                FilterMode.All -> list
                FilterMode.Active -> list.filter { !it.isDone }
                FilterMode.Completed -> list.filter { it.isDone }
            }
        }
        .combine(_searchQuery) { list, q ->
            if (q.isBlank()) list
            else list.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var cached: List<Todo> = emptyList()

    init {
        viewModelScope.launch {
            repo.todos.collect { cached = it }
        }
    }

    fun setFilter(mode: FilterMode) { _filter.value = mode }
    fun setSearch(q: String) { _searchQuery.value = q }

    fun getById(id: Long): Todo? = cached.find { it.id == id }

    fun addTodo(title: String, description: String, priority: Int) {
        viewModelScope.launch {
            val item = Todo(
                title = title.trim(),
                description = description.trim(),
                priority = priority
            )
            repo.save(cached + item)
        }
    }

    fun updateTodo(id: Long, title: String, description: String, priority: Int) {
        viewModelScope.launch {
            repo.save(
                cached.map {
                    if (it.id == id) it.copy(
                        title = title.trim(),
                        description = description.trim(),
                        priority = priority
                    ) else it
                }
            )
        }
    }

    fun toggleDone(id: Long) {
        viewModelScope.launch {
            repo.save(cached.map { if (it.id == id) it.copy(isDone = !it.isDone) else it })
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            repo.save(cached.filterNot { it.id == id })
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repo.save(cached.filterNot { it.isDone })
        }
    }

    val activeCount: Int get() = cached.count { !it.isDone }
    val completedCount: Int get() = cached.count { it.isDone }
}