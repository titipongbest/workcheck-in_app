package com.workcheckin.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate

enum class CheckType { IN, OUT }

/** ตารางเวลาทำงาน 1 รายการ = สแกนเข้า + สแกนออก ในวันที่เลือก */
@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** นาทีนับจากเที่ยงคืน */
    val inMinutes: Int,
    val outMinutes: Int,
    /** bit ที่ (DayOfWeek.value - 1) = วันนั้นต้องทำงาน (จันทร์ = bit 0) */
    val daysMask: Int,
    val enabled: Boolean = true,
    /** เตือนซ้ำหลังเวลาที่ตั้งกี่นาทีถ้ายังไม่เช็ค (0 = ไม่เตือนซ้ำ) */
    val followUpMinutes: Int = 10,
    /** วันที่สร้าง ใช้ไม่ให้นับวันก่อนหน้านั้นเป็น "ขาด" */
    val createdEpochDay: Long = LocalDate.now().toEpochDay(),
) {
    fun isActiveOn(date: LocalDate): Boolean =
        enabled && date.toEpochDay() >= createdEpochDay && worksOn(date.dayOfWeek)

    fun worksOn(day: DayOfWeek): Boolean = daysMask and (1 shl (day.value - 1)) != 0

    fun minutesFor(type: CheckType): Int = if (type == CheckType.IN) inMinutes else outMinutes

    /** เวลาออกงานข้ามเที่ยงคืน (กะดึก) ให้นับต่อจากวันทำงาน */
    fun offsetMinutesFor(type: CheckType): Int =
        if (type == CheckType.OUT && outMinutes <= inMinutes) outMinutes + 24 * 60 else minutesFor(type)
}

@Entity(
    tableName = "check_records",
    indices = [Index(value = ["shiftId", "epochDay", "type"], unique = true)],
)
data class CheckRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val shiftName: String,
    val type: CheckType,
    /** วันทำงานที่รายการนี้สังกัด */
    val epochDay: Long,
    /** นาทีนับจากเที่ยงคืนของ epochDay (กะดึกอาจเกิน 24 ชม.) */
    val scheduledMinutes: Int,
    val checkedAtMillis: Long,
    /** ผู้ใช้กด "ข้าม" (เช่น ลา/หยุด) แทนการสแกน */
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
)
