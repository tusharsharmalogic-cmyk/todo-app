package com.tushar.sharma.logic.todo.data

import kotlinx.serialization.Serializable

@Serializable
enum class ReminderRepeat { None, Daily, Weekly }

@Serializable
data class ReminderConfig(
    val triggerAt: Long,
    val repeat: ReminderRepeat = ReminderRepeat.None,
    val repeatDays: Set<Int> = emptySet()
)

@Serializable
data class Todo(
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val description: String = "",
    val isDone: Boolean = false,
    val completedAt: Long? = null,
    val priority: Int = 1,
    val categoryId: String = Category.DEFAULT_ID,
    val deadlineDate: Long? = null,
    val deadlineTime: Long? = null,
    val reminder: ReminderConfig? = null,
    val repeatDays: Set<Int> = emptySet(),
    val repeatParentId: Long? = null,
    val instanceDate: Long? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val deadlineMillis: Long?
        get() = deadlineTime ?: deadlineDate?.let {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = it
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }

    val isOverdue: Boolean
        get() = !isDone && deadlineMillis != null && deadlineMillis!! < System.currentTimeMillis()

    val isRepeat: Boolean get() = repeatDays.isNotEmpty()
    val isInstance: Boolean get() = repeatParentId != null
}
