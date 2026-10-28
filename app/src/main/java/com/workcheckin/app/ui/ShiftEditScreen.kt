package com.workcheckin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workcheckin.app.data.CheckType
import com.workcheckin.app.data.hhmm
import com.workcheckin.app.ui.theme.Background
import com.workcheckin.app.ui.theme.Brown
import com.workcheckin.app.ui.theme.CheckGreen
import com.workcheckin.app.ui.theme.DarkBrown
import com.workcheckin.app.ui.theme.MissRed
import com.workcheckin.app.ui.theme.Muted
import com.workcheckin.app.ui.theme.Peach

private val followUpOptions = listOf(0, 5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftEditScreen(shiftId: Long, onDone: () -> Unit, vm: ShiftEditViewModel = viewModel()) {
    LaunchedEffect(shiftId) { vm.load(shiftId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val f = form ?: return

    var editingTime by remember { mutableStateOf<CheckType?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(if (f.id == 0L) "เพิ่มตารางเวลา" else "แก้ไขตารางเวลา", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") }
                },
                actions = {
                    if (f.id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "ลบ", tint = MissRed) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            Button(
                onClick = { vm.save(onDone) },
                enabled = f.daysMask != 0,
                colors = ButtonDefaults.buttonColors(containerColor = Brown),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
            ) { Text("บันทึก", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("ชื่อ (เช่น งานประจำ, กะเช้า)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            WhiteCard {
                CheckType.entries.forEachIndexed { i, type ->
                    if (i > 0) HorizontalDivider(color = Color(0xFFF0ECEA))
                    Row(
                        Modifier.fillMaxWidth().clickable { editingTime = type }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypeBadge(type, tint = Brown, background = Peach)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (type == CheckType.IN) "เวลาเข้างาน" else "เวลาออกงาน",
                            fontSize = 18.sp, color = DarkBrown, modifier = Modifier.weight(1f),
                        )
                        val minutes = if (type == CheckType.IN) f.inMinutes else f.outMinutes
                        Text(minutes.hhmm(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Brown)
                    }
                }
                if (f.outMinutes <= f.inMinutes) {
                    Text(
                        "เวลาออกงานน้อยกว่าเวลาเข้า จะนับเป็นออกงานวันถัดไป (กะดึก)",
                        fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            WhiteCard {
                Column(Modifier.padding(16.dp)) {
                    Text("วันทำงาน", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekOrder.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            val selected = f.daysMask and bit != 0
                            FilterChip(
                                selected = selected,
                                onClick = { vm.update { it.copy(daysMask = it.daysMask xor bit) } },
                                label = { Text(day.thaiShort(), fontSize = 16.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Brown,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.update { it.copy(daysMask = 0b0011111) } }) { Text("จ.-ศ.") }
                        TextButton(onClick = { vm.update { it.copy(daysMask = 0b0111111) } }) { Text("จ.-ส.") }
                        TextButton(onClick = { vm.update { it.copy(daysMask = 0b1111111) } }) { Text("ทุกวัน") }
                    }
                    if (f.daysMask == 0) Text("เลือกอย่างน้อย 1 วัน", color = MissRed, fontSize = 13.sp)
                }
            }

            WhiteCard {
                Column(Modifier.padding(16.dp)) {
                    Text("เตือนซ้ำถ้ายังไม่ได้เช็ค", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        followUpOptions.forEach { m ->
                            FilterChip(
                                selected = f.followUpMinutes == m,
                                onClick = { vm.update { it.copy(followUpMinutes = m) } },
                                label = { Text(if (m == 0) "ไม่เตือนซ้ำ" else "ทุก $m นาที") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Brown,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("เปิดใช้งาน/แจ้งเตือน", fontSize = 16.sp, color = DarkBrown, modifier = Modifier.weight(1f))
                        Switch(
                            checked = f.enabled,
                            onCheckedChange = { v -> vm.update { it.copy(enabled = v) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = CheckGreen),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    editingTime?.let { type ->
        TimePickDialog(
            title = if (type == CheckType.IN) "เวลาเข้างาน" else "เวลาออกงาน",
            initialMinutes = if (type == CheckType.IN) f.inMinutes else f.outMinutes,
            onDismiss = { editingTime = null },
            onConfirm = { m ->
                vm.update { if (type == CheckType.IN) it.copy(inMinutes = m) else it.copy(outMinutes = m) }
                editingTime = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("ลบตารางเวลานี้?") },
            text = { Text("ประวัติที่บันทึกไว้แล้วจะยังอยู่ แต่จะไม่มีการแจ้งเตือนอีก") },
            confirmButton = { TextButton(onClick = { vm.delete(onDone) }) { Text("ลบ", color = MissRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
private fun WhiteCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}
