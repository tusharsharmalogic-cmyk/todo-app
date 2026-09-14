package com.example.helloworld.data

import kotlinx.serialization.Serializable

@Serializable
data class Todo(
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Int = 1, // 0=Low, 1=Medium, 2=High
    val createdAt: Long = System.currentTimeMillis()
)