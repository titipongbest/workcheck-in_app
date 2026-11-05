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
        DiaryBackup.markDirty(context)
        return id
    }

    suspend fun deleteShift(shift: Shift) {
        db.shiftDao().delete(shift)
        ReminderScheduler.cancelShift(context, shift.id)
        TodayWidget.updateAll(context)
        DiaryBackup.markDirty(context)
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
        DiaryBackup.markDirty(context)
    }

    suspend fun isChecked(shiftId: Long, epochDay: Long, type: CheckType): Boolean =
        db.recordDao().find(shiftId, epochDay, type) != null

    /** สลับสถานะ เช็คแล้ว <-> ข้าม โดยเก็บเวลาเดิมไว้ */
    suspend fun setSkipped(shiftId: Long, epochDay: Long, type: CheckType, skipped: Boolean) {
        val existing = db.recordDao().find(shiftId, epochDay, type) ?: return
        db.recordDao().upsert(existing.copy(skipped = skipped))
        TodayWidget.updateAll(context)
        DiaryBackup.markDirty(context)
    }

    suspend fun uncheck(shiftId: Long, epochDay: Long, type: CheckType) {
        db.recordDao().delete(shiftId, epochDay, type)
        TodayWidget.updateAll(context)
        DiaryBackup.markDirty(context)
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

    suspend fun snapshot(): DiaryBackup.Snapshot =
        DiaryBackup.Snapshot(db.workLogDao().getAll(), db.shiftDao().getAll(), db.recordDao().getAll())

    data class ImportResult(val workLogs: Int, val skippedLogs: Int, val shifts: Int, val records: Int)

    /**
     * รวมข้อมูลที่นำเข้ากับของเดิม
     * - บันทึกงาน: วันที่ซ้ำเก็บรายการที่แก้ไขล่าสุด
     * - ตารางเวลา: ถ้ามีตารางชื่อ/เวลา/วันเดียวกันอยู่แล้วใช้ของเดิม ไม่งั้นเพิ่มใหม่
     * - ประวัติเช็คอิน: ถ้ากะ/วัน/ประเภทเดียวกันมีอยู่แล้วเก็บของเดิม
     */
    suspend fun importSnapshot(data: DiaryBackup.Snapshot): ImportResult {
        var applied = 0
        var skipped = 0
        var newShifts = 0
        var newRecords = 0
        db.withTransaction {
            for (log in data.workLogs) {
                val existing = db.workLogDao().forDay(log.epochDay)
                when {
                    existing == null -> { db.workLogDao().upsert(log.copy(id = 0)); applied++ }
                    existing.text == log.text || existing.updatedAtMillis >= log.updatedAtMillis -> skipped++
                    else -> { db.workLogDao().upsert(log.copy(id = existing.id)); applied++ }
                }
            }

            val current = db.shiftDao().getAll()
            val idMap = HashMap<Long, Long>()
            for (shift in data.shifts) {
                val same = current.firstOrNull {
                    it.name == shift.name && it.inMinutes == shift.inMinutes &&
                        it.outMinutes == shift.outMinutes && it.daysMask == shift.daysMask
                }
                idMap[shift.id] = same?.id ?: db.shiftDao().insert(shift.copy(id = 0)).also { newShifts++ }
            }
            for (r in data.records) {
                // ประวัติของตารางที่ถูกลบไปแล้วใช้ id ติดลบ กันไปชนกับตารางอื่นในเครื่องนี้
                val shiftId = idMap[r.shiftId] ?: if (r.shiftId < 0) r.shiftId else -r.shiftId
                if (db.recordDao().insertIgnore(r.copy(id = 0, shiftId = shiftId)) != -1L) newRecords++
            }
        }
        if (newShifts > 0) ReminderScheduler.rescheduleAll(context)
        if (newShifts > 0 || newRecords > 0) TodayWidget.updateAll(context)
        if (applied > 0 || newShifts > 0 || newRecords > 0) DiaryBackup.markDirty(context)
        return ImportResult(applied, skipped, newShifts, newRecords)
    }
}
