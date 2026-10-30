package com.workcheckin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.workcheckin.app.MainActivity
import com.workcheckin.app.R
import com.workcheckin.app.data.AppDatabase
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.Repository
import com.workcheckin.app.data.hhmm
import com.workcheckin.app.data.indexed
import com.workcheckin.app.data.label
import com.workcheckin.app.data.millisToHhmm
import com.workcheckin.app.data.tasksFor
import com.workcheckin.app.notify.ReminderScheduler
import com.workcheckin.app.ui.thaiShort
import com.workcheckin.app.ui.thaiShortDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        widgetScope.launch {
            try {
                TodayWidget.update(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TodayWidget.ACTION_CHECK) return super.onReceive(context, intent)
        val shiftId = intent.getLongExtra(ReminderScheduler.EXTRA_SHIFT_ID, -1)
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)?.let(CheckType::valueOf) ?: return
        val epochDay = intent.getLongExtra(ReminderScheduler.EXTRA_EPOCH_DAY, 0)
        val pending = goAsync()
        widgetScope.launch {
            try {
                val repo = Repository(context)
                val shift = repo.getShift(shiftId)
                if (shift != null && !repo.isChecked(shiftId, epochDay, type)) {
                    repo.check(shift, type, epochDay) // อัปเดต widget ให้เองหลังบันทึก
                } else {
                    TodayWidget.updateAll(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object TodayWidget {
    const val ACTION_CHECK = "com.workcheckin.app.WIDGET_CHECK"

    private val rowIds = listOf(
        Triple(R.id.row1, R.id.label1, R.id.time1) to R.id.icon1,
        Triple(R.id.row2, R.id.label2, R.id.time2) to R.id.icon2,
        Triple(R.id.row3, R.id.label3, R.id.time3) to R.id.icon3,
    )

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isNotEmpty()) update(context, manager, ids)
    }

    suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val db = AppDatabase.get(context)
        val today = LocalDate.now()
        val tasks = tasksFor(today, db.shiftDao().getAll(), db.recordDao().forDay(today.toEpochDay()).indexed())
        // ที่ยังไม่เช็คขึ้นก่อน แล้วตามด้วยที่เสร็จแล้ว (ถ้ายังมีที่ว่าง)
        val shown = (tasks.filter { !it.done } + tasks.filter { it.done }).take(rowIds.size)
        val doneCount = tasks.count { it.done }

        val views = RemoteViews(context.packageName, R.layout.widget_today)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.header, openApp)
        views.setOnClickPendingIntent(R.id.empty, openApp)
        views.setTextViewText(R.id.date, "${today.dayOfWeek.thaiShort()} ${today.thaiShortDate()}")
        views.setTextViewText(
            R.id.progress,
            when {
                tasks.isEmpty() -> ""
                doneCount == tasks.size -> "ครบแล้ว ✓"
                else -> "$doneCount/${tasks.size}"
            },
        )

        rowIds.forEachIndexed { i, (ids3, iconId) ->
            val (rowId, labelId, timeId) = ids3
            val task = shown.getOrNull(i)
            if (task == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(labelId, task.type.label())
            views.setTextColor(labelId, if (task.done) 0xFF9E9794.toInt() else 0xFF3B2622.toInt())
            val record = task.record
            views.setTextViewText(
                timeId,
                when {
                    record == null -> "${task.scheduledMinutes.hhmm()} น."
                    record.skipped -> "ข้าม"
                    else -> "เช็คแล้ว ${record.checkedAtMillis.millisToHhmm()}"
                },
            )
            views.setImageViewResource(
                iconId,
                when {
                    record == null -> R.drawable.widget_check_todo
                    record.skipped -> R.drawable.widget_check_skip
                    else -> R.drawable.widget_check_done
                },
            )
            val click = if (task.done || task.shift == null) {
                openApp
            } else {
                PendingIntent.getBroadcast(
                    context,
                    i + 1,
                    Intent(context, TodayWidgetProvider::class.java)
                        .setAction(ACTION_CHECK)
                        .putExtra(ReminderScheduler.EXTRA_SHIFT_ID, task.shiftId)
                        .putExtra(ReminderScheduler.EXTRA_TYPE, task.type.name)
                        .putExtra(ReminderScheduler.EXTRA_EPOCH_DAY, task.epochDay),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setOnClickPendingIntent(rowId, click)
        }

        views.setViewVisibility(R.id.empty, if (tasks.isEmpty()) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.empty, "วันนี้ไม่มีงาน")
        manager.updateAppWidget(ids, views)
    }
}
