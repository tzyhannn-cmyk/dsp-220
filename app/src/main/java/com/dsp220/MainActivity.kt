package com.dsp220

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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
import org.schabi.newpipe.extractor.localization.Localization
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Buka WebView terlebih dahulu agar UI tidak kosong/crash jika NewPipe gagal
        try {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal menginisialisasi WebView: ${e.message}")
        }

        // Inisialisasi NewPipeExtractor secara aman
        try {
            initNewPipeExtractor()
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal menginisialisasi NewPipe: ${e.message}")
        }
    }

    private fun initNewPipeExtractor() {
        // Set Localization default (PENTING agar NewPipeExtractor tidak crash saat dipanggil)
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
        }, Localization.fromLocale(java.util.Locale.ENGLISH))
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun extractYouTubeAudio(url: String) {
            Thread {
                try {
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()
                    
                    val audioStreams = extractor.audioStreams
                    val videoStreams = extractor.videoStreams

                    val audioUrl = if (!audioStreams.isNullOrEmpty()) audioStreams[0].url else ""
                    val videoUrl = if (!videoStreams.isNullOrEmpty()) videoStreams[0].url else ""

                    if (!audioUrl.isNullOrEmpty() || !videoUrl.isNullOrEmpty()) {
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onMediaExtracted('$audioUrl', '$videoUrl');", null)
                        }
                    } else {
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onExtractionFailed('Format audio maupun video tidak dapat ditemukan.');", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
                        val errorClean = errorMessage.replace("'", "\\'") 
                        webView.evaluateJavascript("javascript:onExtractionFailed('$errorClean');", null)
                    }
                }
            }.start()
        }
    }
}
