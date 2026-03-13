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

    fun addLog(log: BroadcastService.BroadcastLog) {
        _logs.value = _logs.value + log
    }

    fun addAction(action: String) {
        _registeredActions.value = _registeredActions.value + action
    }

    fun removeAction(action: String) {
        _registeredActions.value = _registeredActions.value - action
    }

    fun clearActions() {
        _registeredActions.value = emptySet()
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }
}
