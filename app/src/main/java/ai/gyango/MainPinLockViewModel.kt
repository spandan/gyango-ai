package ai.gyango

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Drives full-screen PIN unlock after the activity stops (background / minimize). */
class MainPinLockViewModel : ViewModel() {
    private val _needsUnlock = MutableStateFlow(false)
    val needsUnlock: StateFlow<Boolean> = _needsUnlock.asStateFlow()

    fun requestUnlock() {
        _needsUnlock.value = true
    }

    fun clearUnlock() {
        _needsUnlock.value = false
    }
}
