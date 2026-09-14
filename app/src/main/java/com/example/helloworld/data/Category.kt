package com.example.helloworld.data

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

        fun byId(id: String): Category =
            PRESETS.firstOrNull { it.id == id } ?: DEFAULT
    }
}