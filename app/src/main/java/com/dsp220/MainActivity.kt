package com.dsp220.pro

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient // PERBAIKAN: Untuk mengaktifkan popup alert
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inisialisasi NewPipe Extractor
        initNewPipeExtractor()

        // 2. Inisialisasi WebView
        webView = WebView(this)
        setContentView(webView)

        // 3. Konfigurasi Keamanan & Fitur Web Audio
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false 
        }

        webView.webViewClient = WebViewClient()
        
        // PERBAIKAN PENTING: Mengaktifkan WebChromeClient agar alert() JavaScript bisa muncul di Android
        webView.webChromeClient = WebChromeClient() 
        
        // 4. Daftarkan Jembatan (Bridge)
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        // 5. Muat file HTML DSP
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun initNewPipeExtractor() {
        try {
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val connection = URL(request.url()).openConnection() as HttpURLConnection
                    
                    // PERBAIKAN PENTING: Tambahkan User-Agent Browser agar YouTube tidak memblokir koneksi kita
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    
                    request.headers().forEach { (key, values) ->
                        connection.setRequestProperty(key, values.joinToString(","))
                    }
                    
                    val responseCode = connection.responseCode
                    val responseMessage = connection.responseMessage
                    val responseHeaders = connection.headerFields
                    
                    val responseBody = try {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    
                    return Response(responseCode, responseMessage, responseHeaders, responseBody, request.url())
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun extractYouTubeAudio(url: String) {
            Thread {
                try {
                    // Ekstraksi Link YouTube via NewPipe
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()

                    val audioStreams = extractor.audioStreams

                    if (!audioStreams.isNullOrEmpty()) {
                        // Ambil URL streaming mentah format audio teratas
                        val rawAudioUrl = audioStreams[0].url

                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onAudioExtracted('$rawAudioUrl');", null)
                        }
                    } else {
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onExtractionFailed('Audio stream tidak ditemukan pada video ini.');", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        // Kirim detail pesan error asli agar kita tahu letak kendalanya
                        val errorClean = e.toString().replace("'", "\\'") 
                        webView.evaluateJavascript("javascript:onExtractionFailed('$errorClean');", null)
                    }
                }
            }.start()
        }
    }
}
                }
            }.start()
        }
    }
}
