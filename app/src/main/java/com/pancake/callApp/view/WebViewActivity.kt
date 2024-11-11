package com.pancake.callApp.view

import android.R.string
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.chiller3.bcr.databinding.PancakeWebViewActivityBinding
import com.pancake.callApp.view.ui.theme.BCRTheme


class WebViewActivity : AppCompatActivity() {
    
    private lateinit var binding: PancakeWebViewActivityBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
        Log.d("WebView", "onCreate: $url")
        binding = PancakeWebViewActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.webView.loadUrl(url ?: "https://www.google.com")
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if(url != null && url.startsWith("https://hub.internal.pancake.vn/api/users/pancake_sim_call://")){
                    val urlFormat = url.replace("https://hub.internal.pancake.vn/api/users/", "")
                    val accessToken = Uri.parse(urlFormat).getQueryParameter("access_token")
                    val data = Intent()
                    data.putExtra("access_token", accessToken)
                    setResult(RESULT_OK, data)
                    finish()
                    return true
                }
                return false
            }
        }
    }

}