package com.workcheckin.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workcheckin.app.App
import com.workcheckin.app.data.CheckRecord
import com.workcheckin.app.data.DayTask
import com.workcheckin.app.data.RecordKey
import com.workcheckin.app.data.Shift
import com.workcheckin.app.data.indexed
import com.workcheckin.app.data.streak
import com.workcheckin.app.data.tasksFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class WeekDay(val date: LocalDate, val hasTasks: Boolean, val allDone: Boolean)

data class TodayState(
    val now: LocalDateTime,
    val selected: LocalDate,
    val tasks: List<DayTask>,
    val streak: Int,
    val hasShifts: Boolean,
    private val shifts: List<Shift>,
    private val index: Map<RecordKey, CheckRecord>,
) {
    val today: LocalDate get() = now.toLocalDate()

    /** ข้อมูลของสัปดาห์ใดก็ได้ ใช้ตอนเลื่อนแถบสัปดาห์ไปมา */
    fun weekDays(start: LocalDate): List<WeekDay> = (0..6L).map { i ->
        val d = start.plusDays(i)
        val t = tasksFor(d, shifts, index)
        WeekDay(d, t.isNotEmpty(), t.isNotEmpty() && t.all { it.done })
    }
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    private val selected = MutableStateFlow(LocalDate.now())

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    val state: StateFlow<TodayState?> = combine(
        selected,
        repo.shifts,
        repo.recordsSince(LocalDate.now().minusDays(400)),
        clock,
    ) { sel, shifts, records, now ->
        val index = records.indexed()
        TodayState(
            now = now,
            selected = sel,
            tasks = tasksFor(sel, shifts, index),
            streak = streak(now.toLocalDate(), shifts, index),
            hasShifts = shifts.isNotEmpty(),
            shifts = shifts,
            index = index,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(date: LocalDate) {
        selected.value = date
    }

    /** บันทึกเวลาปัจจุบัน */
    fun checkNow(task: DayTask) = checkAt(task, System.currentTimeMillis())

    /** บันทึกเวลาที่ผู้ใช้เลือกเอง (ใช้กับวันที่ผ่านมาแล้ว หรือแก้เวลา) */
    fun checkAtMinutes(task: DayTask, minutes: Int) {
        // กะดึก: เวลาขาออกที่เลือกเป็นช่วงเช้าให้นับเป็นวันถัดไป
        val dayOffset = if (task.scheduledMinutes >= 24 * 60 && minutes < 12 * 60) 1L else 0L
        val at = task.date.plusDays(dayOffset).atStartOfDay().plusMinutes(minutes.toLong())
        checkAt(task, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    private fun checkAt(task: DayTask, millis: Long) {
        viewModelScope.launch {
            repo.check(task.shiftId, task.shiftName, task.type, task.epochDay, task.scheduledMinutes, millis)
        }
    }

    /** ข้ามรายการนี้ (เช่น ลา/หยุด) หรือเปลี่ยนรายการที่เช็คแล้วเป็นข้าม และกลับกัน */
    fun setSkipped(task: DayTask, skipped: Boolean) {
        viewModelScope.launch {
            if (task.record == null) {
                repo.check(task.shiftId, task.shiftName, task.type, task.epochDay, task.scheduledMinutes, System.currentTimeMillis(), skipped = true)
            } else {
                repo.setSkipped(task.shiftId, task.epochDay, task.type, skipped)
            }
        }
    }

    fun uncheck(task: DayTask) {
        viewModelScope.launch { repo.uncheck(task.shiftId, task.epochDay, task.type) }
    }
}
