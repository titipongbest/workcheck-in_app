package com.workcheckin.app.data

import android.content.Context
import com.workcheckin.app.notify.Notifications
import com.workcheckin.app.notify.ReminderScheduler
import com.workcheckin.app.widget.TodayWidget
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class Repository(private val context: Context) {
    private val db = AppDatabase.get(context)

    val shifts: Flow<List<Shift>> = db.shiftDao().observeAll()

    fun recordsSince(fromDay: LocalDate): Flow<List<CheckRecord>> =
        db.recordDao().observeSince(fromDay.toEpochDay())

    suspend fun getShift(id: Long): Shift? = db.shiftDao().get(id)

    suspend fun saveShift(shift: Shift): Long {
        val id = if (shift.id == 0L) {
            db.shiftDao().insert(shift)
        } else {
            db.shiftDao().update(shift)
            shift.id
        }
        ReminderScheduler.rescheduleAll(context)
        TodayWidget.updateAll(context)
        return id
    }

    suspend fun deleteShift(shift: Shift) {
        db.shiftDao().delete(shift)
        ReminderScheduler.cancelShift(context, shift.id)
        TodayWidget.updateAll(context)
    }

    suspend fun check(shift: Shift, type: CheckType, epochDay: Long, atMillis: Long = System.currentTimeMillis(), skipped: Boolean = false) {
        check(shift.id, shift.name, type, epochDay, shift.offsetMinutesFor(type), atMillis, skipped)
    }

    suspend fun check(
        shiftId: Long,
        shiftName: String,
        type: CheckType,
        epochDay: Long,
        scheduledMinutes: Int,
        atMillis: Long,
        skipped: Boolean = false,
    ) {
        val existing = db.recordDao().find(shiftId, epochDay, type)
        db.recordDao().upsert(
            CheckRecord(
                id = existing?.id ?: 0,
                shiftId = shiftId,
                shiftName = shiftName,
                type = type,
                epochDay = epochDay,
                scheduledMinutes = scheduledMinutes,
                checkedAtMillis = atMillis,
                skipped = skipped,
            )
        )
        ReminderScheduler.cancelFollowUp(context, shiftId, type)
        Notifications.dismiss(context, shiftId, type)
        TodayWidget.updateAll(context)
    }

    suspend fun isChecked(shiftId: Long, epochDay: Long, type: CheckType): Boolean =
        db.recordDao().find(shiftId, epochDay, type) != null

    /** สลับสถานะ เช็คแล้ว <-> ข้าม โดยเก็บเวลาเดิมไว้ */
    suspend fun setSkipped(shiftId: Long, epochDay: Long, type: CheckType, skipped: Boolean) {
        val existing = db.recordDao().find(shiftId, epochDay, type) ?: return
        db.recordDao().upsert(existing.copy(skipped = skipped))
        TodayWidget.updateAll(context)
    }

    suspend fun uncheck(shiftId: Long, epochDay: Long, type: CheckType) {
        db.recordDao().delete(shiftId, epochDay, type)
        TodayWidget.updateAll(context)
    }

    // ---- บันทึกการทำงาน ----

    fun workLogs(from: LocalDate, to: LocalDate): Flow<List<WorkLog>> =
        db.workLogDao().observeRange(from.toEpochDay(), to.toEpochDay())

    fun observeWorkLog(id: Long): Flow<WorkLog?> = db.workLogDao().observe(id)

    suspend fun getWorkLog(id: Long): WorkLog? = db.workLogDao().get(id)

    suspend fun workLogForDay(date: LocalDate): WorkLog? = db.workLogDao().forDay(date.toEpochDay())

    suspend fun allWorkLogs(): List<WorkLog> = db.workLogDao().getAll()

    /** บันทึก (1 รายการต่อวัน) ถ้า replacesId != 0 จะลบรายการเดิมที่ถูกย้ายวันออก */
    suspend fun saveWorkLog(log: WorkLog, replacesId: Long = 0) {
        db.withTransaction {
            if (replacesId != 0L && replacesId != log.id) db.workLogDao().delete(replacesId)
            db.workLogDao().upsert(log.copy(updatedAtMillis = System.currentTimeMillis()))
        }
        DiaryBackup.markDirty(context)
    }

    suspend fun deleteWorkLog(id: Long) {
        db.workLogDao().delete(id)
        DiaryBackup.markDirty(context)
    }

    /** รวมข้อมูลที่นำเข้า: วันที่ซ้ำจะเก็บรายการที่แก้ไขล่าสุด คืนค่า (เพิ่ม/อัปเดต, ข้าม) */
    suspend fun importWorkLogs(logs: List<WorkLog>): Pair<Int, Int> {
        var applied = 0
        var skipped = 0
        db.withTransaction {
            for (log in logs) {
                val existing = db.workLogDao().forDay(log.epochDay)
                when {
                    existing == null -> { db.workLogDao().upsert(log.copy(id = 0)); applied++ }
                    existing.text == log.text || existing.updatedAtMillis >= log.updatedAtMillis -> skipped++
                    else -> { db.workLogDao().upsert(log.copy(id = existing.id)); applied++ }
                }
            }
        }
        if (applied > 0) DiaryBackup.markDirty(context)
        return applied to skipped
    }
}
