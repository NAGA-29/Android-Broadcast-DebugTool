package com.devtools.broadcastdebug

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devtools.broadcastdebug.databinding.ActivityMainBinding
import com.devtools.broadcastdebug.databinding.ItemLogBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * アプリのメイン画面。
 *
 * ブロードキャストアクションの登録・削除、[BroadcastService] の起動・停止、
 * 受信ログのリスト表示を担当する。
 * UIの状態管理は [MainViewModel] に委譲し、[BroadcastService.broadcastFlow] を
 * 直接collectしてログをViewModelへ追加する。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val logAdapter = LogAdapter()

    /**
     * POST_NOTIFICATIONS 権限リクエストの結果を受け取るランチャー。
     * 権限が付与された場合は [startBroadcastService] を呼び出す。
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBroadcastService()
        } else {
            Toast.makeText(this, "通知権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * MANAGE_EXTERNAL_STORAGE 権限設定画面からの戻りを受け取るランチャー。
     * 権限が付与済みの場合は [startBroadcastService] を呼び出す。
     */
    private val manageStorageSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            startBroadcastService()
        } else {
            Toast.makeText(this, "ストレージ権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Activityの初期化。ViewBinding・RecyclerView・リスナー・状態監視・
     * 保存済みアクションの復元をセットアップする。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeState()
        observeLogs()
        restoreSavedActions()
    }

    /**
     * ログ表示用RecyclerViewを初期化する。
     * 新着ログが下に追加されるよう、逆順レイアウト（[LinearLayoutManager.reverseLayout]）を設定する。
     */
    private fun setupRecyclerView() {
        binding.rvLogs.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        binding.rvLogs.adapter = logAdapter
    }

    /**
     * 各ボタンのクリックリスナーを設定する。
     * - 「追加」ボタン: 入力欄のアクション名をViewModelに登録
     * - 「開始」ボタン: 権限確認の上でサービス起動
     * - 「停止」ボタン: サービス停止
     * - 「クリア」ボタン: SharedPreferencesとViewModelの登録アクションをすべて削除
     */
    private fun setupListeners() {
        binding.btnAddAction.setOnClickListener {
            val action = binding.etActionName.text.toString().trim()
            if (action.isNotEmpty()) {
                viewModel.addAction(action)
                binding.etActionName.text?.clear()
            }
        }

        binding.btnStart.setOnClickListener {
            if (viewModel.registeredActions.value.isEmpty()) {
                Toast.makeText(this, "アクションを少なくとも1つ追加してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkPermissionsAndStart()
        }

        binding.btnStop.setOnClickListener {
            stopBroadcastService()
        }

        binding.btnClearSavedActions.setOnClickListener {
            getPreferences(MODE_PRIVATE).edit().remove("registered_actions").apply()
            viewModel.clearActions()
            Toast.makeText(this, "保存済みアクションを削除しました", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 指定したアクション名のChipをChipGroupに追加する。
     * Chipの閉じるアイコンをタップするとViewModelからそのアクションが削除される。
     *
     * @param action ChipGroupに追加するブロードキャストアクション名
     */
    private fun addChipToGroup(action: String) {
        val chip = Chip(this).apply {
            text = action
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                viewModel.removeAction(action)
            }
        }
        binding.chipGroupActions.addView(chip)
    }

    /**
     * 必要な権限を確認し、すべて満たしていれば [startBroadcastService] を呼び出す。
     *
     * 確認する権限:
     * - Android 11以上: MANAGE_EXTERNAL_STORAGE（ログファイル書き込みに必要）
     * - Android 13以上: POST_NOTIFICATIONS（フォアグラウンドサービス通知に必要）
     */
    private fun checkPermissionsAndStart() {
        // Android 11+: MANAGE_EXTERNAL_STORAGE が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            manageStorageSettingsLauncher.launch(intent)
            return
        }
        // Android 13+: POST_NOTIFICATIONS が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startBroadcastService()
    }

    /**
     * [BroadcastService] をフォアグラウンドサービスとして起動する。
     * 登録済みアクションをIntentのExtrasに渡し、ViewModelのサービス状態をtrueに更新する。
     */
    private fun startBroadcastService() {
        val intent = Intent(this, BroadcastService::class.java).apply {
            putExtra(BroadcastService.EXTRA_ACTIONS, viewModel.registeredActions.value.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        viewModel.setServiceRunning(true)
    }

    /**
     * [BroadcastService] を停止し、ViewModelのサービス状態をfalseに更新する。
     */
    private fun stopBroadcastService() {
        val intent = Intent(this, BroadcastService::class.java)
        stopService(intent)
        viewModel.setServiceRunning(false)
    }

    /**
     * ViewModelの各StateFlowを監視し、UIに反映する。
     * [Lifecycle.State.STARTED] の間のみ収集し、バックグラウンド時は自動停止する。
     *
     * 監視対象:
     * - [MainViewModel.registeredActions]: ChipGroupの再描画とカラーマップ更新
     * - [MainViewModel.isServiceRunning]: ボタン・入力欄の有効/無効切り替え
     * - [MainViewModel.logs]: RecyclerViewのログ一覧更新と末尾スクロール
     */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.registeredActions.collect { actions ->
                        binding.chipGroupActions.removeAllViews()
                        actions.forEach { addChipToGroup(it) }
                        saveActions(actions)
                        logAdapter.updateColorMap(viewModel.actionColors.value)
                    }
                }
                launch {
                    viewModel.isServiceRunning.collect { isRunning ->
                        updateUiState(isRunning)
                    }
                }
                launch {
                    viewModel.logs.collect { logs ->
                        logAdapter.setLogs(logs)
                        if (logs.isNotEmpty()) {
                            binding.rvLogs.scrollToPosition(logs.size - 1)
                        }
                    }
                }
            }
        }
    }

    /**
     * サービスの稼働状態に応じてUI要素の有効/無効を切り替える。
     * サービス起動中はアクションの追加・削除操作を無効化する。
     *
     * @param isRunning サービスが起動中であれば `true`
     */
    private fun updateUiState(isRunning: Boolean) {
        binding.btnStart.isEnabled = !isRunning
        binding.btnStop.isEnabled = isRunning
        binding.etActionName.isEnabled = !isRunning
        binding.btnAddAction.isEnabled = !isRunning

        for (i in 0 until binding.chipGroupActions.childCount) {
            binding.chipGroupActions.getChildAt(i).isEnabled = !isRunning
        }
    }

    /**
     * SharedPreferencesに保存されていたアクション一覧をViewModelに復元する。
     * Activity再生成時（画面回転など）に呼ばれるが、ViewModelが既に保持している
     * 場合は [MainViewModel.addAction] 内の重複チェックにより無視される。
     */
    private fun restoreSavedActions() {
        val prefs = getPreferences(MODE_PRIVATE)
        val saved = prefs.getStringSet("registered_actions", emptySet()) ?: emptySet()
        saved.forEach { viewModel.addAction(it) }
    }

    /**
     * 現在登録されているアクション一覧をSharedPreferencesに永続化する。
     *
     * @param actions 保存するアクション名のセット
     */
    private fun saveActions(actions: Set<String>) {
        getPreferences(MODE_PRIVATE).edit()
            .putStringSet("registered_actions", actions)
            .apply()
    }

    /**
     * [BroadcastService.broadcastFlow] を監視し、受信したログをViewModelに追加する。
     * [Lifecycle.State.STARTED] の間のみ収集する。
     */
    private fun observeLogs() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BroadcastService.broadcastFlow.collect { log ->
                    viewModel.addLog(log)
                }
            }
        }
    }

    /**
     * ブロードキャストログを表示するRecyclerViewのアダプター。
     * ログ一覧とアクション別カラーマップを保持し、各アイテムの左端ストリップに色を反映する。
     */
    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
        private var logs = listOf<BroadcastService.BroadcastLog>()
        private var colorMap = mapOf<String, Int>()

        /**
         * 表示するログ一覧を更新し、RecyclerViewを再描画する。
         *
         * @param newLogs 新しいログ一覧
         */
        fun setLogs(newLogs: List<BroadcastService.BroadcastLog>) {
            logs = newLogs
            notifyDataSetChanged()
        }

        /**
         * アクション別カラーマップを更新し、RecyclerViewを再描画する。
         * アクションの追加・削除時に呼ばれる。
         *
         * @param map アクション名をキー、ARGB Intを値とするカラーマップ
         */
        fun updateColorMap(map: Map<String, Int>) {
            colorMap = map
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return LogViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logs[position])
        }

        override fun getItemCount(): Int = logs.size

        /**
         * ログアイテム1件を表示するViewHolder。
         * タイムスタンプ・アクション名・Extrasの表示と、左端カラーストリップの色設定を行う。
         */
        inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {

            /**
             * [BroadcastService.BroadcastLog] の内容をViewに反映する。
             * カラーマップにアクションが存在しない場合はパレットの先頭色をフォールバックとして使用する。
             *
             * @param log 表示対象のブロードキャストログ
             */
            fun bind(log: BroadcastService.BroadcastLog) {
                binding.tvTimestamp.text = log.timestamp
                binding.tvAction.text = log.action
                binding.tvExtras.text = log.extras
                val color = colorMap[log.action] ?: MainViewModel.COLOR_PALETTE[0]
                binding.colorStrip.setBackgroundColor(color)
            }
        }
    }
}
