package com.dsp220.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class PlaybackService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private val CHANNEL_ID = "DLMS_CHANNEL"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_PLAY = "com.dsp220.pro.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dsp220.pro.ACTION_PAUSE"
        const val ACTION_RESUME = "com.dsp220.pro.ACTION_RESUME"
        const val ACTION_STOP = "com.dsp220.pro.ACTION_STOP"
        const val EXTRA_URL = "extra_audio_url"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrEmpty()) {
                    playAudio(url)
                }
            }
            ACTION_PAUSE -> pauseAudio()
            ACTION_RESUME -> resumeAudio()
            ACTION_STOP -> stopAudio()
        }

        // Tampilkan Notifikasi Latar Belakang
        val notification = createNotification("Media sedang diputar di latar belakang...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun playAudio(url: String) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    // Mencegah CPU tidur saat layar HP dimatikan (WakeLock)
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setOnPreparedListener(this@PlaybackService)
                    setOnErrorListener(this@PlaybackService)
                }
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.setDataSource(url)
            mediaPlayer?.prepareAsync() // Menyiapkan koneksi audio di latar belakang
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseAudio() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    private fun resumeAudio() {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
        }
    }

    private fun stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start() // Mulai memutar suara begitu audio siap
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        mp?.reset()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DLMS Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DSP 220 PRO")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
