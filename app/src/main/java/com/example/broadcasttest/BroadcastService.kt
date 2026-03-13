package com.example.broadcasttest

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class BroadcastService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var receiver: BroadcastReceiver? = null
    private var logFile: File? = null

    companion object {
        const val CHANNEL_ID = "BroadcastServiceChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_ACTIONS = "extra_actions"

        // Shared flow to communicate received broadcasts to the UI
        val broadcastFlow = MutableSharedFlow<BroadcastLog>(extraBufferCapacity = 10)
    }

    data class BroadcastLog(
        val timestamp: String,
        val action: String,
        val extras: String
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupLogFile()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actions = intent?.getStringArrayExtra(EXTRA_ACTIONS) ?: emptyArray()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerBroadcastReceiver(actions)

        return START_NOT_STICKY
    }

    private fun setupLogFile() {
        val directory = File(getExternalFilesDir(null), "broadcast_logs")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        logFile = File(directory, "log_$timestamp.txt")

        writeToLogFile("--- Service Started at $timestamp ---")
    }

    private fun registerBroadcastReceiver(actions: Array<String>) {
        // Unregister previous if any
        receiver?.let { unregisterReceiver(it) }

        if (actions.isEmpty()) return

        val filter = IntentFilter()
        actions.forEach { filter.addAction(it) }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val action = intent.action ?: "Unknown Action"
                val extras = StringBuilder()
                intent.extras?.keySet()?.forEach { key ->
                    val value = intent.extras?.get(key)
                    extras.append("$key: $value\n")
                }

                val logEntry = BroadcastLog(timestamp, action, extras.toString())

                // Save to file
                writeToLogFile("[$timestamp] ACTION: $action\nEXTRAS:\n${extras}\n---")

                // Notify UI
                scope.launch {
                    broadcastFlow.emit(logEntry)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun writeToLogFile(text: String) {
        scope.launch {
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append(text).append("\n")
                }
            } catch (e: Exception) {
                Log.e("BroadcastService", "Failed to write to log file", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        writeToLogFile("--- Service Stopped at ${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())} ---")
        job.cancel()
        super.onDestroy()
    }
}
