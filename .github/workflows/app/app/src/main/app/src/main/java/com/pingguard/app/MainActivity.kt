package com.pingguard.app

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pingguard.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val pingHistory = mutableListOf<Long>()

    private val pingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pingMs = intent?.getLongExtra(PingMonitorService.EXTRA_PING_MS, -1) ?: -1
            updatePingDisplay(pingMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        binding.btnStartMonitoring.setOnClickListener {
            startPingService()
            Toast.makeText(this, "Мониторинг пинга запущен", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopMonitoring.setOnClickListener {
            stopPingService()
            Toast.makeText(this, "Мониторинг остановлен", Toast.LENGTH_SHORT).show()
        }

        binding.btnGameMode.setOnClickListener {
            optimizeForGame()
        }

        binding.btnNetworkSettings.setOnClickListener {
            openNetworkSettings()
        }

        startPingService()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    private fun startPingService() {
        val intent = Intent(this, PingMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopPingService() {
        val intent = Intent(this, PingMonitorService::class.java)
        stopService(intent)
        binding.tvPingValue.text = "—"
        binding.tvPingStatus.text = "Остановлено"
    }

    private fun optimizeForGame() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = activityManager.runningAppProcesses ?: emptyList()
            var closedCount = 0

            for (proc in runningApps) {
                val pkg = proc.processName
                if (pkg != packageName && !isSystemCritical(pkg)) {
                    try {
                        activityManager.killBackgroundProcesses(pkg)
                        closedCount++
                    } catch (e: Exception) {
                    }
                }
            }
            Toast.makeText(
                this,
                "Освобождено фоновых процессов: $closedCount",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось оптимизировать: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSystemCritical(packageName: String): Boolean {
        val protected = listOf("android", "com.android.systemui", "com.pingguard.app")
        return protected.any { packageName.startsWith(it) }
    }

    private fun openNetworkSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun updatePingDisplay(pingMs: Long) {
        if (pingMs < 0) {
            binding.tvPingValue.text = "МЁРТВ"
            binding.tvPingStatus.text = "Нет ответа от сети"
            binding.tvPingValue.setTextColor(0xFFFF3B30.toInt())
        } else {
            binding.tvPingValue.text = "$pingMs ms"
            when {
                pingMs < 100 -> {
                    binding.tvPingStatus.text = "Отлично"
                    binding.tvPingValue.setTextColor(0xFF34C759.toInt())
                }
                pingMs < 300 -> {
                    binding.tvPingStatus.text = "Нормально"
                    binding.tvPingValue.setTextColor(0xFFFF9500.toInt())
                }
                else -> {
                    binding.tvPingStatus.text = "Плохо"
                    binding.tvPingValue.setTextColor(0xFFFF3B30.toInt())
                }
            }
        }

        pingHistory.add(if (pingMs < 0) 999 else pingMs)
        if (pingHistory.size > 30) pingHistory.removeAt(0)
        binding.pingGraphView.updateData(pingHistory)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PingMonitorService.ACTION_PING_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pingReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(pingReceiver)
        } catch (e: Exception) {
        }
    }
}
