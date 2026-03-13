package com.devtools.broadcastdebug

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * メイン画面のUIロジックを管理するViewModel。
 *
 * ブロードキャストログの蓄積、監視対象アクションの管理、
 * サービス稼働状態、およびアクション別カラー割り当てを [StateFlow] で公開する。
 */
class MainViewModel : ViewModel() {

    /** 受信したブロードキャストのログ一覧。新規ログは末尾に追加される。 */
    private val _logs = MutableStateFlow<List<BroadcastService.BroadcastLog>>(emptyList())
    val logs: StateFlow<List<BroadcastService.BroadcastLog>> = _logs.asStateFlow()

    /** 監視対象として登録されたブロードキャストアクション名のセット。 */
    private val _registeredActions = MutableStateFlow<Set<String>>(emptySet())
    val registeredActions: StateFlow<Set<String>> = _registeredActions.asStateFlow()

    /** フォアグラウンドサービスが現在起動中かどうか。 */
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    /**
     * アクション名をキー、割り当てカラー（ARGB Int）を値とするマップ。
     * [addAction] 呼び出し時に [COLOR_PALETTE] から循環割り当てされる。
     */
    private val _actionColors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val actionColors: StateFlow<Map<String, Int>> = _actionColors.asStateFlow()

    companion object {
        /**
         * アクションに循環割り当てされる8色のカラーパレット（ARGB Int）。
         * 登録アクション数が8を超えた場合は先頭から再利用される。
         */
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

    /**
     * 受信したブロードキャストのログを一覧末尾に追加する。
     *
     * @param log 追加する [BroadcastService.BroadcastLog]
     */
    fun addLog(log: BroadcastService.BroadcastLog) {
        _logs.value = _logs.value + log
    }

    /**
     * 監視対象アクションを登録し、カラーパレットから色を割り当てる。
     * 既に登録済みのアクションを渡した場合は何もしない。
     *
     * @param action 登録するブロードキャストアクション名
     */
    fun addAction(action: String) {
        if (action in _registeredActions.value) return
        val colorIndex = _actionColors.value.size % COLOR_PALETTE.size
        _actionColors.value = _actionColors.value + (action to COLOR_PALETTE[colorIndex])
        _registeredActions.value = _registeredActions.value + action
    }

    /**
     * 指定したアクションの登録とカラー割り当てを削除する。
     * 存在しないアクションを渡した場合は何もしない。
     *
     * @param action 削除するブロードキャストアクション名
     */
    fun removeAction(action: String) {
        _actionColors.value = _actionColors.value - action
        _registeredActions.value = _registeredActions.value - action
    }

    /**
     * 登録済みアクションとカラーマップをすべてクリアする。
     */
    fun clearActions() {
        _actionColors.value = emptyMap()
        _registeredActions.value = emptySet()
    }

    /**
     * サービスの稼働状態を更新する。
     *
     * @param running `true` でサービス起動中、`false` で停止中
     */
    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }
}
