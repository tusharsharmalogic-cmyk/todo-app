package com.tushar.sharma.logic.todo.data

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val name: String,
    val colorHex: String
) {
    companion object {
        const val DEFAULT_ID = "default"

        val DEFAULT = Category(
            id = DEFAULT_ID,
            name = "General",
            colorHex = "#6750A4"
        )

        val PRESETS = listOf(
            Category(DEFAULT_ID, "General", "#6750A4"),
            Category("work", "Work", "#2196F3"),
            Category("personal", "Personal", "#4CAF50"),
            Category("shopping", "Shopping", "#FF9800"),
            Category("health", "Health", "#E91E63"),
            Category("study", "Study", "#9C27B0")
        )

        val COLOR_CHOICES = listOf(
            "#6750A4", "#2196F3", "#4CAF50", "#FF9800",
            "#E91E63", "#9C27B0", "#009688", "#F44336",
            "#3F51B5", "#795548", "#607D8B", "#FF5722"
        )

        fun all(custom: List<Category>): List<Category> = PRESETS + custom

        fun byId(id: String, custom: List<Category> = emptyList()): Category =
            (PRESETS + custom).firstOrNull { it.id == id } ?: DEFAULT
    }
}