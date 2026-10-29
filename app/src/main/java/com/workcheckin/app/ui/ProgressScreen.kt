package com.workcheckin.app.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.DayStatus
import com.workcheckin.app.data.DayTask
import com.workcheckin.app.data.diffMinutes
import com.workcheckin.app.data.hhmm
import com.workcheckin.app.data.isOffTime
import com.workcheckin.app.data.label
import com.workcheckin.app.data.millisToHhmm
import com.workcheckin.app.ui.theme.Brown
import com.workcheckin.app.ui.theme.CheckGreen
import com.workcheckin.app.ui.theme.DarkBrown
import com.workcheckin.app.ui.theme.LightGrey
import com.workcheckin.app.ui.theme.MissRed
import com.workcheckin.app.ui.theme.Muted
import com.workcheckin.app.ui.theme.Peach
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ProgressScreen(vm: ProgressViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ประวัติ", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DarkBrown, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val s = state ?: return@TextButton
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.shareText(s))
                context.startActivity(Intent.createChooser(send, "แชร์ประวัติ"))
            }) { Text("แชร์", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown) }
        }
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            indicator = { pos ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = DarkBrown)
            },
        ) {
            listOf("กราฟ", "รายการ").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = {
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (tab == i) DarkBrown else Muted)
                })
            }
        }
        val s = state ?: return
        if (tab == 0) ChartsTab(s) else ListTab(s)
    }
}

@Composable
private fun ChartsTab(s: ProgressState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SummaryCard(s.summary) }
        if (s.weekCharts.isEmpty()) {
            item { Text("ยังไม่มีตารางเวลา เพิ่มได้ที่แท็บ \"ตารางเวลา\"", color = Muted) }
        }
        val multipleShifts = s.weekCharts.map { it.shift.id }.distinct().size > 1
        items(s.weekCharts, key = { "${it.shift.id}-${it.type}" }) { chart ->
            WeekCard(chart, s, multipleShifts)
        }
        item { DiffChartCard("เข้างาน: เร็ว/ช้ากว่ากำหนด (14 วัน)", s.inDiffs, lateIsPositive = true) }
        item { DiffChartCard("ออกงาน: เร็ว/ช้ากว่ากำหนด (14 วัน)", s.outDiffs, lateIsPositive = false) }
    }
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(20.dp)) { content() } }
}

