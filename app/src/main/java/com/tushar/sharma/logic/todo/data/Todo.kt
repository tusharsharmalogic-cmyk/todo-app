package com.tushar.sharma.logic.todo.data

import kotlinx.serialization.Serializable

@Serializable
data class Todo(
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Int = 1, // 0=Low, 1=Medium, 2=High
    val categoryId: String = Category.DEFAULT_ID,
    val dueDate: Long? = null,
    val reminderMinutes: Int? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isOverdue: Boolean
        get() = !isDone && dueDate != null && dueDate < System.currentTimeMillis()
}