package com.workcheckin.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workcheckin.app.data.DayStatus
import com.workcheckin.app.data.DayTask
import com.workcheckin.app.data.diffMinutes
import com.workcheckin.app.data.hhmm
import com.workcheckin.app.data.isOffTime
import com.workcheckin.app.data.label
import com.workcheckin.app.data.millisToHhmm
import com.workcheckin.app.data.status
import com.workcheckin.app.ui.theme.Brown
import com.workcheckin.app.ui.theme.CheckGreen
import com.workcheckin.app.ui.theme.DarkBrown
import com.workcheckin.app.ui.theme.LightGrey
import com.workcheckin.app.ui.theme.MissRed
import com.workcheckin.app.ui.theme.Muted
import com.workcheckin.app.ui.theme.Peach
import com.workcheckin.app.ui.theme.Rust
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun TodayScreen(onAdd: () -> Unit, vm: TodayViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var pickTimeFor by remember { mutableStateOf<DayTask?>(null) }
    var manageTask by remember { mutableStateOf<DayTask?>(null) }
    var showResolved by rememberSaveable { mutableStateOf(true) }

    val pending = s.tasks.filter { !it.done }
    val resolved = s.tasks.filter { it.done }

    fun onLongPressTask(task: DayTask) {
        if (task.date.isAfter(s.today)) {
            scope.launch { snackbar.showSnackbar("ยังไม่ถึงวันนี้") }
        } else {
            manageTask = task
        }
    }

    fun onTapTask(task: DayTask) {
        when {
            task.done -> manageTask = task
            task.date == s.today -> vm.checkNow(task)
            task.date.isBefore(s.today) -> pickTimeFor = task
            else -> scope.launch { snackbar.showSnackbar("ยังไม่ถึงวันนี้ เช็คล่วงหน้าไม่ได้") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Peach,
                contentColor = Rust,
                shape = RoundedCornerShape(32.dp),
                icon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                text = { Text("เพิ่ม", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopBar(
                title = if (s.selected == s.today) "วันนี้" else s.selected.thaiShortDate(),
                streak = s.streak,
                onCalendar = { showDatePicker = true },
            )
            WeekStrip(s, onSelect = vm::select, onSwipe = vm::shiftWeek)
            if (s.selected != s.today) {
                TextButton(
                    onClick = { vm.select(s.today) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("กลับไปวันนี้", color = Rust, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp, top = 8.dp), color = LightGrey)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pending.isNotEmpty()) {
                    item {
                        Column {
                            SectionTitle("ต้องเช็ค")
                            Text("กดเพื่อเช็ค · กดค้างเพื่อข้ามหรือระบุเวลา", fontSize = 13.sp, color = Muted)
                        }
                    }
                    item {
                        TaskCard(pending, s, onTap = ::onTapTask, onLongPress = ::onLongPressTask)
                    }
                }
                if (resolved.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { showResolved = !showResolved }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle("เสร็จแล้ว (${resolved.size})", Modifier.weight(1f))
                            Icon(if (showResolved) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Muted)
                        }
                    }
                    item {
                        AnimatedVisibility(showResolved) {
                            TaskCard(resolved, s, onTap = ::onTapTask, onLongPress = ::onLongPressTask)
                        }
                    }
                }
                item {
                    when {
                        !s.hasShifts -> EmptyState(
                            title = "ยังไม่มีตารางเวลา",
                            subtitle = "กด \"เพิ่ม\" เพื่อตั้งเวลาเข้างาน ออกงาน และวันทำงาน",
                            done = false,
                        )
                        s.tasks.isEmpty() -> EmptyState("วันนี้ไม่มีงาน", "พักผ่อนให้เต็มที่นะ", done = false)
                        pending.isEmpty() -> EmptyState("ครบแล้วสำหรับวันนี้!", "สแกนนิ้วเข้าออกครบทุกรายการ", done = true)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickDialog(
            initial = s.selected,
            onDismiss = { showDatePicker = false },
            onConfirm = { vm.select(it); showDatePicker = false },
        )
    }

    pickTimeFor?.let { task ->
        TimePickDialog(
            title = "เวลาที่${task.type.label()}จริง",
            initialMinutes = task.record?.checkedAtMillis?.let { millisMinutes(it) } ?: task.scheduledMinutes,
            onDismiss = { pickTimeFor = null },
            onConfirm = { vm.checkAtMinutes(task, it); pickTimeFor = null },
        )
    }

    manageTask?.let { task ->
        val close = { manageTask = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(task.type.label()) },
            text = {
                Column {
                    Text(
                        when {
                            task.skipped -> "สถานะ: ข้าม"
                            task.checked -> "บันทึกไว้เวลา ${task.record?.checkedAtMillis?.millisToHhmm()} น."
                            else -> "ยังไม่ได้เช็ค"
                        } + "\n(กำหนด ${task.scheduledMinutes.hhmm()} น.)",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (!task.done && task.date == s.today) {
                        DialogAction("เช็คตอนนี้") { vm.checkNow(task); close() }
                    }
                    if (!task.date.isAfter(s.today)) {
                        DialogAction(if (task.checked) "แก้เวลา" else "เช็คแล้ว (ระบุเวลา)") { pickTimeFor = task; close() }
                    }
                    if (!task.skipped) {
                        DialogAction("เปลี่ยนเป็น \"ข้าม\"") { vm.setSkipped(task, true); close() }
                    }
                    if (task.done) {
                        DialogAction("ยกเลิก (กลับเป็นยังไม่เช็ค)", color = MissRed) { vm.uncheck(task); close() }
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("ปิด") } },
        )
    }
}

private fun millisMinutes(millis: Long): Int {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    return t.hour * 60 + t.minute
}

@Composable
private fun DialogAction(label: String, color: Color = Brown, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = color, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TopBar(title: String, streak: Int, onCalendar: () -> Unit) {
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DarkBrown, modifier = Modifier.weight(1f))
            IconButton(onClick = onCalendar) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "เลือกวันที่", tint = DarkBrown, modifier = Modifier.size(28.dp))
            }
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .border(2.dp, DarkBrown, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).background(Color(0xFFFFE3B8), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = "วันติดต่อกัน", tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("$streak", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
            }
        }
    }
}

@Composable
private fun WeekStrip(s: TodayState, onSelect: (LocalDate) -> Unit, onSwipe: (Long) -> Unit) {
    var drag by remember { mutableStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > 120) onSwipe(if (drag < 0) 1 else -1)
                    },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
    ) {
        s.week.forEach { day ->
            val isToday = day.date == s.today
            val isSelected = day.date == s.selected
            Column(
                Modifier.weight(1f).clickable { onSelect(day.date) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.date.dayOfWeek.thaiShort(),
                    color = if (isToday) Rust else Muted,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(if (isToday) Peach else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
                val ring = when {
                    isSelected && isToday -> BorderStroke(2.dp, Rust)
                    day.allDone -> BorderStroke(2.dp, CheckGreen)
                    day.hasTasks -> BorderStroke(2.dp, Color(0xFFB7AFAB))
                    else -> null
                }
                val fill = when {
                    isSelected -> Brown
                    isToday -> Peach
                    else -> Color.Transparent
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .then(if (ring != null) Modifier.border(ring, CircleShape) else Modifier)
                        .padding(if (isSelected && isToday) 3.dp else 0.dp)
                        .background(fill, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${day.date.dayOfMonth}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isSelected -> Color.White
                            isToday -> Rust
                            else -> Muted
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5A504C))
}

@Composable
private fun TaskCard(tasks: List<DayTask>, s: TodayState, onTap: (DayTask) -> Unit, onLongPress: (DayTask) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val showShiftName = tasks.map { it.shiftId }.distinct().size > 1
            tasks.forEach { task -> TaskRow(task, s, showShiftName, onTap, onLongPress) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(task: DayTask, s: TodayState, showShiftName: Boolean, onTap: (DayTask) -> Unit, onLongPress: (DayTask) -> Unit) {
    val status = task.status(s.now)
    val overdue = !task.done && s.now.isAfter(task.dueAt)
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onTap(task) }, onLongClick = { onLongPress(task) })
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.scheduledMinutes.hhmm(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5A504C))
            if (task.scheduledMinutes >= 24 * 60) {
                Text("  (วันถัดไป)", fontSize = 13.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(
                type = task.type,
                tint = if (task.done) Color(0xFFC9C3C0) else Brown,
                background = if (task.done) Color(0xFFF3F1F0) else Peach,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.type.label(),
                    fontSize = 19.sp,
                    color = if (task.done) Color(0xFF9E9794) else DarkBrown,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                val sub = buildList {
                    if (showShiftName) add(task.shiftName)
                    if (task.skipped) add("ข้าม")
                    task.record?.takeIf { !it.skipped }?.let { r ->
                        val diff = r.diffMinutes()
                        val note = when {
                            !r.isOffTime() -> "ตรงเวลา"
                            diff > 0 -> "ช้า $diff นาที"
                            else -> "ก่อน ${-diff} นาที"
                        }
                        add("เช็คเมื่อ ${r.checkedAtMillis.millisToHhmm()} · $note")
                    }
                    if (status == DayStatus.MISSED) add("ไม่ได้เช็ค")
                    else if (overdue) add("เลยเวลาแล้ว กดเพื่อบันทึก")
                }
                if (sub.isNotEmpty()) {
                    Text(
                        sub.joinToString(" · "),
                        fontSize = 13.sp,
                        color = when {
                            task.record?.isOffTime() == true -> MissRed
                            status == DayStatus.MISSED || overdue -> MissRed
                            else -> Muted
                        },
                    )
                }
            }
            if (task.skipped) {
                Icon(Icons.Filled.SkipNext, contentDescription = "ข้าม", tint = Muted, modifier = Modifier.size(34.dp))
            } else if (task.done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "เช็คแล้ว", tint = CheckGreen, modifier = Modifier.size(34.dp))
            } else {
                Icon(Icons.Outlined.Circle, contentDescription = "ยังไม่เช็ค", tint = if (overdue) MissRed else Color(0xFFB7AFAB), modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, done: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(150.dp).background(if (done) Peach else Color(0xFFEFE9E5), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = if (done) Color(0xFFFFB300) else Color(0xFFCFC6C1), modifier = Modifier.size(100.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E3A39), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 17.sp, color = Color(0xFF6E6865), textAlign = TextAlign.Center)
    }
}
