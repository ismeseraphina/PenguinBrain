package com.mhss.app.presentation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mhss.app.ui.components.common.DateDialog
import com.mhss.app.util.date.formatDate
import java.util.Calendar as JCal

/** A calendar category made on the website (kept on the phone after each sync). */
data class EventCategoryUi(val id: String, val name: String, val color: Int)

fun loadEventCategories(context: Context): List<EventCategoryUi> = try {
    val arr = org.json.JSONArray(
        context.getSharedPreferences("penguin_categories", Context.MODE_PRIVATE).getString("json", "[]")
    )
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val hex = o.optString("color").removePrefix("#")
        val rgb = hex.toIntOrNull(16) ?: return@mapNotNull null
        if (hex.length != 6) return@mapNotNull null
        EventCategoryUi(o.optString("id"), o.optString("name"), (0xFF000000L or rgb.toLong()).toInt())
    }
} catch (_: Exception) {
    emptyList()
}

fun endOfDay(millis: Long): Long = JCal.getInstance().apply {
    timeInMillis = millis
    set(JCal.HOUR_OF_DAY, 23); set(JCal.MINUTE, 59); set(JCal.SECOND, 59); set(JCal.MILLISECOND, 0)
}.timeInMillis

@Composable
fun RepeatUntilSection(until: Long?, startMillis: Long, onUntilChange: (Long?) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { show = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Repeat until", style = MaterialTheme.typography.bodyLarge)
            Text(
                until?.formatDate(forceShowYear = true) ?: "Forever (tap to set the last day, e.g. semester end)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (until != null) TextButton(onClick = { onUntilChange(null) }) { Text("Clear") }
    }
    if (show) DateDialog(
        initialDate = until ?: startMillis,
        onDismissRequest = { show = false }
    ) {
        onUntilChange(endOfDay(maxOf(it, startMillis)))
        show = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySection(
    categories: List<EventCategoryUi>,
    selectedId: String,
    onSelect: (EventCategoryUi?) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Category", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(6.dp))
        if (categories.isEmpty()) {
            Text(
                "Sync once to get your categories from the website (Settings → Sign in & Sync).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("None", Color.Gray, selectedId.isEmpty()) { onSelect(null) }
            categories.forEach { c ->
                CategoryChip(c.name, Color(c.color), c.id == selectedId) { onSelect(c) }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            "Edit names and colours on the website.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, if (selected) color else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
