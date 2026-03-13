package com.devtools.broadcastdebug

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _logs = MutableStateFlow<List<BroadcastService.BroadcastLog>>(emptyList())
    val logs: StateFlow<List<BroadcastService.BroadcastLog>> = _logs.asStateFlow()

    private val _registeredActions = MutableStateFlow<Set<String>>(emptySet())
    val registeredActions: StateFlow<Set<String>> = _registeredActions.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _actionColors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val actionColors: StateFlow<Map<String, Int>> = _actionColors.asStateFlow()

    companion object {
        val COLOR_PALETTE = intArrayOf(
            0xFFF44336.toInt(),
            0xFF2196F3.toInt(),
            0xFF4CAF50.toInt(),
            0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(),
            0xFF00BCD4.toInt(),
            0xFFFF5722.toInt(),
            0xFF607D8B.toInt()
        )
    }

    fun addLog(log: BroadcastService.BroadcastLog) {
        _logs.value = _logs.value + log
    }

    fun addAction(action: String) {
        if (action in _registeredActions.value) return
        val colorIndex = _actionColors.value.size % COLOR_PALETTE.size
        _actionColors.value = _actionColors.value + (action to COLOR_PALETTE[colorIndex])
        _registeredActions.value = _registeredActions.value + action
    }

    fun removeAction(action: String) {
        _actionColors.value = _actionColors.value - action
        _registeredActions.value = _registeredActions.value - action
    }

    fun clearActions() {
        _actionColors.value = emptyMap()
        _registeredActions.value = emptySet()
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }
}
