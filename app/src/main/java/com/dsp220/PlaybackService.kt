package com.dsp220.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notification: Notification = NotificationCompat.Builder(this, "DLMS_CHANNEL")
            .setContentTitle("DSP 220 PRO")
            .setContentText("Media sedang diputar di latar belakang...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        // Penanganan kompatibilitas Android 14 (API 34)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            startForeground(1, notification, foregroundType)
        } else {
            startForeground(1, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "DLMS_CHANNEL",
                "DLMS Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
