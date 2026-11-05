package com.workcheckin.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workcheckin.app.data.DiaryBackup
import com.workcheckin.app.data.WorkLog
import com.workcheckin.app.ui.theme.Background
import com.workcheckin.app.ui.theme.Brown
import com.workcheckin.app.ui.theme.CheckGreen
import com.workcheckin.app.ui.theme.DarkBrown
import com.workcheckin.app.ui.theme.MissRed
import com.workcheckin.app.ui.theme.Muted
import com.workcheckin.app.ui.theme.Peach
import com.workcheckin.app.ui.theme.Rust
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val monthHeader = DateTimeFormatter.ofPattern("MMMM yyyy", ThaiLocale)
private val shortDateYear = DateTimeFormatter.ofPattern("d MMM yyyy", ThaiLocale)
private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", ThaiLocale)

private fun Long.toDateTimeText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateTime) + " น."

private fun rangeText(from: LocalDate, to: LocalDate): String =
    if (from.year == to.year) "${from.thaiShortDate()} – ${to.format(shortDateYear)}"
    else "${from.format(shortDateYear)} – ${to.format(shortDateYear)}"

// ---------------- รายการ ----------------

@Composable
fun WorkLogScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onData: () -> Unit,
    vm: WorkLogViewModel = viewModel(),
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var pickRange by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Peach,
                contentColor = Rust,
                shape = RoundedCornerShape(32.dp),
                icon = { Icon(Icons.Outlined.AddCircleOutline, null) },
                text = { Text("เพิ่ม", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("บันทึกงาน", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DarkBrown, modifier = Modifier.weight(1f))
                IconButton(onClick = onData) {
                    Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = "นำเข้า / ส่งออก / สำรองข้อมูล", tint = DarkBrown)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RangePreset.entries.forEach { preset ->
                    val selected = filter.preset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { if (preset == RangePreset.CUSTOM) pickRange = true else vm.setPreset(preset) },
                        label = {
                            Text(
                                if (preset == RangePreset.CUSTOM && selected && filter.from != null && filter.to != null)
                                    rangeText(filter.from!!, filter.to!!) else preset.label
                            )
                        },
                        leadingIcon = if (preset == RangePreset.CUSTOM) {
                            { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Peach,
                            selectedLabelColor = Rust,
                            selectedLeadingIconColor = Rust,
                        ),
                    )
                }
            }
            val list = logs
            if (list != null) {
                val from = filter.from
                val to = filter.to
                Text(
                    buildString {
                        append("${list.size} วัน")
                        if (from != null && to != null) append(" · ${rangeText(from, to)}")
                    },
                    color = Muted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (list != null && list.isEmpty()) {
                    item {
                        Text(
                            if (filter.isFiltered) "ไม่มีบันทึกในช่วงวันที่นี้"
                            else "ยังไม่มีบันทึก กด \"เพิ่ม\" เพื่อจดว่าวันนี้ทำงานอะไรบ้าง",
                            color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                        )
                    }
                }
                val groups = list.orEmpty().groupBy { YearMonth.from(it.date) }
                groups.forEach { (month, entries) ->
                    item(key = "m-$month") {
                        Text(
                            month.format(monthHeader),
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Brown,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
                        )
                    }
                    items(entries, key = { it.id }) { log -> LogRow(log, onClick = { onOpen(log.id) }) }
                }
            }
        }
    }

    if (pickRange) {
        DateRangeDialog(
            initialFrom = filter.from,
            initialTo = filter.to,
            onDismiss = { pickRange = false },
            onConfirm = { from, to ->
                pickRange = false
                vm.setCustom(from, to)
            },
        )
    }
}

