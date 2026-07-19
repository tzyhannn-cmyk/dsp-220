package com.dsp220.pro

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PERBAIKAN UTAMA: Mengaktifkan mesin penyimpan Cookie otomatis di seluruh sistem aplikasi
        CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))

        initNewPipeExtractor()

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false 
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
                    
                    // Prioritaskan Header dan User-Agent bawaan asli dari NewPipe agar tidak bentrok
                    var hasUserAgent = false
                    request.headers().forEach { (key, values) ->
                        connection.setRequestProperty(key, values.joinToString(","))
                        if (key.equals("User-Agent", ignoreCase = true)) {
                            hasUserAgent = true
                        }
                    }
                    
                    // Jika NewPipe tidak menyediakan User-Agent pada request tertentu, baru pakai backup ini
                    if (!hasUserAgent) {
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
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
        @JavascriptInterface
        fun extractYouTubeAudio(url: String) {
            Thread {
                try {
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()
                    val audioStreams = extractor.audioStreams

                    if (!audioStreams.isNullOrEmpty()) {
                        val rawAudioUrl = audioStreams[0].url
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onAudioExtracted('$rawAudioUrl');", null)
                        }
                    } else {
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onExtractionFailed('Audio stream tidak ditemukan.');", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        val errorClean = e.toString().replace("'", "\\'") 
                        webView.evaluateJavascript("javascript:onExtractionFailed('$errorClean');", null)
                    }
                }
            }.start()
        }
    }
}
