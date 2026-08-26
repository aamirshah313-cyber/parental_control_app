package com.guardianlink.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.guardianlink.policy.PolicyEngine
import com.guardianlink.policy.PolicyStore
import org.json.JSONTokener

/**
 * The supervised browser is the only reliable app-owned surface for selective URL/title filtering.
 * It does not inspect private messages, audio, camera data, or other installed apps.
 */
class SafeBrowserActivity : android.app.Activity() {
    private lateinit var browser: WebView
    private val store by lazy { PolicyStore(this) }
    private val engine = PolicyEngine()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = FilterClient()
        }
        setContentView(browser, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        browser.loadUrl(intent.getStringExtra(EXTRA_URL) ?: "https://www.youtube.com")
    }

    private fun enforce(url: String, pageTitle: String) {
        val decision = engine.pageDecision(store.load(), url, pageTitle)
        if (decision.blocked) {
            browser.stopLoading()
            startActivity(BlockingActivity.intent(this, decision.reason ?: "Blocked", "browser"))
        }
    }

    private inner class FilterClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val decision = engine.pageDecision(store.load(), url, "")
            if (!decision.blocked) return false
            startActivity(BlockingActivity.intent(this@SafeBrowserActivity, decision.reason ?: "Blocked", "browser"))
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = enforce(url, "")

        override fun onPageFinished(view: WebView, url: String) {
            // A page title (including a YouTube video title) is checked locally after dynamic content loads.
            view.evaluateJavascript("document.title") { value ->
                val title = runCatching { JSONTokener(value).nextValue() as String }.getOrDefault("")
                enforce(url, title)
            }
        }
    }

    override fun onDestroy() { browser.destroy(); super.onDestroy() }

    companion object {
        private const val EXTRA_URL = "url"
        fun intent(context: android.content.Context, url: String = "https://www.youtube.com") =
            Intent(context, SafeBrowserActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
