package com.tushar.sharma.logic.todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tushar.sharma.logic.todo.data.Category
import com.tushar.sharma.logic.todo.data.Todo
import com.tushar.sharma.logic.todo.ui.theme.PriorityHigh
import com.tushar.sharma.logic.todo.ui.theme.PriorityLow
import com.tushar.sharma.logic.todo.ui.theme.PriorityMedium
import com.tushar.sharma.logic.todo.ui.theme.parseHexColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoItem(
    todo: Todo,
    categories: List<Category>,
    onClick: (() -> Unit)?,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val priorityColor = when (todo.priority) {
        2 -> PriorityHigh
        0 -> PriorityLow
        else -> PriorityMedium
    }
    val cat = Category.byId(todo.categoryId, categories)
    val catColor = parseHexColor(cat.colorHex)

    // Completed task: faded/greyed-out look that adapts to light & dark theme
    val cardBgColor = if (todo.isDone)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    else
        MaterialTheme.colorScheme.surface

    val borderColor = if (todo.isDone)
        MaterialTheme.colorScheme.outlineVariant
    else
        Color.Transparent

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete?.invoke()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !readOnly,
        backgroundContent = {
            val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val bgColor = if (isSwiping) MaterialTheme.colorScheme.errorContainer
            else Color.Transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSwiping) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (todo.isDone) 1.5.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(20.dp)
                ),
            onClick = { onClick?.invoke() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (todo.isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f) else cardBgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (todo.isDone) 0.dp else 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(priorityColor)
                )
                Spacer(Modifier.width(10.dp))

                // Toggle button
                IconButton(
                    onClick = { onToggle?.invoke() },
                    enabled = !readOnly,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (todo.isDone) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (todo.isDone) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = if (todo.isDone) TextDecoration.LineThrough
                        else TextDecoration.None,
                        color = if (todo.isDone)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (todo.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = todo.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (todo.isDone) 0.5f else 1f
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = catColor.copy(alpha = if (todo.isDone) 0.10f else 0.18f)
                        ) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = catColor.copy(alpha = if (todo.isDone) 0.6f else 1f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        val dlMs = todo.deadlineMillis
                        if (dlMs != null) {
                            val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                            val label = fmt.format(Date(dlMs))
                            val dueColor = if (todo.isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                text = (if (todo.isOverdue) "⏰ " else "📅 ") + label,
                                style = MaterialTheme.typography.labelSmall, color = dueColor,
                                fontWeight = if (todo.isOverdue) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        if (todo.isRepeat) {
                            Text("🔁", style = MaterialTheme.typography.labelSmall)
                        }
                        if (todo.isDone && todo.completedAt != null) {
                            val cFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
                            Text("✓ ${cFmt.format(Date(todo.completedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }

                // Up/Down reorder buttons — sirf active tasks pe dikhenge
                if (!todo.isDone && (onMoveUp != null || onMoveDown != null)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { onMoveUp?.invoke() },
                            modifier = Modifier.size(28.dp),
                            enabled = onMoveUp != null
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                tint = if (onMoveUp != null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onMoveDown?.invoke() },
                            modifier = Modifier.size(28.dp),
                            enabled = onMoveDown != null
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                tint = if (onMoveDown != null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}