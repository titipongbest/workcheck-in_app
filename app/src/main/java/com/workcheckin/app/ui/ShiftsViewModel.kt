package com.workcheckin.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workcheckin.app.App
import com.workcheckin.app.data.Shift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ShiftsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository

    val shifts: StateFlow<List<Shift>?> = repo.shifts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(shift: Shift, enabled: Boolean) {
        viewModelScope.launch { repo.saveShift(shift.copy(enabled = enabled)) }
    }
}

data class ShiftForm(
    val id: Long = 0,
    val name: String = "งานประจำ",
    val inMinutes: Int = 8 * 60 + 30,
    val outMinutes: Int = 17 * 60 + 30,
    /** จันทร์ - ศุกร์ */
    val daysMask: Int = 0b0011111,
    val followUpMinutes: Int = 10,
    val enabled: Boolean = true,
    val createdEpochDay: Long = LocalDate.now().toEpochDay(),
) {
    fun toShift() = Shift(id, name.trim().ifEmpty { "งานประจำ" }, inMinutes, outMinutes, daysMask, enabled, followUpMinutes, createdEpochDay)

    companion object {
        fun from(s: Shift) = ShiftForm(s.id, s.name, s.inMinutes, s.outMinutes, s.daysMask, s.followUpMinutes, s.enabled, s.createdEpochDay)
    }
}

class ShiftEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    private val _form = MutableStateFlow<ShiftForm?>(null)
    val form: StateFlow<ShiftForm?> = _form.asStateFlow()

    fun load(id: Long) {
        if (_form.value != null) return
        viewModelScope.launch {
            _form.value = repo.getShift(id)?.let(ShiftForm::from) ?: ShiftForm()
        }
    }

    fun update(transform: (ShiftForm) -> ShiftForm) {
        _form.value = _form.value?.let(transform)
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value ?: return
        viewModelScope.launch {
            repo.saveShift(f.toShift())
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val f = _form.value ?: return
        viewModelScope.launch {
            if (f.id != 0L) repo.deleteShift(f.toShift())
            onDone()
        }
    }
}
