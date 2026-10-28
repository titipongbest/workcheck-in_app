package com.workcheckin.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.workcheckin.app.MainActivity
import com.workcheckin.app.R
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.Shift
import com.workcheckin.app.data.hhmm
import com.workcheckin.app.data.label

object Notifications {
    const val CHANNEL_ID = "checkin_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "เตือนสแกนนิ้ว",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "แจ้งเตือนเมื่อถึงเวลาสแกนนิ้วเข้า/ออกงาน"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificationId(shiftId: Long, type: CheckType): Int = (shiftId * 2 + type.ordinal).toInt()

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, shift: Shift, type: CheckType, epochDay: Long, followUp: Boolean) {
        if (!canPost(context)) return
        val id = notificationId(shift.id, type)

        val openApp = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val checkNow = PendingIntent.getBroadcast(
            context, id,
            Intent(context, CheckActionReceiver::class.java)
                .putExtra(ReminderScheduler.EXTRA_SHIFT_ID, shift.id)
                .putExtra(ReminderScheduler.EXTRA_TYPE, type.name)
                .putExtra(ReminderScheduler.EXTRA_EPOCH_DAY, epochDay),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (followUp) "ยังไม่ได้${type.label()}!" else "ถึงเวลา${type.label()}แล้ว"
        val text = "${shift.name} · เวลา ${shift.minutesFor(type).hhmm()} น. สแกนแล้วกด \"บันทึกแล้ว\""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fingerprint)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, "บันทึกแล้ว", checkNow)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // ผู้ใช้ปิดสิทธิ์แจ้งเตือนระหว่างทาง
        }
    }

    fun dismiss(context: Context, shiftId: Long, type: CheckType) {
        NotificationManagerCompat.from(context).cancel(notificationId(shiftId, type))
    }
}
