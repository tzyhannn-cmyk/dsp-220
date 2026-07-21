package com.dsp220.pro

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Callback untuk pemilih file lokal dari WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Launcher untuk menangani jendela pemilih file Android
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

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

        // PEMBARUAN: WebChromeClient didukung penuh untuk File Picker lokal
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                try {
                    val intent = fileChooserParams?.createIntent()
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        } 
        
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

        // FITUR BARU 1: Menjalankan PlaybackService untuk pemutaran latar belakang
        @JavascriptInterface
        fun startBackgroundService() {
            val serviceIntent = Intent(this@MainActivity, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        // FITUR BARU 2: Mengekstrak metadata lengkap + URL Audio & Video
        @JavascriptInterface
        fun extractYouTubeAudio(url: String) {
            Thread {
                try {
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()
                    
                    // Mengambil metadata informasi lagu
                    val title = extractor.name ?: "Judul Tidak Diketahui"
                    val uploader = extractor.uploaderName ?: "Uploader Tidak Diketahui"
                    val thumbnailUrl = extractor.thumbnails?.firstOrNull()?.url ?: ""
                    
                    // Mengambil stream audio & video
                    val audioStreams = extractor.audioStreams
                    val videoStreams = extractor.videoStreams

                    val audioUrl = audioStreams?.firstOrNull()?.url ?: ""
                    val videoUrl = videoStreams?.firstOrNull()?.url ?: ""

                    if (audioUrl.isNotEmpty() || videoUrl.isNotEmpty()) {
                        // Mengemas data menjadi format JSON
                        val jsonResponse = JSONObject().apply {
                            put("title", title)
                            put("uploader", uploader)
                            put("thumbnail", thumbnailUrl)
                            put("audioUrl", audioUrl)
                            put("videoUrl", videoUrl)
                        }.toString()

                        val safeJson = jsonResponse.replace("'", "\\'")

                        runOnUiThread {
                            // Mengirim data JSON ke JavaScript di index.html
                            webView.evaluateJavascript("javascript:onExtractionSuccess('$safeJson');", null)
                        }
                    } else {
                        runOnUiThread {
                            webView.evaluateJavascript("javascript:onExtractionFailed('Format media tidak dapat ditemukan.');", null)
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
