package com.workcheckin.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.workcheckin.app.data.AppDatabase
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.Shift
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    const val EXTRA_SHIFT_ID = "shift_id"
    const val EXTRA_TYPE = "type"
    const val EXTRA_EPOCH_DAY = "epoch_day"
    const val EXTRA_FOLLOW_UP = "follow_up"
    private const val FOLLOW_UP_LIMIT_HOURS = 2L

    private fun requestCode(shiftId: Long, type: CheckType, followUp: Boolean): Int =
        (shiftId * 4 + type.ordinal * 2 + if (followUp) 1 else 0).toInt()

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    suspend fun rescheduleAll(context: Context) {
        val shifts = AppDatabase.get(context).shiftDao().getAll()
        shifts.forEach { shift ->
            if (shift.enabled) {
                CheckType.entries.forEach { scheduleNext(context, shift, it) }
            } else {
                cancelShift(context, shift.id)
            }
        }
    }

    /** ตั้งปลุกครั้งถัดไปของ shift/type หลังเวลา [after] */
    fun scheduleNext(context: Context, shift: Shift, type: CheckType, after: LocalDateTime = LocalDateTime.now()) {
        val next = nextOccurrence(shift, type, after) ?: return cancel(context, shift.id, type, followUp = false)
        setAlarm(context, shift.id, type, next.second, followUp = false, at = next.first)
    }

    /** เตือนซ้ำทุก followUpMinutes จนกว่าจะเช็ค แต่ไม่เกิน 2 ชม. หลังเวลาที่ตั้ง */
    fun scheduleFollowUp(context: Context, shift: Shift, type: CheckType, epochDay: Long) {
        if (shift.followUpMinutes <= 0) return
        val due = LocalDate.ofEpochDay(epochDay).atStartOfDay().plusMinutes(shift.offsetMinutesFor(type).toLong())
        if (LocalDateTime.now().isAfter(due.plusHours(FOLLOW_UP_LIMIT_HOURS))) return
        setAlarm(context, shift.id, type, epochDay, followUp = true, at = LocalDateTime.now().plusMinutes(shift.followUpMinutes.toLong()))
    }

    /** คืนค่า (เวลาที่ต้องเตือน, วันทำงาน) */
    fun nextOccurrence(shift: Shift, type: CheckType, after: LocalDateTime): Pair<LocalDateTime, LocalDate>? {
        if (!shift.enabled || shift.daysMask == 0) return null
        val start = after.toLocalDate().minusDays(1)
        for (i in 0..8L) {
            val day = start.plusDays(i)
            if (!shift.isActiveOn(day)) continue
            val at = day.atStartOfDay().plusMinutes(shift.offsetMinutesFor(type).toLong())
            if (at.isAfter(after)) return at to day
        }
        return null
    }

    private fun setAlarm(context: Context, shiftId: Long, type: CheckType, epochDay: LocalDate, followUp: Boolean, at: LocalDateTime) =
        setAlarm(context, shiftId, type, epochDay.toEpochDay(), followUp, at)

    private fun setAlarm(context: Context, shiftId: Long, type: CheckType, epochDay: Long, followUp: Boolean, at: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, shiftId, type, followUp, epochDay)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return
            }
        } catch (_: SecurityException) {
            // สิทธิ์ถูกเพิกถอนระหว่างทาง ใช้แบบไม่ตรงเวลาแทน
        }
        // ไม่ได้รับสิทธิ์ปลุกแบบตรงเวลา: ระบบอาจเลื่อนไปไม่กี่นาที
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun pendingIntent(context: Context, shiftId: Long, type: CheckType, followUp: Boolean, epochDay: Long = 0): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_SHIFT_ID, shiftId)
            .putExtra(EXTRA_TYPE, type.name)
            .putExtra(EXTRA_EPOCH_DAY, epochDay)
            .putExtra(EXTRA_FOLLOW_UP, followUp)
        return PendingIntent.getBroadcast(
            context,
            requestCode(shiftId, type, followUp),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancel(context: Context, shiftId: Long, type: CheckType, followUp: Boolean) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, shiftId, type, followUp))
    }

    fun cancelFollowUp(context: Context, shiftId: Long, type: CheckType) = cancel(context, shiftId, type, followUp = true)

    fun cancelShift(context: Context, shiftId: Long) {
        CheckType.entries.forEach { type ->
            cancel(context, shiftId, type, followUp = false)
            cancel(context, shiftId, type, followUp = true)
            Notifications.dismiss(context, shiftId, type)
        }
    }
}
