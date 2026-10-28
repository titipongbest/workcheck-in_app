package com.workcheckin.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.workcheckin.app.data.AppDatabase
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** ถึงเวลาที่ตั้งไว้: แจ้งเตือนถ้ายังไม่ได้เช็ค แล้วตั้งปลุกรอบถัดไป */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shiftId = intent.getLongExtra(ReminderScheduler.EXTRA_SHIFT_ID, -1)
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)?.let(CheckType::valueOf) ?: return
        val epochDay = intent.getLongExtra(ReminderScheduler.EXTRA_EPOCH_DAY, 0)
        val followUp = intent.getBooleanExtra(ReminderScheduler.EXTRA_FOLLOW_UP, false)

        runAsync {
            val shift = AppDatabase.get(context).shiftDao().get(shiftId) ?: return@runAsync
            if (!followUp) {
                ReminderScheduler.scheduleNext(context, shift, type, LocalDateTime.now().plusMinutes(1))
            }
            if (!shift.enabled) return@runAsync
            if (Repository(context).isChecked(shiftId, epochDay, type)) return@runAsync
            Notifications.showReminder(context, shift, type, epochDay, followUp)
            ReminderScheduler.scheduleFollowUp(context, shift, type, epochDay)
        }
    }
}

/** ปุ่มบนการแจ้งเตือน: เช็คอิน (บันทึกเวลาปัจจุบัน) / ข้าม / เตือนอีกครั้ง */
class CheckActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shiftId = intent.getLongExtra(ReminderScheduler.EXTRA_SHIFT_ID, -1)
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)?.let(CheckType::valueOf) ?: return
        val epochDay = intent.getLongExtra(ReminderScheduler.EXTRA_EPOCH_DAY, 0)

        runAsync {
            val repo = Repository(context)
            val shift = repo.getShift(shiftId)
            if (shift == null) {
                Notifications.dismiss(context, shiftId, type)
                return@runAsync
            }
            when (intent.action) {
                ACTION_SKIP -> repo.check(shift, type, epochDay, skipped = true)
                ACTION_SNOOZE -> {
                    Notifications.dismiss(context, shiftId, type)
                    ReminderScheduler.snooze(context, shiftId, type, epochDay, Notifications.SNOOZE_MINUTES)
                }
                else -> repo.check(shift, type, epochDay)
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.workcheckin.app.CHECK"
        const val ACTION_SKIP = "com.workcheckin.app.SKIP"
        const val ACTION_SNOOZE = "com.workcheckin.app.SNOOZE"
    }
}

/** เปิดเครื่องใหม่/เปลี่ยนเวลา: AlarmManager ล้างปลุกทั้งหมด ต้องตั้งใหม่ */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { ReminderScheduler.rescheduleAll(context) }
    }
}
