package com.workcheckin.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** รายการที่ต้องเช็คในวันหนึ่ง (จากตารางเวลา) หรือรายการที่เคยเช็คไว้แล้ว */
data class DayTask(
    val shiftId: Long,
    val shiftName: String,
    val type: CheckType,
    val epochDay: Long,
    val scheduledMinutes: Int,
    /** ใช้เรียงลำดับ: กะดึกที่ออกหลังเที่ยงคืนจะต่อท้าย */
    val sortMinutes: Int,
    val record: CheckRecord?,
    val shift: Shift?,
) {
    val done: Boolean get() = record != null
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    /** เวลาที่ควรเช็คจริง (รวมกรณีข้ามวัน) */
    val dueAt: LocalDateTime
        get() = date.atStartOfDay().plusMinutes(sortMinutes.toLong())
}

enum class DayStatus { DONE, MISSED, PENDING, OFF }

typealias RecordKey = Triple<Long, Long, CheckType>

fun CheckRecord.key(): RecordKey = Triple(shiftId, epochDay, type)

fun List<CheckRecord>.indexed(): Map<RecordKey, CheckRecord> = associateBy { it.key() }

fun tasksFor(date: LocalDate, shifts: List<Shift>, records: Map<RecordKey, CheckRecord>): List<DayTask> {
    val day = date.toEpochDay()
    val scheduled = shifts.filter { it.isActiveOn(date) }.flatMap { shift ->
        CheckType.entries.map { type ->
            DayTask(
                shiftId = shift.id,
                shiftName = shift.name,
                type = type,
                epochDay = day,
                scheduledMinutes = shift.offsetMinutesFor(type),
                sortMinutes = shift.offsetMinutesFor(type),
                record = records[Triple(shift.id, day, type)],
                shift = shift,
            )
        }
    }
    val scheduledKeys = scheduled.map { Triple(it.shiftId, day, it.type) }.toSet()
    // รายการที่เคยเช็คไว้ แต่ตารางเวลาถูกลบ/เปลี่ยนวันไปแล้ว ก็ยังแสดงในประวัติ
    val orphans = records.values
        .filter { it.epochDay == day && it.key() !in scheduledKeys }
        .map { r ->
            DayTask(
                shiftId = r.shiftId,
                shiftName = r.shiftName,
                type = r.type,
                epochDay = day,
                scheduledMinutes = r.scheduledMinutes,
                sortMinutes = r.scheduledMinutes,
                record = r,
                shift = shifts.firstOrNull { it.id == r.shiftId },
            )
        }
    return (scheduled + orphans).sortedWith(compareBy({ it.sortMinutes }, { it.type }))
}

fun DayTask.status(now: LocalDateTime): DayStatus = when {
    done -> DayStatus.DONE
    now.isAfter(dueAt.plusHours(4)) || date.isBefore(now.toLocalDate().minusDays(1)) -> DayStatus.MISSED
    else -> DayStatus.PENDING
}

/** จำนวนวันติดต่อกันที่เช็คครบทุกรายการ (วันที่ไม่มีงานไม่ทำให้สถิติขาด) */
fun streak(today: LocalDate, shifts: List<Shift>, records: Map<RecordKey, CheckRecord>): Int {
    val todayTasks = tasksFor(today, shifts, records).filter { it.shift?.isActiveOn(today) == true }
    // วันนี้ยังเช็คไม่ครบก็ไม่ถือว่าขาด นับต่อจากเมื่อวาน
    var count = if (todayTasks.isNotEmpty() && todayTasks.all { it.done }) 1 else 0
    var date = today.minusDays(1)
    val earliest = shifts.minOfOrNull { it.createdEpochDay } ?: return count
    while (date.toEpochDay() >= earliest && count < 3650) {
        val tasks = tasksFor(date, shifts, records).filter { it.shift?.isActiveOn(date) == true }
        if (tasks.isNotEmpty()) {
            if (tasks.any { !it.done }) break
            count++
        }
        date = date.minusDays(1)
    }
    return count
}

/** ต่างจากเวลาที่ตั้งกี่นาที (+ = ช้ากว่ากำหนด) */
fun CheckRecord.diffMinutes(zone: ZoneId = ZoneId.systemDefault()): Long {
    val checked = Instant.ofEpochMilli(checkedAtMillis).atZone(zone).toLocalDateTime()
    val due = LocalDate.ofEpochDay(epochDay).atStartOfDay().plusMinutes(scheduledMinutes.toLong())
    return java.time.Duration.between(due, checked).toMinutes()
}

/** สายสำหรับขาเข้า / ออกก่อนเวลาสำหรับขาออก */
fun CheckRecord.isOffTime(): Boolean {
    val diff = diffMinutes()
    return if (type == CheckType.IN) diff > 0 else diff < 0
}

fun Int.hhmm(): String = "%02d:%02d".format((this / 60) % 24, this % 60)

fun Long.millisToHhmm(): String {
    val t = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

fun CheckType.label(): String = if (this == CheckType.IN) "สแกนนิ้วเข้า" else "สแกนนิ้วออก"
