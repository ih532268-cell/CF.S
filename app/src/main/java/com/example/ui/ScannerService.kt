package com.example.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class ScannerService : Service() {

    companion object {
        const val CHANNEL_ID = "com.example.ui.SCANNER_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 888

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundWithNotification()
                observeScannerProgress()
            }
            ACTION_PAUSE -> {
                ScannerManager.pauseScan()
            }
            ACTION_RESUME -> {
                ScannerManager.resumeScan()
            }
            ACTION_STOP -> {
                ScannerManager.stopScan()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun startForegroundWithNotification() {
        val isEng = ScannerManager.isEnglish
        val initialTitle = if (isEng) "Cloudflare Scanner Active" else "اسکنر فعال کلادفلر"
        val initialText = if (isEng) "Warming up scanner telemetry cache..." else "آماده‌سازی لایه‌های شبکه..."

        val notification = buildNotification(initialTitle, initialText, isScanning = true, isPaused = false)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun observeScannerProgress() {
        serviceScope.launch {
            combine(
                ScannerManager.scanState,
                ScannerManager.isScanning,
                ScannerManager.isPaused
            ) { state, scanning, paused ->
                Triple(state, scanning, paused)
            }.collect { (state, scanning, paused) ->
                val isEng = ScannerManager.isEnglish
                
                var titleText = if (isEng) "Cloudflare Edge Scanner" else "اسکنر لبه کلادفلر"
                var bodyText = ""

                if (paused) {
                    titleText = if (isEng) "Scanning Paused ⏸️" else "اسکنر متوقف شد ⏸️"
                    bodyText = if (isEng) "Tap Resume to continue checking edges" else "جهت ادامه شروع مجدد اسکن را لمس کنید"
                } else if (!scanning) {
                    titleText = if (isEng) "Scanner Idle 😴" else "اسکنر غیرفعال 😴"
                    bodyText = if (isEng) "Waiting for target IP subnet limits" else "در انتظار تعیین محدوده رنج شبکه"
                } else {
                    when (state) {
                        is ScanState.Idle -> {
                            bodyText = if (isEng) "Preparing network sockets..." else "آماده‌سازی کانال ساب‌نت‌ها..."
                        }
                        is ScanState.Stage1PortScan -> {
                            titleText = if (isEng) "Stage 1: Checking Ports 🔎" else "مرحله ۱: یافتن پورت باز 🔎"
                            bodyText = if (isEng) {
                                "Progress: ${state.checked}/${state.total} | Active: ${state.foundActive}"
                            } else {
                                "پیشرفت: ${state.checked} از ${state.total} | پاسخگو: ${state.foundActive}"
                            }
                        }
                        is ScanState.Stage2Benchmark -> {
                            titleText = if (isEng) "Stage 2: Speed Profiling ⚡" else "مرحله ۲: تست پینگ و سرعت ⚡"
                            bodyText = if (isEng) {
                                "Benchmarking node ${state.indexed}/${state.total}"
                            } else {
                                "عیارسنجی گره‌ی ${state.indexed} از ${state.total}"
                            }
                        }
                        is ScanState.Finished -> {
                            titleText = if (isEng) "Scanning Complete! 🎉" else "اسکن با موفقیت به پایان رسید! 🎉"
                            bodyText = if (isEng) "Optimized edge roster updated" else "کانفیگ‌های نهایی تولید و نشان‌گذاری شدند"
                        }
                    }
                }

                val notification = buildNotification(titleText, bodyText, scanning, paused)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        isScanning: Boolean,
        isPaused: Boolean
    ): Notification {
        val isEng = ScannerManager.isEnglish
        
        // Tap intent to open MainActivity
        val appIntent = Intent(this, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // PendingIntents for action buttons
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pauseIntent = Intent(this, ScannerService::class.java).apply { action = ACTION_PAUSE }
        val piPause = PendingIntent.getService(this, 1, pauseIntent, piFlags)

        val resumeIntent = Intent(this, ScannerService::class.java).apply { action = ACTION_RESUME }
        val piResume = PendingIntent.getService(this, 2, resumeIntent, piFlags)

        val stopIntent = Intent(this, ScannerService::class.java).apply { action = ACTION_STOP }
        val piStop = PendingIntent.getService(this, 3, stopIntent, piFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(appPendingIntent)
            .setOngoing(isScanning || isPaused)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Add action buttons dynamically based on scanning/paused state
        if (isScanning && !isPaused) {
            val pauseLabel = if (isEng) "Pause" else "توقف موقت"
            builder.addAction(android.R.drawable.ic_media_pause, pauseLabel, piPause)
        } else if (isPaused) {
            val resumeLabel = if (isEng) "Resume" else "ادامه‌ی اسکن"
            builder.addAction(android.R.drawable.ic_media_play, resumeLabel, piResume)
        }

        val stopLabel = if (isEng) "Stop" else "توقف کامل"
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, stopLabel, piStop)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isEng = ScannerManager.isEnglish
            val channelName = if (isEng) "Cloudflare Scanner Service" else "سرویس اسکنر کلادفلر"
            val channelDescription = if (isEng) "Displays ongoing edge scanner metrics" else "نمایش عملکرد و میزان پیشرفت اسکنر"
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDescription
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
