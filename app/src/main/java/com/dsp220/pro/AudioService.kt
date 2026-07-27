package com.dsp220.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject

class AudioService : Service() {

    private val binder = LocalBinder()
    private var player: ExoPlayer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    private val CHANNEL_ID = "dsp_audio_channel"
    private val NOTIFICATION_ID = 1001

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Inisialisasi ExoPlayer
        player = ExoPlayer.Builder(this).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        // 1. Tangani jika ada perintah STOP dari MainActivity
        if (intent.action == "ACTION_STOP") {
            stopAudio()
            return START_NOT_STICKY
        }

        // 2. Tangani pemutaran audio dari stream URL
        val streamUrl = intent.getStringExtra("EXTRA_URL")
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "DSP 220 PRO Audio"

        if (!streamUrl.isNullOrEmpty()) {
            playAudio(streamUrl)
            startForeground(NOTIFICATION_ID, buildNotification(title))
        }

        return START_STICKY
    }

    private fun playAudio(url: String) {
        player?.let { exoPlayer ->
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

            // Inisialisasi DSP Native jika audioSessionId tersedia
            setupDSP(exoPlayer.audioSessionId)
        }
    }

    private fun stopAudio() {
        player?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // Inisialisasi modul Native DSP Android (DynamicsProcessing)
    private fun setupDSP(audioSessionId: Int) {
        if (audioSessionId != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                dynamicsProcessing?.release()

                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // Stereo (2 Channel)
                    true, 1, // PreEQ
                    true, 1, // MBC
                    true, 1, // PostEQ
                    true     // Limiter
                ).build()

                dynamicsProcessing = DynamicsProcessing(audioSessionId, config)
                dynamicsProcessing?.enabled = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // =========================================================================
    // --- KONTROL DSP (Dipanggil dari MainActivity via LocalBinder) ---
    // =========================================================================

    fun applyDSPConfig(jsonString: String) {
        try {
            val json = JSONObject(jsonString)

            // A. Mengatur Volume / Gain Fader
            if (json.has("gain")) {
                val gain = json.getDouble("gain").toFloat()
                // Konversi rentang 0 - 100 dari JS HTML ke 0.0 - 1.0 ExoPlayer
                val volume = (gain / 100f).coerceIn(0f, 1f)
                player?.volume = volume
            }

            // B. Mengatur Mute
            if (json.has("isMuted")) {
                val isMuted = json.getBoolean("isMuted")
                player?.volume = if (isMuted) 0f else 1f
            }

            // C. Pengaturan Equalizer/Limiter via DynamicsProcessing
            // Parameter tambahan seperti HPF/LPF dapat diterapkan ke dynamicsProcessing di sini

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    fun setMute(isMuted: Boolean) {
        player?.volume = if (isMuted) 0f else 1f
    }

    // =========================================================================
    // --- HELPER NOTIFIKASI FOREGROUND SERVICE ---
    // =========================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DSP Audio Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel pemutaran audio latar belakang DSP 220 PRO"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Memutar Audio DSP")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        player?.release()
        player = null
    }
}
