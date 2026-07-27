package com.dsp220.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class AudioService : Service() {

    private var player: ExoPlayer? = null
    private val CHANNEL_ID = "audio_playback_channel"

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Cek jika ada perintah STOP dari MainActivity
        if (intent?.action == "ACTION_STOP") {
            player?.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Ambil data URL dan Judul (Disesuaikan dengan kunci di MainActivity.kt)
        val audioUrl = intent?.getStringExtra("EXTRA_URL")
        val audioTitle = intent?.getStringExtra("EXTRA_TITLE") ?: "Sedang memutar audio..."

        // 3. Buat dan tampilkan notifikasi di Foreground
        val notification = createNotification("Pemutar Musik", audioTitle)
        startForeground(1, notification)

        // 4. Putar Audio pakai ExoPlayer
        if (!audioUrl.isNullOrEmpty()) {
            playAudio(audioUrl)
        }

        return START_STICKY
    }

    private fun playAudio(url: String) {
        player?.let {
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Notifikasi tidak bisa di-swipe hapus saat lagu jalan
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemutar Musik Latar Belakang",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        player?.stop()
        player?.release()
        player = null
        super.onDestroy()
    }
}
