package com.tushar.sharma.logic.todo.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val themeMode: Int = 0,                 // 0=System, 1=Light, 2=Dark
    val accentHex: String = "#6750A4",
    val dynamicColor: Boolean = true,
    val fontScale: Float = 1.0f,
    val defaultDeadlineDays: Int = 0,       // 0=today, 1=tomorrow, etc.
    val lastFilterOrdinal: Int = 0,
    val lastCategoryFilter: String? = null,
    val customCategories: List<Category> = emptyList(),
    val categoryOrder: List<String> = emptyList(),
    val hiddenCategories: List<String> = emptyList(),
    val pinCode: String = ""
)
