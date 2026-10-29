package com.workcheckin.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
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
    const val SNOOZE_MINUTES = 30L
    private const val TEST_ID = Int.MAX_VALUE

    private fun CheckType.actionLabel() = if (this == CheckType.IN) "เช็คอิน" else "เช็คเอาท์"

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
        fun action(name: String) = PendingIntent.getBroadcast(
            context, id,
            Intent(context, CheckActionReceiver::class.java)
                .setAction(name)
                .putExtra(ReminderScheduler.EXTRA_SHIFT_ID, shift.id)
                .putExtra(ReminderScheduler.EXTRA_TYPE, type.name)
                .putExtra(ReminderScheduler.EXTRA_EPOCH_DAY, epochDay),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (followUp) "ยังไม่ได้${type.label()}!" else "ถึงเวลา${type.label()}แล้ว"
        val time = shift.minutesFor(type).hhmm()
        val text = "${shift.name} · เวลา $time น. สแกนแล้วกด \"${type.actionLabel()}\""

        val isIn = type == CheckType.IN
        // หน้าตาแจ้งเตือนออกแบบเอง ให้ปุ่มโชว์ตลอดแม้ในแบบย่อ
        fun views(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setImageViewResource(R.id.badge, if (isIn) R.drawable.notif_badge_in else R.drawable.notif_badge_out)
            setTextViewText(R.id.btn_check, type.actionLabel())
            setInt(R.id.btn_check, "setBackgroundResource", if (isIn) R.drawable.notif_btn_in else R.drawable.notif_btn_out)
            setOnClickPendingIntent(R.id.btn_check, action(CheckActionReceiver.ACTION_CHECK))
            setOnClickPendingIntent(R.id.btn_skip, action(CheckActionReceiver.ACTION_SKIP))
            setOnClickPendingIntent(R.id.btn_snooze, action(CheckActionReceiver.ACTION_SNOOZE))
        }
        val collapsed = views(R.layout.notif_collapsed).apply {
            setTextViewText(R.id.title, "$title · $time")
            setTextViewText(R.id.btn_snooze, "อีก $SNOOZE_MINUTES นาที")
        }
        val expanded = views(R.layout.notif_expanded).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.subtitle, shift.name)
            setTextViewText(R.id.time, time)
            setTextViewText(R.id.btn_snooze, "เตือนอีก $SNOOZE_MINUTES นาที")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fingerprint)
            .setColor(if (isIn) 0xFF6B4237.toInt() else 0xFFC25E00.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomHeadsUpContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // ผู้ใช้ปิดสิทธิ์แจ้งเตือนระหว่างทาง
        }
    }

    fun showTest(context: Context, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fingerprint)
            .setContentTitle("ทดสอบแจ้งเตือน")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TEST_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    /** ช่องแจ้งเตือนถูกผู้ใช้ปิดหรือลดระดับจนไม่เด้ง */
    fun channelBlocked(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance < NotificationManager.IMPORTANCE_DEFAULT
    }

    fun dismiss(context: Context, shiftId: Long, type: CheckType) {
        NotificationManagerCompat.from(context).cancel(notificationId(shiftId, type))
    }
}
