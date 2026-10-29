package com.workcheckin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.Shift
import com.workcheckin.app.data.hhmm
import java.time.LocalDateTime
import com.workcheckin.app.notify.ReminderScheduler
import com.workcheckin.app.ui.theme.Brown
import com.workcheckin.app.ui.theme.CheckGreen
import com.workcheckin.app.ui.theme.DarkBrown
import com.workcheckin.app.ui.theme.Muted
import com.workcheckin.app.ui.theme.Peach
import com.workcheckin.app.ui.theme.Rust

@Composable
fun ShiftsScreen(onEdit: (Long) -> Unit, vm: ShiftsViewModel = viewModel()) {
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0) },
                containerColor = Peach,
                contentColor = Rust,
                shape = RoundedCornerShape(32.dp),
                icon = { Icon(Icons.Outlined.AddCircleOutline, null) },
                text = { Text("เพิ่ม", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("ตารางเวลา", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DarkBrown)
            }
            item { ReminderWarnings() }
            item { TestReminderCard() }
            val list = shifts.orEmpty()
            if (shifts != null && list.isEmpty()) {
                item {
                    Text(
                        "ยังไม่มีตารางเวลา กด \"เพิ่ม\" เพื่อตั้งเวลาเข้างาน/ออกงาน",
                        color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(list, key = { it.id }) { shift ->
                ShiftCard(shift, onClick = { onEdit(shift.id) }, onToggle = { vm.setEnabled(shift, it) })
            }
        }
    }
}

@Composable
private fun ShiftCard(shift: Shift, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(shift.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
                Spacer(Modifier.height(10.dp))
                CheckType.entries.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        TypeBadge(type, tint = Brown, background = Peach, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${if (type == CheckType.IN) "เข้างาน" else "ออกงาน"} ${shift.minutesFor(type).hhmm()} น.",
                            fontSize = 16.sp, color = DarkBrown,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(daysText(shift.daysMask), color = Muted, fontSize = 14.sp)
                nextReminderText(shift)?.let { Text(it, color = Brown, fontSize = 13.sp) }
            }
            Switch(
                checked = shift.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = CheckGreen),
            )
        }
    }
}

private fun nextReminderText(shift: Shift): String? {
    val now = LocalDateTime.now()
    val next = CheckType.entries.mapNotNull { ReminderScheduler.nextOccurrence(shift, it, now)?.first }.minOrNull()
        ?: return null
    val day = next.toLocalDate()
    return "เตือนครั้งถัดไป: ${day.dayOfWeek.thaiShort()} ${day.thaiShortDate()} ${"%02d:%02d".format(next.hour, next.minute)} น."
}
