package com.devtools.broadcastdebug

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
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

    /**
     * 受信したブロードキャストの記録を保持するデータクラス。
     *
     * @property timestamp 受信日時（例: "2024-01-01 12:00:00.000"）
     * @property action 受信したブロードキャストのアクション名
     * @property extras Intentに付属していたExtrasを文字列化したもの
     */
    data class BroadcastLog(
        val timestamp: String,
        val action: String,
        val extras: String
    )

    /**
     * サービス生成時に呼ばれる。
     * 通知チャンネルの作成とログファイルのセットアップを行う。
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupLogFile()
    }

    /**
     * サービス開始コマンド受信時に呼ばれる。
     * Intentに含まれるアクション一覧を取得してフォアグラウンド通知を表示し、
     * ブロードキャストレシーバーを登録する。
     *
     * @param intent 起動元から渡されるIntent。[EXTRA_ACTIONS]にアクション文字列配列を含む。
     * @param flags 追加の起動フラグ
     * @param startId 本起動リクエストを識別するID
     * @return [START_NOT_STICKY]（プロセスキル後の自動再起動を行わない）
     */
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

    /**
     * ログ保存先ファイルを初期化する。
     * 外部ストレージの `BroadcastDebugLogs/` ディレクトリに、
     * 起動日時を含むファイル名（例: `log_20240101_120000.txt`）でファイルを作成する。
     */
    private fun setupLogFile() {
        val directory = File(Environment.getExternalStorageDirectory(), "BroadcastDebugLogs")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        logFile = File(directory, "log_$timestamp.txt")

        writeToLogFile("--- Service Started at $timestamp ---")
    }

    /**
     * 指定されたアクション一覧に対応する動的ブロードキャストレシーバーを登録する。
     * 既存のレシーバーが存在する場合は先に解除する。
     * 受信時は [BroadcastLog] を生成し、[broadcastFlow] にemitするとともにログファイルへ書き込む。
     *
     * @param actions 監視対象のブロードキャストアクション文字列の配列
     */
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

    /**
     * 指定されたテキストをログファイルに追記する。
     * IO ディスパッチャーで非同期に実行され、書き込み失敗時はLogcatにエラーを出力する。
     *
     * @param text ログファイルに追記する文字列
     */
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

    /**
     * フォアグラウンドサービス用の通知チャンネルを作成する。
     * Android 8.0（Oreo）以降でのみ実行される。
     * 通知の重要度は [NotificationManager.IMPORTANCE_LOW]（サウンドなし）。
     */
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

    /**
     * このサービスはバインドをサポートしない。
     *
     * @return 常に `null`
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * サービス破棄時に呼ばれる。
     * 登録済みのレシーバーを解除し、終了ログを書き込んだ後、コルーチンジョブをキャンセルする。
     */
    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        writeToLogFile("--- Service Stopped at ${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())} ---")
        job.cancel()
        super.onDestroy()
    }
}
