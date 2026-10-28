package com.workcheckin.app.data

import android.content.Context
import com.workcheckin.app.notify.Notifications
import com.workcheckin.app.notify.ReminderScheduler
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
        return id
    }

    suspend fun deleteShift(shift: Shift) {
        db.shiftDao().delete(shift)
        ReminderScheduler.cancelShift(context, shift.id)
    }

    suspend fun check(shift: Shift, type: CheckType, epochDay: Long, atMillis: Long = System.currentTimeMillis()) {
        check(shift.id, shift.name, type, epochDay, shift.offsetMinutesFor(type), atMillis)
    }

    suspend fun check(shiftId: Long, shiftName: String, type: CheckType, epochDay: Long, scheduledMinutes: Int, atMillis: Long) {
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
            )
        )
        ReminderScheduler.cancelFollowUp(context, shiftId, type)
        Notifications.dismiss(context, shiftId, type)
    }

    suspend fun isChecked(shiftId: Long, epochDay: Long, type: CheckType): Boolean =
        db.recordDao().find(shiftId, epochDay, type) != null

    suspend fun uncheck(shiftId: Long, epochDay: Long, type: CheckType) {
        db.recordDao().delete(shiftId, epochDay, type)
    }
}
