package com.workcheckin.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.workcheckin.app.notify.Notifications
import com.workcheckin.app.notify.ReminderScheduler
import com.workcheckin.app.ui.theme.Brown
import com.workcheckin.app.ui.theme.DarkBrown
import com.workcheckin.app.ui.theme.MissRed
import com.workcheckin.app.ui.theme.Muted

private data class Health(
    val canNotify: Boolean,
    val channelOn: Boolean,
    val canExact: Boolean,
    val batteryOk: Boolean,
) {
    val allOk get() = canNotify && channelOn && canExact && batteryOk
}

private fun Context.health() = Health(
    canNotify = Notifications.canPost(this),
    channelOn = !Notifications.channelBlocked(this),
    canExact = ReminderScheduler.canScheduleExact(this),
    batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
)

/** ตรวจสถานะใหม่ทุกครั้งที่กลับเข้าแอป (เช่น หลังไปเปิดสิทธิ์ในหน้าตั้งค่า) */
@Composable
private fun rememberHealth(): Health {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    return remember(refresh) { context.health() }
}

/** การ์ดเตือนเมื่อมีอะไรทำให้การแจ้งเตือนไม่เด้ง พร้อมปุ่มไปแก้ */
@SuppressLint("BatteryLife")
@Composable
fun ReminderWarnings() {
    val context = LocalContext.current
    val h = rememberHealth()
    if (h.allOk) return

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEDEA)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsOff, null, tint = MissRed)
                Spacer(Modifier.width(8.dp))
                Text("การแจ้งเตือนอาจไม่เด้ง แก้ตามนี้", fontWeight = FontWeight.SemiBold, color = MissRed)
            }
            if (!h.canNotify || !h.channelOn) {
                Text("• แอปยังไม่ได้รับอนุญาตให้แสดงการแจ้งเตือน หรือช่อง \"เตือนสแกนนิ้ว\" ถูกปิด/ตั้งเป็นแบบเงียบ", fontSize = 14.sp, color = DarkBrown)
                TextButton(onClick = {
                    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("เปิดการแจ้งเตือน") }
            }
            if (!h.canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text("• ยังไม่ได้อนุญาต \"การปลุกและการช่วยเตือน\"", fontSize = 14.sp, color = DarkBrown)
                TextButton(onClick = {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text("อนุญาตให้เตือนตรงเวลา") }
            }
            if (!h.batteryOk) {
                Text("• ระบบประหยัดแบตอาจหยุดแอปตอนปิดหน้าจอ ให้ตั้งเป็น \"ไม่จำกัด\"", fontSize = 14.sp, color = DarkBrown)
                TextButton(onClick = {
                    open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }) { Text("ปิดการประหยัดแบตให้แอปนี้") }
            }
        }
    }
}

/** ปุ่มทดสอบ: เด้งทันที 1 ครั้ง และตั้งเวลาเด้งอีกครั้งใน 1 นาที (ทดสอบตอนปิดจอได้) */
@Composable
fun TestReminderCard() {
    val context = LocalContext.current
    var sent by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = Brown)
                Spacer(Modifier.width(8.dp))
                Text("ทดสอบแจ้งเตือน", fontWeight = FontWeight.SemiBold, color = DarkBrown, fontSize = 16.sp)
            }
            Text(
                if (sent) "ควรเห็นแจ้งเตือนทันที 1 ครั้ง และอีกครั้งใน 1 นาที ลองปิดหน้าจอรอได้ ถ้าครั้งที่ 2 ไม่มา แปลว่าระบบมือถือบล็อกการตั้งเวลา"
                else "กดเพื่อเช็คว่าแจ้งเตือนเด้งจริง (เด้งทันที และอีกครั้งใน 1 นาที)",
                fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = {
                Notifications.showTest(context, "ถ้าเห็นข้อความนี้ แอปแสดงการแจ้งเตือนได้แล้ว อีก 1 นาทีจะเด้งอีกครั้ง")
                ReminderScheduler.scheduleTest(context, 60)
                sent = true
            }) { Text("ส่งแจ้งเตือนทดสอบ") }
        }
    }
}
