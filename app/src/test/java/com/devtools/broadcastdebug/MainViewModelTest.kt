package com.devtools.broadcastdebug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    // ─── addAction ───────────────────────────────────────────────────────────

    @Test
    fun addAction_updatesRegisteredActions() {
        viewModel.addAction("com.example.TEST")

        assertTrue(viewModel.registeredActions.value.contains("com.example.TEST"))
    }

    @Test
    fun addAction_duplicateAction_isIgnored() {
        viewModel.addAction("com.example.TEST")
        viewModel.addAction("com.example.TEST")

        assertEquals(1, viewModel.registeredActions.value.size)
    }

    @Test
    fun addAction_multipleActions_allRegistered() {
        viewModel.addAction("com.example.A")
        viewModel.addAction("com.example.B")
        viewModel.addAction("com.example.C")

        assertEquals(3, viewModel.registeredActions.value.size)
    }

    // ─── removeAction ────────────────────────────────────────────────────────

    @Test
    fun removeAction_removesFromRegisteredActions() {
        viewModel.addAction("com.example.TEST")
        viewModel.removeAction("com.example.TEST")

        assertFalse(viewModel.registeredActions.value.contains("com.example.TEST"))
    }

    @Test
    fun removeAction_onNonExistentAction_doesNotThrow() {
        // 登録されていないアクションを削除しても例外が起きないこと
        viewModel.removeAction("com.example.NONEXISTENT")

        assertTrue(viewModel.registeredActions.value.isEmpty())
    }

    @Test
    fun removeAction_onlyRemovesTargetAction() {
        viewModel.addAction("com.example.A")
        viewModel.addAction("com.example.B")
        viewModel.removeAction("com.example.A")

        assertFalse(viewModel.registeredActions.value.contains("com.example.A"))
        assertTrue(viewModel.registeredActions.value.contains("com.example.B"))
    }

    // ─── clearActions ────────────────────────────────────────────────────────

    @Test
    fun clearActions_removesAllRegisteredActions() {
        viewModel.addAction("com.example.A")
        viewModel.addAction("com.example.B")
        viewModel.clearActions()

        assertTrue(viewModel.registeredActions.value.isEmpty())
    }

    @Test
    fun clearActions_onEmptySet_doesNotThrow() {
        viewModel.clearActions()

        assertTrue(viewModel.registeredActions.value.isEmpty())
    }

    // ─── actionColors ────────────────────────────────────────────────────────

    @Test
    fun addAction_assignsColorFromPalette() {
        viewModel.addAction("com.example.A")

        val color = viewModel.actionColors.value["com.example.A"]
        assertTrue(color != null && MainViewModel.COLOR_PALETTE.contains(color))
    }

    @Test
    fun addAction_eightActions_allHaveUniqueColors() {
        repeat(8) { i -> viewModel.addAction("com.example.ACTION_$i") }

        val colors = viewModel.actionColors.value.values.toList()
        assertEquals(8, colors.toSet().size)
    }

    @Test
    fun addAction_ninthAction_colorCyclesBackToFirst() {
        repeat(9) { i -> viewModel.addAction("com.example.ACTION_$i") }

        val firstColor = viewModel.actionColors.value["com.example.ACTION_0"]
        val ninthColor = viewModel.actionColors.value["com.example.ACTION_8"]
        assertEquals(firstColor, ninthColor)
    }

    @Test
    fun addAction_duplicateAction_doesNotChangeColorMap() {
        viewModel.addAction("com.example.A")
        val colorBefore = viewModel.actionColors.value["com.example.A"]

        viewModel.addAction("com.example.A")
        val colorAfter = viewModel.actionColors.value["com.example.A"]

        assertEquals(colorBefore, colorAfter)
        assertEquals(1, viewModel.actionColors.value.size)
    }

    @Test
    fun removeAction_removesColorFromMap() {
        viewModel.addAction("com.example.A")
        viewModel.removeAction("com.example.A")

        assertFalse(viewModel.actionColors.value.containsKey("com.example.A"))
    }

    @Test
    fun removeAction_doesNotAffectOtherActionColors() {
        viewModel.addAction("com.example.A")
        viewModel.addAction("com.example.B")
        val colorB = viewModel.actionColors.value["com.example.B"]

        viewModel.removeAction("com.example.A")

        assertEquals(colorB, viewModel.actionColors.value["com.example.B"])
    }

    @Test
    fun clearActions_clearsColorMap() {
        viewModel.addAction("com.example.A")
        viewModel.addAction("com.example.B")
        viewModel.clearActions()

        assertTrue(viewModel.actionColors.value.isEmpty())
    }

    @Test
    fun addAction_afterClear_reassignsColorFromPaletteStart() {
        viewModel.addAction("com.example.A")
        val colorFirst = viewModel.actionColors.value["com.example.A"]

        viewModel.clearActions()
        viewModel.addAction("com.example.A")
        val colorAfterClear = viewModel.actionColors.value["com.example.A"]

        assertEquals(colorFirst, colorAfterClear)
    }

    @Test
    fun addAction_twoDistinctActions_haveDifferentColors() {
        viewModel.addAction("com.example.A")
        viewModel.addAction("com.example.B")

        val colorA = viewModel.actionColors.value["com.example.A"]
        val colorB = viewModel.actionColors.value["com.example.B"]
        assertNotEquals(colorA, colorB)
    }

    // ─── addLog ──────────────────────────────────────────────────────────────

    @Test
    fun addLog_appendsToLogs() {
        val log = BroadcastService.BroadcastLog("2024-01-01 00:00:00.000", "com.example.ACTION", "key: value\n")
        viewModel.addLog(log)

        assertEquals(1, viewModel.logs.value.size)
        assertEquals(log, viewModel.logs.value[0])
    }

    @Test
    fun addLog_multipleEntries_preservesOrder() {
        val log1 = BroadcastService.BroadcastLog("2024-01-01 00:00:00.000", "ACTION_1", "")
        val log2 = BroadcastService.BroadcastLog("2024-01-01 00:00:01.000", "ACTION_2", "")
        val log3 = BroadcastService.BroadcastLog("2024-01-01 00:00:02.000", "ACTION_3", "")

        viewModel.addLog(log1)
        viewModel.addLog(log2)
        viewModel.addLog(log3)

        assertEquals(listOf(log1, log2, log3), viewModel.logs.value)
    }

    @Test
    fun initialState_logsIsEmpty() {
        assertTrue(viewModel.logs.value.isEmpty())
    }

    // ─── setServiceRunning ───────────────────────────────────────────────────

    @Test
    fun setServiceRunning_true_updatesState() {
        viewModel.setServiceRunning(true)

        assertTrue(viewModel.isServiceRunning.value)
    }

    @Test
    fun setServiceRunning_false_updatesState() {
        viewModel.setServiceRunning(true)
        viewModel.setServiceRunning(false)

        assertFalse(viewModel.isServiceRunning.value)
    }

    @Test
    fun initialState_isServiceRunningIsFalse() {
        assertFalse(viewModel.isServiceRunning.value)
    }
}
