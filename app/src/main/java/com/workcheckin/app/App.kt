package com.workcheckin.app

import android.app.Application
import com.workcheckin.app.data.Repository
import com.workcheckin.app.notify.Notifications
import com.workcheckin.app.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    val repository by lazy { Repository(this) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        appScope.launch { ReminderScheduler.rescheduleAll(this@App) }
    }
}
