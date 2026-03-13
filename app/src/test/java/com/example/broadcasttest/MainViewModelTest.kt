package com.example.broadcasttest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @Test
    fun addAction_updatesState() {
        val viewModel = MainViewModel()
        viewModel.addAction("com.example.TEST")
        assertTrue(viewModel.registeredActions.value.contains("com.example.TEST"))
    }

    @Test
    fun removeAction_updatesState() {
        val viewModel = MainViewModel()
        viewModel.addAction("com.example.TEST")
        viewModel.removeAction("com.example.TEST")
        assertTrue(viewModel.registeredActions.value.isEmpty())
    }

    @Test
    fun addLog_updatesState() {
        val viewModel = MainViewModel()
        val log = BroadcastService.BroadcastLog("2023-10-27", "ACTION", "EXTRAS")
        viewModel.addLog(log)
        assertEquals(1, viewModel.logs.value.size)
        assertEquals(log, viewModel.logs.value[0])
    }
}