private data class StatDetail(val title: String, val tasks: List<DayTask>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryCard(sum: Summary) {
    var detail by remember { mutableStateOf<StatDetail?>(null) }
    WhiteCard {
        Text("30 วันล่าสุด", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
        Spacer(Modifier.height(4.dp))
        val pct = if (sum.total == 0) 0 else sum.done * 100 / sum.total
        Text("เช็คครบ $pct% (${sum.done}/${sum.total} รายการ)", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(
                Triple("ตรงเวลา", sum.onTimeTasks, CheckGreen),
                Triple("สาย/ออกก่อน", sum.offTimeTasks, Color(0xFFFF9800)),
                Triple("ข้าม", sum.skippedTasks, Color(0xFFB26A00)),
                Triple("ไม่ได้เช็ค", sum.missedTasks, MissRed),
            ).forEach { (label, tasks, color) ->
                Stat(label, tasks.size, color, Modifier.weight(1f)) { detail = StatDetail(label, tasks) }
            }
        }
        Text(
            "กดที่ตัวเลขเพื่อดูรายการ",
            fontSize = 12.sp, color = Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
    }

    detail?.let { d ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = Color.White) {
            Text(
                "${d.title} · 30 วันล่าสุด (${d.tasks.size} รายการ)",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (d.tasks.isEmpty()) {
                Text("ไม่มีรายการ", color = Muted, modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(d.tasks, key = { "${it.shiftId}-${it.epochDay}-${it.type}" }) { t ->
                        Text(
                            t.date.thaiLongDate(),
                            fontSize = 13.sp, color = Muted,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                        )
                        HistoryRow(t)
                        HorizontalDivider(color = Color(0xFFF3F0EE), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun WeekCard(chart: WeekChart, s: ProgressState, showShiftName: Boolean) {
    var expanded by rememberSaveable(chart.shift.id, chart.type) { mutableStateOf(false) }
    val canExpand = chart.days.size > 7
    WhiteCard {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = canExpand) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(chart.type.icon(), null, tint = DarkBrown, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(chart.type.label(), fontSize = 22.sp, color = DarkBrown, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (canExpand) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "ย่อ" else "ดูทั้งหมด",
                    tint = Muted,
                )
            }
        }
        Text(
            (if (showShiftName) "${chart.shift.name} · " else "") +
                if (expanded) "ทั้งหมด ${chart.days.size} วัน" else "7 วันล่าสุด",
            color = Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 36.dp, top = 2.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (!expanded) {
            Row(Modifier.fillMaxWidth()) {
                chart.last7.forEach { (date, status) ->
                    val isToday = date == s.today
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.thaiShort(),
                            fontSize = 15.sp,
                            color = if (isToday) Color.White else Muted,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .background(if (isToday) Brown else Color.Transparent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        StatusDot(status, highlight = isToday)
                    }
                }
            }
        } else {
            ExpandedHistory(chart, s)
        }
        if (canExpand) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (expanded) "ย่อ" else "ดูทั้งหมด", color = Brown)
            }
        }
    }
}

/** ตารางย้อนหลังทั้งหมด จัดเป็นแถวละสัปดาห์ (อา.-ส.) สัปดาห์ล่าสุดอยู่บน */
@Composable
private fun ExpandedHistory(chart: WeekChart, s: ProgressState) {
    val byDate = chart.days.toMap()
    val first = chart.days.first().first.weekStart()
    val weeks = generateSequence(s.today.weekStart()) { it.minusWeeks(1) }
        .takeWhile { !it.isBefore(first) }
        .toList()
    val counts = chart.days.groupingBy { it.second }.eachCount()
    Text(
        "เช็ค ${counts[DayStatus.DONE] ?: 0} · ข้าม ${counts[DayStatus.SKIPPED] ?: 0} · ไม่ได้เช็ค ${counts[DayStatus.MISSED] ?: 0}",
        fontSize = 14.sp, color = Muted,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        WeekOrder.forEach { day ->
            Text(
                day.thaiShort(), fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    weeks.forEach { weekStart ->
        Text(
            "${weekStart.thaiShortDate()} - ${weekStart.plusDays(6).thaiShortDate()}",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            (0..6L).forEach { i ->
                val date = weekStart.plusDays(i)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val status = byDate[date]
                    if (status != null) {
                        StatusDot(status, highlight = date == s.today, size = 36.dp)
                    } else {
                        Spacer(Modifier.size(36.dp))
                    }
                    Text("${date.dayOfMonth}", fontSize = 11.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: DayStatus, highlight: Boolean, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .then(if (highlight) Modifier.border(2.dp, Brown, CircleShape).padding(4.dp) else Modifier.padding(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            DayStatus.DONE -> Box(Modifier.fillMaxSize().background(CheckGreen, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            DayStatus.MISSED -> Box(Modifier.fillMaxSize().background(LightGrey, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = Color(0xFF6E6865), modifier = Modifier.size(22.dp))
            }
            DayStatus.SKIPPED -> Box(Modifier.fillMaxSize().background(Color(0xFFFFE8C2), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipNext, null, tint = Color(0xFFB26A00), modifier = Modifier.size(22.dp))
            }
            DayStatus.PENDING -> Box(Modifier.fillMaxSize().border(2.dp, LightGrey, CircleShape))
            DayStatus.OFF -> Box(Modifier.size(12.dp).background(LightGrey, CircleShape))
        }
    }
}

/** กราฟแท่งรอบเส้นศูนย์ (= เวลาที่กำหนด): สีเขียวคือดี สีส้มคือสาย/ออกก่อน */
@Composable
private fun DiffChartCard(title: String, points: List<DiffPoint>, lateIsPositive: Boolean) {
    WhiteCard {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
        Spacer(Modifier.height(4.dp))
        Text(
            if (lateIsPositive) "แท่งขึ้น = ช้ากว่ากำหนด, แท่งลง = มาก่อนเวลา" else "แท่งขึ้น = ออกหลังเวลา, แท่งลง = ออกก่อนเวลา",
            fontSize = 13.sp, color = Muted,
        )
        Spacer(Modifier.height(12.dp))
        val maxAbs = max(15L, points.mapNotNull { it.diff?.let(::abs) }.maxOrNull() ?: 0L).coerceAtMost(180L)
        Text("+$maxAbs นาที", fontSize = 11.sp, color = Muted)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / points.size
            val mid = size.height / 2
            drawLine(
                LightGrey, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            points.forEachIndexed { i, p ->
                val cx = slot * i + slot / 2
                val diff = p.diff
                if (diff == null) {
                    drawCircle(LightGrey, radius = 5f, center = Offset(cx, mid))
                    return@forEachIndexed
                }
                val clamped = diff.coerceIn(-maxAbs, maxAbs)
                val h = (abs(clamped).toFloat() / maxAbs) * (mid - 4f)
                val bad = if (lateIsPositive) diff > 0 else diff < 0
                val color = if (bad) Color(0xFFFF9800) else CheckGreen
                val barW = slot * 0.55f
                val top = if (clamped >= 0) mid - h else mid
                drawRoundRect(
                    color,
                    topLeft = Offset(cx - barW / 2, top),
                    size = Size(barW, max(h, 4f)),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Text("-$maxAbs นาที", fontSize = 11.sp, color = Muted)
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    "${p.date.dayOfMonth}",
                    fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ListTab(s: ProgressState) {
    if (s.history.isEmpty()) {
        Text("ยังไม่มีประวัติ", color = Muted, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        s.history.forEach { (date, tasks) ->
            item(key = date.toEpochDay()) {
                Text(
                    if (date == s.today) "วันนี้ · ${date.thaiShortDate()}" else date.thaiLongDate(),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5A504C),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            item(key = "c${date.toEpochDay()}") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        tasks.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(color = Color(0xFFF3F0EE), modifier = Modifier.padding(horizontal = 16.dp))
                            HistoryRow(t)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(t: DayTask) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TypeBadge(t.type, tint = Brown, background = Peach, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.type.label(), fontSize = 17.sp, color = DarkBrown)
            Text("${t.shiftName} · กำหนด ${t.scheduledMinutes.hhmm()}", fontSize = 13.sp, color = Muted)
        }
        val r = t.record
        Column(horizontalAlignment = Alignment.End) {
            if (r == null) {
                Text("ไม่ได้เช็ค", color = MissRed, fontWeight = FontWeight.SemiBold)
            } else if (r.skipped) {
                Text("ข้าม", color = Color(0xFFB26A00), fontWeight = FontWeight.SemiBold)
            } else {
                Text(r.checkedAtMillis.millisToHhmm(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkBrown)
                val diff = r.diffMinutes()
                val (label, color) = when {
                    !r.isOffTime() -> "ตรงเวลา" to CheckGreen
                    t.type == CheckType.IN -> "สาย $diff นาที" to Color(0xFFE67E00)
                    else -> "ออกก่อน ${-diff} นาที" to Color(0xFFE67E00)
                }
                Text(label, fontSize = 12.sp, color = color)
            }
        }
    }
}
