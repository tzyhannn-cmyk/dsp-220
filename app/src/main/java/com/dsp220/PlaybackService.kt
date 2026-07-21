package com.dsp220.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var dp: DynamicsProcessing? = null

    companion object {
        const val CHANNEL_ID = "playback_channel_dsp"
        const val NOTIFICATION_ID = 101

        // Actions bawaan Pemutar Lagu
        const val ACTION_PLAY = "com.dsp220.pro.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dsp220.pro.ACTION_PAUSE"
        const val ACTION_RESUME = "com.dsp220.pro.ACTION_RESUME"
        const val ACTION_STOP = "com.dsp220.pro.ACTION_STOP"
        const val EXTRA_URL = "com.dsp220.pro.EXTRA_URL"

        // Actions Kontrol DLMS / DSP
        const val ACTION_SET_GAIN = "com.dsp220.pro.SET_GAIN"
        const val ACTION_SET_EQ_BAND = "com.dsp220.pro.SET_EQ_BAND"
        const val ACTION_SET_LIMITER = "com.dsp220.pro.SET_LIMITER"
        const val ACTION_SET_SHELF = "com.dsp220.pro.SET_SHELF"
        const val ACTION_SET_XOVER = "com.dsp220.pro.SET_XOVER"
        const val ACTION_SET_COMPRESSOR = "com.dsp220.pro.SET_COMPRESSOR"
        const val ACTION_SET_DELAY = "com.dsp220.pro.SET_DELAY"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        // Menjalankan Foreground Service agar pemutaran tidak dimatikan oleh sistem Android
        startForeground(NOTIFICATION_ID, buildNotification("DLMS Audio Engine Active"))

        when (action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrEmpty()) startAudio(url)
            }
            ACTION_PAUSE -> mediaPlayer?.pause()
            ACTION_RESUME -> mediaPlayer?.start()
            ACTION_STOP -> stopAudio()

            // --- EKSEKUSI KONTROL DLMS NATIVE ---
            ACTION_SET_GAIN -> {
                val gainDb = intent.getFloatExtra("GAIN", 0f)
                setInputGain(gainDb)
            }
            ACTION_SET_EQ_BAND -> {
                val bandIndex = intent.getIntExtra("BAND_INDEX", 0)
                val gainDb = intent.getFloatExtra("BAND_GAIN", 0f)
                setEqBandGain(bandIndex, gainDb)
            }
            ACTION_SET_SHELF -> {
                val type = intent.getStringExtra("SHELF_TYPE") ?: "SUB"
                val freq = intent.getFloatExtra("FREQ", 100f)
                val gainDb = intent.getFloatExtra("GAIN", 0f)
                setShelfFilter(type, freq, gainDb)
            }
            ACTION_SET_XOVER -> {
                val type = intent.getStringExtra("XOVER_TYPE") ?: "HPF"
                val freq = intent.getFloatExtra("FREQ", 80f)
                val cutoffType = intent.getStringExtra("CUTOFF_TYPE") ?: "Butterworth"
                setXoverFilter(type, freq, cutoffType)
            }
            ACTION_SET_COMPRESSOR -> {
                val thresholdDb = intent.getFloatExtra("THRESHOLD", 0f)
                val ratio = intent.getFloatExtra("RATIO", 1f)
                val attackMs = intent.getFloatExtra("ATTACK", 10f)
                val releaseMs = intent.getFloatExtra("RELEASE", 100f)
                setCompressor(thresholdDb, ratio, attackMs, releaseMs)
            }
            ACTION_SET_LIMITER -> {
                val thresholdDb = intent.getFloatExtra("THRESHOLD", 0f)
                setLimiterThreshold(thresholdDb)
            }
            ACTION_SET_DELAY -> {
                val channel = intent.getIntExtra("CHANNEL", 0)
                val delayMs = intent.getFloatExtra("DELAY_MS", 0f)
                setAudioDelay(channel, delayMs)
            }
        }

        return START_NOT_STICKY
    }

    private fun startAudio(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    setupDynamicsProcessing(audioSessionId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- INISIALISASI DYNAMICS PROCESSING (MESIN DSP NATIVE) ---
    private fun setupDynamicsProcessing(sessionId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // Konfigurasi DSP: 2 Channel (Stereo), Pre-EQ (15 Bands), MBC Compressor, Limiter
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,    // Channel stereo
                    true, 15, // Pre-EQ: Active, 15 Band (EQ, Shelf, Xover)
                    true, 1,  // Multi-Band Compressor: Active, 1 Band (Compressor / DVC)
                    false, 0, // Post-EQ: Nonaktif
                    true      // Limiter: Active
                )

                dp = DynamicsProcessing(0, sessionId, builder.build()).apply {
                    enabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- FUNGSI OLAH PARAMETER DLMS ---

    // 1. Gain Control
    private fun setInputGain(gainDb: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dp != null) {
            dp?.setInputGainAllChannelsTo(gainDb)
        } else {
            val volume = Math.pow(10.0, (gainDb / 20.0).toDouble()).toFloat()
            mediaPlayer?.setVolume(volume, volume)
        }
    }

    // 2. EQ 15 Band Gain
    private fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dp?.setPreEqBandAllChannelsTo(bandIndex, gainDb)
        }
    }

    // 3. Sub & High Shelf
    private fun setShelfFilter(type: String, frequency: Float, gainDb: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dp != null) {
            val targetBand = if (type.equals("SUB", ignoreCase = true)) 0 else 14
            for (ch in 0..1) {
                val band = dp?.getPreEqBandByChannelIndex(ch, targetBand)
                band?.cutoffFrequency = frequency
                band?.gain = gainDb
                dp?.setPreEqBandByChannelIndex(ch, targetBand, band)
            }
        }
    }

    // 4. Crossover (HPF & LPF)
    private fun setXoverFilter(type: String, frequency: Float, cutoffType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dp != null) {
            val targetBand = if (type.equals("HPF", ignoreCase = true)) 0 else 14
            for (ch in 0..1) {
                val band = dp?.getPreEqBandByChannelIndex(ch, targetBand)
                band?.cutoffFrequency = frequency
                dp?.setPreEqBandByChannelIndex(ch, targetBand, band)
            }
        }
    }

    // 5. Compressor / DVC
    private fun setCompressor(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dp != null) {
            for (ch in 0..1) {
                val mbc = dp?.getMbcBandByChannelIndex(ch, 0)
                mbc?.apply {
                    threshold = thresholdDb
                    this.ratio = ratio
                    attackTime = attackMs
                    releaseTime = releaseMs
                }
                dp?.setMbcBandByChannelIndex(ch, 0, mbc)
            }
        }
    }

    // 6. Limiter
    private fun setLimiterThreshold(thresholdDb: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dp != null) {
            for (ch in 0..1) {
                val limiter = dp?.getLimiterByChannelIndex(ch)
                limiter?.threshold = thresholdDb
                dp?.setLimiterByChannelIndex(ch, limiter)
            }
        }
    }

    // 7. Time Delay
    private fun setAudioDelay(channel: Int, delayMs: Float) {
        // DynamicsProcessing Native API tidak menyediakan delay buffer langsung.
        // Parameter siap diproses jika kamu menggunakan ExoPlayer / Native AudioTrack kustom.
    }

    private fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        dp?.release()
        dp = null
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DLMS Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DSP DLMS Player Pro")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
