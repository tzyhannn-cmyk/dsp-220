package com.dsp220.pro

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initNewPipeExtractor()

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false 
            
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient() 
        
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun initNewPipeExtractor() {
        try {
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val connection = URL(request.url()).openConnection() as HttpURLConnection
                    
                    val method = request.httpMethod() ?: "GET"
                    connection.requestMethod = method
                    
                    request.headers().forEach { (key, values) ->
                        if (!key.equals("Accept-Encoding", ignoreCase = true)) {
                            if (key.equals("Cookie", ignoreCase = true)) {
                                connection.setRequestProperty(key, values.joinToString("; "))
                            } else {
                                values.forEach { value ->
                                    connection.addRequestProperty(key, value)
                                }
                            }
                        }
                    }
                    
                    if (connection.getRequestProperty("User-Agent") == null) {
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    }
                    
                    if (method == "POST" && request.dataToSend() != null) {
                        connection.doOutput = true
                        connection.outputStream.use { os ->
                            os.write(request.dataToSend())
                        }
                    }
                    
                    val responseCodeValue = connection.responseCode
                    val responseMessage = connection.responseMessage
                    val responseHeaders = connection.headerFields
                    
                    val responseBody = try {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    
                    return Response(responseCodeValue, responseMessage, responseHeaders, responseBody, request.url())
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class AndroidBridge {

        // --- PEMUTARAN AUDIO LATAR BELAKANG (NATIVE) ---
        @JavascriptInterface
        fun playAudioNative(url: String) {
            val intent = Intent(this@MainActivity, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY
                putExtra(PlaybackService.EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        @JavascriptInterface
        fun pauseAudioNative() {
            sendIntentToService(PlaybackService.ACTION_PAUSE)
        }

        @JavascriptInterface
        fun resumeAudioNative() {
            sendIntentToService(PlaybackService.ACTION_RESUME)
        }

        @JavascriptInterface
        fun stopAudioNative() {
            sendIntentToService(PlaybackService.ACTION_STOP)
        }

        // --- KONTROL DLMS / DSP NATIVE VIA JAVASCRIPT ---

        @JavascriptInterface
        fun setGainNative(gainDb: Float) {
            sendIntentToService(PlaybackService.ACTION_SET_GAIN) {
                putExtra("GAIN", gainDb)
            }
        }

        @JavascriptInterface
        fun setEqBandNative(bandIndex: Int, gainDb: Float) {
            sendIntentToService(PlaybackService.ACTION_SET_EQ_BAND) {
                putExtra("BAND_INDEX", bandIndex)
                putExtra("BAND_GAIN", gainDb)
            }
        }

        @JavascriptInterface
        fun setShelfNative(type: String, frequency: Float, gainDb: Float) {
            sendIntentToService("com.dsp220.pro.SET_SHELF") {
                putExtra("SHELF_TYPE", type)
                putExtra("FREQ", frequency)
                putExtra("GAIN", gainDb)
            }
        }

        @JavascriptInterface
        fun setXoverNative(type: String, frequency: Float, cutoffType: String) {
            sendIntentToService("com.dsp220.pro.SET_XOVER") {
                putExtra("XOVER_TYPE", type)
                putExtra("FREQ", frequency)
                putExtra("CUTOFF_TYPE", cutoffType)
            }
        }

        @JavascriptInterface
        fun setCompressorNative(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float) {
            sendIntentToService("com.dsp220.pro.SET_COMPRESSOR") {
                putExtra("THRESHOLD", thresholdDb)
                putExtra("RATIO", ratio)
                putExtra("ATTACK", attackMs)
                putExtra("RELEASE", releaseMs)
            }
        }

        @JavascriptInterface
        fun setLimiterNative(thresholdDb: Float) {
            sendIntentToService(PlaybackService.ACTION_SET_LIMITER) {
                putExtra("THRESHOLD", thresholdDb)
            }
        }

        @JavascriptInterface
        fun setDelayNative(channel: Int, delayMs: Float) {
            sendIntentToService("com.dsp220.pro.SET_DELAY") {
                putExtra("CHANNEL", channel)
                putExtra("DELAY_MS", delayMs)
            }
        }

        // --- EKSTRAKSI YOUTUBE ---
        @JavascriptInterface
        fun extractYouTubeAudio(url: String) {
            Thread {
                try {
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()
                    
                    val audioStreams = extractor.audioStreams
                    val videoStreams = extractor.videoStreams

                    val playableUrl = when {
                        !audioStreams.isNullOrEmpty() -> audioStreams[0].url
                        !videoStreams.isNullOrEmpty() -> videoStreams[0].url
                        else -> null
                    }

                    if (playableUrl != null) {
                        runOnUiThread {
                            webView.evaluateJavascript("onAudioExtracted('$playableUrl')", null)
                        }
                    } else {
                        runOnUiThread {
                            webView.evaluateJavascript("onExtractionFailed('Format audio maupun video tidak dapat ditemukan.')", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        // Sanitasi karakter agar JavaScript tidak error (SyntaxError) akibat newline/quote
                        val errorClean = (e.message ?: e.toString())
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", " ")
                        webView.evaluateJavascript("onExtractionFailed('$errorClean')", null)
                    }
                }
            }.start()
        }

        // Fungsi pembantu agar pengiriman intent dari AndroidBridge ke PlaybackService lebih ringkas
        private fun sendIntentToService(actionName: String, extras: (Intent.() -> Unit)? = null) {
            val intent = Intent(this@MainActivity, PlaybackService::class.java).apply {
                action = actionName
                extras?.invoke(this)
            }
            startService(intent)
        }
    }
}
