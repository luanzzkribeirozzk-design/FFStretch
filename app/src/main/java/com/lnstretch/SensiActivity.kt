package com.lnstretch

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SensiActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setSupportZoom(false)
            displayZoomControls = false
            builtInZoomControls = false
        }
        webView.webViewClient = WebViewClient()
        webView.loadUrl("file:///android_asset/sensi.html")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
