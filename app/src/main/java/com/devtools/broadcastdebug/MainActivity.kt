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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val logAdapter = LogAdapter()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBroadcastService()
        } else {
            Toast.makeText(this, "通知権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    private val manageStorageSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            startBroadcastService()
        } else {
            Toast.makeText(this, "ストレージ権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

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

    private fun setupRecyclerView() {
        binding.rvLogs.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        binding.rvLogs.adapter = logAdapter
    }

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

    private fun stopBroadcastService() {
        val intent = Intent(this, BroadcastService::class.java)
        stopService(intent)
        viewModel.setServiceRunning(false)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.registeredActions.collect { actions ->
                        binding.chipGroupActions.removeAllViews()
                        actions.forEach { addChipToGroup(it) }
                        saveActions(actions)
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

    private fun updateUiState(isRunning: Boolean) {
        binding.btnStart.isEnabled = !isRunning
        binding.btnStop.isEnabled = isRunning
        binding.etActionName.isEnabled = !isRunning
        binding.btnAddAction.isEnabled = !isRunning

        for (i in 0 until binding.chipGroupActions.childCount) {
            binding.chipGroupActions.getChildAt(i).isEnabled = !isRunning
        }
    }

    private fun restoreSavedActions() {
        val prefs = getPreferences(MODE_PRIVATE)
        val saved = prefs.getStringSet("registered_actions", emptySet()) ?: emptySet()
        saved.forEach { viewModel.addAction(it) }
    }

    private fun saveActions(actions: Set<String>) {
        getPreferences(MODE_PRIVATE).edit()
            .putStringSet("registered_actions", actions)
            .apply()
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BroadcastService.broadcastFlow.collect { log ->
                    viewModel.addLog(log)
                }
            }
        }
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
        private var logs = listOf<BroadcastService.BroadcastLog>()

        fun setLogs(newLogs: List<BroadcastService.BroadcastLog>) {
            logs = newLogs
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

        inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(log: BroadcastService.BroadcastLog) {
                binding.tvTimestamp.text = log.timestamp
                binding.tvAction.text = log.action
                binding.tvExtras.text = log.extras
            }
        }
    }
}
