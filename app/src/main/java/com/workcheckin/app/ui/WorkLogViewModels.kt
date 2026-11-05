package com.workcheckin.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workcheckin.app.App
import com.workcheckin.app.data.DiaryBackup
import com.workcheckin.app.data.WorkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class RangePreset(val label: String) {
    ALL("ทั้งหมด"),
    WEEK("7 วันล่าสุด"),
    MONTH("เดือนนี้"),
    LAST_MONTH("เดือนก่อน"),
    CUSTOM("กำหนดเอง"),
}

data class LogFilter(val preset: RangePreset = RangePreset.ALL, val from: LocalDate? = null, val to: LocalDate? = null) {
    val isFiltered get() = preset != RangePreset.ALL

    companion object {
        fun of(preset: RangePreset, today: LocalDate = LocalDate.now()): LogFilter = when (preset) {
            RangePreset.ALL, RangePreset.CUSTOM -> LogFilter()
            RangePreset.WEEK -> LogFilter(preset, today.minusDays(6), today)
            RangePreset.MONTH -> LogFilter(preset, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
            RangePreset.LAST_MONTH -> today.minusMonths(1).let {
                LogFilter(preset, it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth()))
            }
        }
    }
}

private val MinDate: LocalDate = LocalDate.of(1900, 1, 1)
private val MaxDate: LocalDate = LocalDate.of(9999, 12, 31)

class WorkLogViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository

    private val _filter = MutableStateFlow(LogFilter())
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: StateFlow<List<WorkLog>?> = _filter
        .flatMapLatest { repo.workLogs(it.from ?: MinDate, it.to ?: MaxDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPreset(preset: RangePreset) {
        _filter.value = LogFilter.of(preset)
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        _filter.value = LogFilter(RangePreset.CUSTOM, minOf(from, to), maxOf(from, to))
    }
}

class WorkLogDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository

    fun log(id: Long): Flow<WorkLog?> = repo.observeWorkLog(id)

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteWorkLog(id)
            onDone()
        }
    }
}

data class WorkLogForm(
    /** รายการที่เปิดมาแก้ไข (0 = เพิ่มใหม่) */
    val sourceId: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val text: String = "",
    /** บันทึกอื่นที่มีอยู่แล้วในวันที่เลือก ข้อความจะถูกต่อท้ายรายการนั้น */
    val existing: WorkLog? = null,
)

class WorkLogEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    private val _form = MutableStateFlow<WorkLogForm?>(null)
    val form: StateFlow<WorkLogForm?> = _form.asStateFlow()

    fun load(id: Long) {
        if (_form.value != null) return
        viewModelScope.launch {
            val log = if (id != 0L) repo.getWorkLog(id) else null
            _form.value = if (log != null) {
                WorkLogForm(sourceId = log.id, date = log.date, text = log.text)
            } else {
                WorkLogForm(existing = repo.workLogForDay(LocalDate.now()))
            }
        }
    }

    fun setText(text: String) {
        _form.value = _form.value?.copy(text = text)
    }

    fun setDate(date: LocalDate) {
        val f = _form.value ?: return
        viewModelScope.launch {
            val other = repo.workLogForDay(date)?.takeIf { it.id != f.sourceId }
            _form.value = _form.value?.copy(date = date, existing = other)
        }
    }

    /** (ตอนเพิ่มใหม่) เปิดบันทึกเดิมของวันนั้นมาแก้ไขแทน ข้อความที่พิมพ์ไว้จะต่อท้าย */
    fun openExisting() {
        val f = _form.value ?: return
        val other = f.existing ?: return
        if (f.sourceId != 0L) return
        val typed = f.text.trim()
        _form.value = WorkLogForm(
            sourceId = other.id,
            date = other.date,
            text = if (typed.isEmpty()) other.text else other.text.trimEnd() + "\n" + typed,
        )
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value ?: return
        val typed = f.text.trim()
        if (typed.isEmpty()) return
        viewModelScope.launch {
            val other = f.existing
            if (other != null) {
                repo.saveWorkLog(other.copy(text = other.text.trimEnd() + "\n" + typed), replacesId = f.sourceId)
            } else {
                repo.saveWorkLog(WorkLog(id = f.sourceId, epochDay = f.date.toEpochDay(), text = typed))
            }
            onDone()
        }
    }
}

data class BackupUiState(val settings: DiaryBackup.Settings, val count: Int)

class WorkLogDataViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    private val ctx get() = getApplication<App>()

    private val _state = MutableStateFlow<BackupUiState?>(null)
    val state: StateFlow<BackupUiState?> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                BackupUiState(DiaryBackup.settings(ctx), repo.allWorkLogs().size)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _message.value = withContext(Dispatchers.IO) {
                runCatching {
                    val logs = repo.allWorkLogs()
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(DiaryBackup.toJson(logs).toByteArray(Charsets.UTF_8))
                    }
                    "ส่งออก ${logs.size} รายการแล้ว"
                }.getOrElse { "ส่งออกไม่สำเร็จ: ${it.message}" }
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _message.value = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    val logs = DiaryBackup.fromJson(text)
                    if (logs.isEmpty()) return@runCatching "ไม่พบบันทึกในไฟล์นี้"
                    val (applied, skipped) = repo.importWorkLogs(logs)
                    buildString {
                        append("นำเข้า $applied รายการ")
                        if (skipped > 0) append(" (ข้าม $skipped รายการที่มีอยู่แล้ว)")
                    }
                }.getOrElse { "ไฟล์ไม่ถูกต้อง นำเข้าไม่สำเร็จ" }
            }
            refresh()
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        DiaryBackup.setEnabled(ctx, enabled)
        refresh()
    }

    fun setFolder(tree: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { DiaryBackup.setFolder(ctx, tree) }
                    .onFailure { _message.value = "เข้าถึงโฟลเดอร์นี้ไม่ได้ ลองเลือกโฟลเดอร์อื่น" }
                    .onSuccess { _message.value = DiaryBackup.backupNow(ctx) ?: "ตั้งค่าโฟลเดอร์และสำรองครั้งแรกแล้ว" }
            }
            refresh()
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _message.value = withContext(Dispatchers.IO) { DiaryBackup.backupNow(ctx) ?: "สำรองข้อมูลแล้ว" }
            refresh()
        }
    }
}
