package com.guardianlink.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.policy.PolicyEngine
import com.guardianlink.policy.PolicyStore
import com.guardianlink.sync.PolicySynchronizer
import org.json.JSONTokener

/** Visible child browser. It can be selected as Android's default browser for normal web links. */
class SafeBrowserActivity : android.app.Activity() {
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private val store by lazy { PolicyStore(this) }
    private val engine = PolicyEngine()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(NoirUi.BACKGROUND) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(4)) }
        val back = Button(this).apply { text = "‹"; textSize = 24f; isAllCaps = false; setOnClickListener { if (browser.canGoBack()) browser.goBack() } }
        address = EditText(this).apply { hint = "Search or enter website"; setSingleLine(); textSize = 15f; setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED); background = rounded(NoirUi.SURFACE_RAISED) }
        val go = Button(this).apply { text = "Go"; isAllCaps = false; setOnClickListener { load(address.text.toString()) } }
        val sync = Button(this).apply { text = "Sync"; textSize = 12f; isAllCaps = false; setOnClickListener { syncRules(true) } }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        bar.addView(go, LinearLayout.LayoutParams(dp(52), dp(48)))
        bar.addView(sync, LinearLayout.LayoutParams(dp(58), dp(48)))
        root.addView(bar)
        status = TextView(this).apply { text = "Family browser • Websites, YouTube Shorts, and page keywords are checked here."; textSize = 12f; setTextColor(NoirUi.MUTED); setPadding(dp(14), dp(2), dp(14), dp(6)) }
        root.addView(status)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = FilterClient()
        }
        root.addView(browser, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        load(incomingUrl(intent) ?: "https://www.google.com")
        syncRules(false)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incomingUrl(intent)?.let(::load) }

    private fun incomingUrl(intent: Intent): String? = intent.dataString ?: intent.getStringExtra(EXTRA_URL)
    private fun load(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val url = when {
            text.startsWith("http://", true) || text.startsWith("https://", true) -> text
            text.contains('.') && !text.contains(' ') -> "https://$text"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8")
        }
        address.setText(url)
        browser.loadUrl(url)
    }

    private fun syncRules(showResult: Boolean) {
        if (showResult) status.text = "Checking for the latest family rules…"
        Thread {
            val synced = PolicySynchronizer(this).sync()
            runOnUiThread {
                if (showResult || synced) status.text = if (synced) "Family rules are up to date." else "Could not sync now; the last downloaded family rules remain active."
                if (synced && ::browser.isInitialized) browser.reload()
            }
        }.start()
    }

    private fun enforce(url: String, visibleText: String) {
        val decision = engine.pageDecision(store.load(), url, visibleText)
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
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { address.setText(url); enforce(url, "") }
        override fun onPageFinished(view: WebView, url: String) {
            // Check title plus visible text, so words in loaded search/result pages are considered.
            view.evaluateJavascript("JSON.stringify({t:document.title||'',b:(document.body&&document.body.innerText||'').slice(0,12000)})") { value ->
                val page = runCatching { JSONTokener(value).nextValue() as org.json.JSONObject }.getOrNull()
                enforce(url, "${page?.optString("t").orEmpty()} ${page?.optString("b").orEmpty()}")
            }
        }
    }

    override fun onDestroy() { browser.destroy(); super.onDestroy() }
    private fun rounded(fill: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(14).toFloat(); setStroke(dp(1), NoirUi.SURFACE_RAISED) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val EXTRA_URL = "url"
        fun intent(context: android.content.Context, url: String = "https://www.google.com") = Intent(context, SafeBrowserActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
