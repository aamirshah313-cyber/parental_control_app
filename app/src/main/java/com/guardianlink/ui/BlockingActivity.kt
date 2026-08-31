package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.ui.NoirUi

class BlockingActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        root.addView(TextView(this).apply { text = "PAUSE ACTIVE"; textSize = 12f; letterSpacing = .12f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(NoirUi.GOLD) })
        root.addView(TextView(this).apply { text = "This app is unavailable"; textSize = 26f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(NoirUi.TEXT); setPadding(0, dp(8), 0, dp(8)) })
        root.addView(TextView(this).apply { text = intent.getStringExtra(EXTRA_REASON) ?: "A parent rule is currently active."; textSize = 16f; gravity = Gravity.CENTER; setTextColor(NoirUi.MUTED); setPadding(dp(16), 0, dp(16), dp(24)) })
        root.addView(NoirUi.primaryButton(this, "Go to home screen") { finishAndRemoveTask() })
        setContentView(root)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_REASON = "reason"
        fun intent(context: Context, reason: String, packageName: String) = Intent(context, BlockingActivity::class.java).apply {
            putExtra(EXTRA_REASON, reason)
            putExtra("package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
