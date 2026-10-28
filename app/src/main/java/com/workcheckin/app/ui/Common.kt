package com.workcheckin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workcheckin.app.data.CheckType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val ThaiLocale: Locale = Locale("th", "TH")

/** อา. จ. อ. พ. พฤ. ศ. ส. */
fun DayOfWeek.thaiShort(): String = when (this) {
    DayOfWeek.SUNDAY -> "อา."
    DayOfWeek.MONDAY -> "จ."
    DayOfWeek.TUESDAY -> "อ."
    DayOfWeek.WEDNESDAY -> "พ."
    DayOfWeek.THURSDAY -> "พฤ."
    DayOfWeek.FRIDAY -> "ศ."
    DayOfWeek.SATURDAY -> "ส."
}

/** อาทิตย์ก่อน เหมือนปฏิทินไทยทั่วไป */
val WeekOrder = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
)

fun LocalDate.weekStart(): LocalDate = minusDays((dayOfWeek.value % 7).toLong())

private val shortDate = DateTimeFormatter.ofPattern("d MMM", ThaiLocale)
private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ThaiLocale)

fun LocalDate.thaiShortDate(): String = format(shortDate)
fun LocalDate.thaiLongDate(): String = format(longDate)

fun daysText(mask: Int): String = when (mask) {
    0b1111111 -> "ทุกวัน"
    0b0011111 -> "จันทร์ - ศุกร์"
    0b0111111 -> "จันทร์ - เสาร์"
    else -> WeekOrder.filter { mask and (1 shl (it.value - 1)) != 0 }.joinToString(" ") { it.thaiShort() }
}

fun CheckType.icon(): ImageVector =
    if (this == CheckType.IN) Icons.AutoMirrored.Outlined.Login else Icons.AutoMirrored.Outlined.Logout

@Composable
fun TypeBadge(type: CheckType, tint: Color, background: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(title: String, initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60) % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("ตกลง") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("ตกลง") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    ) {
        DatePicker(state = state)
    }
}
