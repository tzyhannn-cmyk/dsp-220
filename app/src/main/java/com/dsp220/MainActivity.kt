package com.dsp220.pro

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
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

        // 1. Inisialisasi NewPipe Extractor dengan Downloader Sederhana via HTTP
        initNewPipeExtractor()

        // 2. Inisialisasi WebView sebagai wadah UI DSP 220 PRO
        webView = WebView(this)
        setContentView(webView)

        // 3. Konfigurasi Keamanan & Fitur Web Audio agar berjalan mulus
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // Mengizinkan autoplay audio lewat JS
        }

        webView.webViewClient = WebViewClient()
        
        // 4. Daftarkan Jembatan (Bridge) agar HTML bisa memanggil fungsi Android
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        // 5. Muat file HTML DSP dari folder assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    // Fungsi inisialisasi client network untuk NewPipe Extractor
    private fun initNewPipeExtractor() {
        try {
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val connection = URL(request.url()).openConnection() as HttpURLConnection
                    request.headers().forEach { (key, values) ->
                        connection.setRequestProperty(key, values.joinToString(","))
                    }
                    
                    val responseCode = connection.responseCode
                    val responseMessage = connection.responseMessage
                    val responseHeaders = connection.headerFields
                    
                    // PERBAIKAN: Membaca InputStream menjadi String seperti yang diminta compiler
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

    // ========================================================
    //  NATIVE BRIDGE ENGINE (Penghubung HTML & Android)
    // ========================================================
    inner class AndroidBridge {
        
        @JavascriptInterface
        fun extractYouTubeAudio(url: String) {
            // Jalankan proses ekstraksi di background thread agar aplikasi tidak hang/freeze
            Thread {
                try {
                    // Panggil extractor YouTube bawaan NewPipe
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()

                    // Ambil daftar streaming audio saja (format m4a atau webm)
                    val audioStreams = extractor.audioStreams

                    if (!audioStreams.isNullOrEmpty()) {
                        // Ambil link streaming mentah (.m4a) urutan pertama dengan kualitas terbaik
                        val rawAudioUrl = audioStreams[0].url

                        // Kembalikan URL mentah ke fungsi JavaScript 'onAudioExtracted' di UI Thread
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
                        val errorClean = e.message?.replace("'", "\\'") ?: "Unknown Error"
                        webView.evaluateJavascript("javascript:onExtractionFailed('$errorClean');", null)
                    }
                }
            }.start()
        }
    }
}
