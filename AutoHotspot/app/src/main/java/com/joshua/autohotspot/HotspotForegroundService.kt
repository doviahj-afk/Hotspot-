package com.joshua.autohotspot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

class HotspotForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val apStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // WIFI_AP_STATE_CHANGED extra: EXTRA_WIFI_AP_STATE, state 11 = disabled, 13 = enabled
            val state = intent.getIntExtra("wifi_state", -1)
            if (state == 11) { // AP disabled
                triggerHotspotOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        acquireWakeLock()
        try {
            registerReceiver(
                apStateReceiver,
                IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
            )
        } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        triggerHotspotOn()
        return START_STICKY
    }

    private fun triggerHotspotOn() {
        // Open tether settings so the accessibility service can find and tap the toggle
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (_: Exception) { }

        // Tell the accessibility service to look for the hotspot toggle
        sendBroadcast(Intent(HotspotAccessibilityService.ACTION_TOGGLE_HOTSPOT))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoHotspot::KeepAliveLock"
        )
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(10 * 60 * 1000L /*10 min*/)
    }

    private fun startForegroundWithNotification() {
        val channelId = "autohotspot_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "AutoHotspot", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoHotspot")
            .setContentText("Watching hotspot state")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(apStateReceiver) } catch (_: Exception) { }
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
