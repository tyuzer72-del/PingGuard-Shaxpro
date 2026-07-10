package com.pingguard.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

class PingMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private lateinit var vibrator: Vibrator

    private val BAD_PING_THRESHOLD = 500L
    private val BAD_PING_STREAK_TO_ALERT = 3
    private val CHECK_INTERVAL_MS = 1000L

    private var badStreak = 0
    private var alertActive = false

    companion object {
        const val CHANNEL_ID_SERVICE = "ping_guard_service"
        const val CHANNEL_ID_ALERT = "ping_guard_alert"
        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_ALERT = 2

        const val ACTION_PING_UPDATE = "com.pingguard.app.PING_UPDATE"
        const val EXTRA_PING_MS = "extra_ping_ms"
        const val EXTRA_IS_ALIVE = "extra_is_alive"
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification("Мониторинг запущен..."))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val pingMs = measurePing()
                handlePingResult(pingMs)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun measurePing(): Long = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }

    private fun handlePingResult(pingMs: Long) {
        val isBad = pingMs < 0 || pingMs > BAD_PING_THRESHOLD

        if (isBad) {
            badStreak++
        } else {
            badStreak = 0
            if (alertActive) {
                alertActive = false
                clearAlert()
            }
        }

        val displayText = if (pingMs < 0) "Пинг: НЕТ ОТВЕТА" else "Пинг: ${pingMs} ms"
        updateServiceNotification(displayText)

        broadcastPingUpdate(pingMs)

        if (badStreak >= BAD_PING_STREAK_TO_ALERT && !alertActive) {
            alertActive = true
            triggerAlert()
        }
    }

    private fun broadcastPingUpdate(pingMs: Long) {
        val intent = Intent(ACTION_PING_UPDATE)
        intent.putExtra(EXTRA_PING_MS, pingMs)
        intent.putExtra(EXTRA_IS_ALIVE, pingMs >= 0)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun triggerAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 300, 100, 300, 100, 300)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 100, 300, 100, 300), -1)
        }

        val settingsIntent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ Пинг умер!")
            .setContentText("Нажмите, чтобы открыть настройки сети и переключить")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID_ALERT, notification)
    }

    private fun clearAlert() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIF_ID_ALERT)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            "Мониторинг пинга",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(serviceChannel)

        val alertChannel = NotificationChannel(
            CHANNEL_ID_ALERT,
            "Алерт плохого пинга",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildServiceNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("PingGuard активен")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateServiceNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID_SERVICE, buildServiceNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