@Composable
private fun LogRow(log: WorkLog, onClick: () -> Unit) {
    val isToday = log.date == LocalDate.now()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .width(48.dp)
                    .background(if (isToday) Peach else Background, RoundedCornerShape(12.dp))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${log.date.dayOfMonth}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (isToday) Rust else DarkBrown)
                Text(log.date.dayOfWeek.thaiShort(), fontSize = 12.sp, color = if (isToday) Rust else Muted)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                log.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "",
                fontSize = 16.sp, color = DarkBrown,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.utcMillis(),
        initialSelectedEndDateMillis = initialTo?.utcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    onConfirm(start.utcDate(), end.utcDate())
                },
            ) { Text("ตกลง") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("เลือกช่วงวันที่", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = true,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------- อ่านเต็ม ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogDetailScreen(id: Long, onEdit: (Long) -> Unit, onBack: () -> Unit, vm: WorkLogDetailViewModel = viewModel()) {
    val flow = remember(id) { vm.log(id) }
    val log by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // รายการถูกลบหรือย้ายไปรวมกับวันอื่น ให้กลับหน้ารายการ
    LaunchedEffect(log) {
        if (log != null) loaded = true else if (loaded) onBack()
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(log?.date?.thaiLongDate() ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") } },
                actions = {
                    IconButton(onClick = { onEdit(id) }) { Icon(Icons.Outlined.Edit, "แก้ไข", tint = DarkBrown) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "ลบ", tint = MissRed) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        val l = log ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            WhitePanel {
                SelectionContainer {
                    Text(l.text, fontSize = 17.sp, lineHeight = 26.sp, color = DarkBrown)
                }
            }
            Text(
                "แก้ไขล่าสุด ${l.updatedAtMillis.toDateTimeText()}",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("ลบบันทึกนี้?") },
            text = { Text("บันทึกของ${log?.date?.thaiLongDate() ?: "วันนี้"}จะถูกลบ") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id) {}
                }) { Text("ลบ", color = MissRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
private fun WhitePanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

// ---------------- เพิ่ม / แก้ไข ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogEditScreen(id: Long, onDone: () -> Unit, vm: WorkLogEditViewModel = viewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val form by vm.form.collectAsStateWithLifecycle()
    val f = form ?: return
    var pickDate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(if (f.sourceId == 0L) "เพิ่มบันทึกงาน" else "แก้ไขบันทึกงาน", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().clickable { pickDate = true },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Peach, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = Rust)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("วันที่", color = Muted, fontSize = 13.sp)
                        Text(f.date.thaiLongDate(), color = DarkBrown, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("เปลี่ยน", color = Brown, fontWeight = FontWeight.SemiBold)
                }
            }

            f.existing?.let { existing ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Peach),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, tint = Rust, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "วันนี้มีบันทึกอยู่แล้ว ข้อความที่พิมพ์จะต่อท้ายบันทึกเดิม",
                                color = Rust, fontSize = 14.sp,
                            )
                            Text(
                                existing.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "",
                                color = DarkBrown, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (f.sourceId == 0L) {
                                TextButton(onClick = vm::openExisting, contentPadding = PaddingValues(0.dp)) {
                                    Text("เปิดบันทึกเดิมมาแก้ไข", color = Rust, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = f.text,
                onValueChange = vm::setText,
                label = { Text("งานที่ทำ") },
                placeholder = { Text("วันนี้ทำงานอะไรบ้าง…") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Brown,
                ),
            )

            Button(
                onClick = { vm.save(onDone) },
                enabled = f.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brown),
            ) { Text("บันทึก", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    if (pickDate) {
        DatePickDialog(
            initial = f.date,
            onDismiss = { pickDate = false },
            onConfirm = {
                pickDate = false
                vm.setDate(it)
            },
        )
    }
}

// ---------------- นำเข้า / ส่งออก / สำรอง ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkLogDataScreen(onBack: () -> Unit, vm: WorkLogDataViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmImport by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::import)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::setFolder)
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ข้อมูลบันทึกงาน", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WhitePanel {
                Text("นำเข้า / ส่งออก", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
                Text("มีบันทึกทั้งหมด ${s.count} วัน ไฟล์เป็นแบบ JSON", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { exportLauncher.launch("WorkCheckIn-worklog-${LocalDate.now()}.json") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Brown),
                ) {
                    Icon(Icons.Outlined.FileUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ส่งออกไฟล์")
                }
                OutlinedButton(onClick = { confirmImport = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileDownload, null, tint = Brown)
                    Spacer(Modifier.width(8.dp))
                    Text("นำเข้าไฟล์", color = Brown)
                }
            }

            WhitePanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("สำรองอัตโนมัติ", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DarkBrown)
                        Text("สำรองทุกครั้งที่ปิดแอป เมื่อมีการเปลี่ยนแปลง", color = Muted, fontSize = 14.sp)
                    }
                    Switch(
                        checked = s.settings.enabled && s.settings.treeUri != null,
                        onCheckedChange = { on ->
                            if (on && s.settings.treeUri == null) folderLauncher.launch(null) else vm.setAutoBackup(on)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = CheckGreen),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Background)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, tint = Brown)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("โฟลเดอร์สำรอง", color = Muted, fontSize = 13.sp)
                        Text(s.settings.folderName ?: "ยังไม่ได้เลือก", color = DarkBrown, fontSize = 16.sp)
                    }
                    TextButton(onClick = { folderLauncher.launch(s.settings.treeUri) }) {
                        Text(if (s.settings.treeUri == null) "เลือก" else "เปลี่ยน", color = Brown, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (s.settings.treeUri != null) {
                    Text(
                        if (s.settings.lastBackupMillis > 0) "สำรองล่าสุด ${s.settings.lastBackupMillis.toDateTimeText()}"
                        else "ยังไม่เคยสำรอง",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    s.settings.lastError?.let { Text(it, color = MissRed, fontSize = 13.sp) }
                    Text(
                        "ไฟล์: ${DiaryBackup.BACKUP_FILE_NAME} (เขียนทับไฟล์เดิม) กู้คืนได้ด้วยปุ่ม \"นำเข้าไฟล์\"",
                        color = Muted, fontSize = 13.sp,
                    )
                    OutlinedButton(onClick = vm::backupNow, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("สำรองตอนนี้", color = Brown)
                    }
                }
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("นำเข้าไฟล์บันทึกงาน") },
            text = {
                Text("บันทึกในไฟล์จะรวมกับข้อมูลที่มีอยู่ ถ้าวันเดียวกันมีทั้งสองที่ จะเก็บอันที่แก้ไขล่าสุด")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("เลือกไฟล์") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("ยกเลิก") } },
        )
    }
}
