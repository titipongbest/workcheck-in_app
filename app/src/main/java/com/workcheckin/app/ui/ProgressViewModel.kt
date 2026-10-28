package com.workcheckin.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workcheckin.app.App
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.DayStatus
import com.workcheckin.app.data.DayTask
import com.workcheckin.app.data.Shift
import com.workcheckin.app.data.diffMinutes
import com.workcheckin.app.data.hhmm
import com.workcheckin.app.data.indexed
import com.workcheckin.app.data.isOffTime
import com.workcheckin.app.data.label
import com.workcheckin.app.data.millisToHhmm
import com.workcheckin.app.data.status
import com.workcheckin.app.data.tasksFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

/** สถานะรายวันของ shift/type เรียงจากเก่าไปใหม่ (วันสุดท้าย = วันนี้) */
data class WeekChart(val shift: Shift, val type: CheckType, val days: List<Pair<LocalDate, DayStatus>>) {
    val last7: List<Pair<LocalDate, DayStatus>> get() = days.takeLast(7)
}

data class Summary(val total: Int, val done: Int, val onTime: Int, val offTime: Int, val skipped: Int, val missed: Int)

/** จุดบนกราฟ: นาทีที่ช้า(+)/เร็ว(-) กว่ากำหนด หรือ null ถ้าไม่ได้เช็ค */
data class DiffPoint(val date: LocalDate, val diff: Long?)

data class ProgressState(
    val today: LocalDate,
    val weekCharts: List<WeekChart>,
    val summary: Summary,
    val inDiffs: List<DiffPoint>,
    val outDiffs: List<DiffPoint>,
    /** ประวัติล่าสุดก่อน จัดกลุ่มตามวัน */
    val history: List<Pair<LocalDate, List<DayTask>>>,
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository

    val state: StateFlow<ProgressState?> = combine(
        repo.shifts,
        repo.recordsSince(LocalDate.now().minusDays(HISTORY_DAYS)),
    ) { shifts, records ->
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val index = records.indexed()
        val tasksByDay = (0 until HISTORY_DAYS).map { i ->
            val d = today.minusDays(i)
            d to tasksFor(d, shifts, index)
        }

        val weekCharts = shifts.filter { it.enabled }.flatMap { shift ->
            CheckType.entries.map { type ->
                // ย้อนไปถึงต้นสัปดาห์ที่สร้างตารางนี้ (อย่างน้อย 7 วัน ไม่เกิน HISTORY_DAYS)
                val created = LocalDate.ofEpochDay(shift.createdEpochDay).weekStart()
                val span = (today.toEpochDay() - created.toEpochDay()).coerceIn(6L, HISTORY_DAYS - 1)
                val days = (span downTo 0L).map { i ->
                    val d = today.minusDays(i)
                    val task = tasksFor(d, listOf(shift), index).firstOrNull { it.type == type && it.shift?.isActiveOn(d) == true }
                    d to (task?.status(now) ?: DayStatus.OFF)
                }
                WeekChart(shift, type, days)
            }
        }

        val last30 = tasksByDay.take(30).flatMap { it.second }.filter { it.status(now) != DayStatus.PENDING }
        val summary = Summary(
            total = last30.size,
            done = last30.count { it.done },
            onTime = last30.count { it.checked && it.record?.isOffTime() == false },
            offTime = last30.count { it.record?.isOffTime() == true },
            skipped = last30.count { it.skipped },
            missed = last30.count { it.status(now) == DayStatus.MISSED },
        )

        fun diffs(type: CheckType) = (13 downTo 0L).map { i ->
            val d = today.minusDays(i)
            val record = records.filter { it.epochDay == d.toEpochDay() && it.type == type && !it.skipped }.minByOrNull { it.checkedAtMillis }
            DiffPoint(d, record?.diffMinutes())
        }

        val history = tasksByDay
            .map { (d, tasks) -> d to tasks.filter { it.status(now) != DayStatus.PENDING } }
            .filter { it.second.isNotEmpty() }

        ProgressState(today, weekCharts, summary, diffs(CheckType.IN), diffs(CheckType.OUT), history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shareText(s: ProgressState): String = buildString {
        appendLine("สรุปการสแกนนิ้วเข้าออกงาน (30 วันล่าสุด)")
        appendLine("ตรงเวลา ${s.summary.onTime} · สาย/ออกก่อน ${s.summary.offTime} · ข้าม ${s.summary.skipped} · ไม่ได้เช็ค ${s.summary.missed}")
        appendLine()
        s.history.take(30).forEach { (date, tasks) ->
            appendLine(date.thaiLongDate())
            tasks.forEach { t ->
                val actual = when {
                    t.skipped -> "ข้าม"
                    else -> t.record?.checkedAtMillis?.millisToHhmm() ?: "ไม่ได้เช็ค"
                }
                appendLine("  ${t.type.label()} (${t.shiftName}) กำหนด ${t.scheduledMinutes.hhmm()} · เช็ค $actual")
            }
        }
    }

    companion object {
        const val HISTORY_DAYS = 90L
    }
}
