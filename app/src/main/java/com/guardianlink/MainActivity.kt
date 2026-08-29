package com.guardianlink

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.ui.ChildModeActivity
import com.guardianlink.ui.GuestPreviewActivity
import com.guardianlink.ui.ParentModeActivity
import com.guardianlink.ui.NoirUi

/** Clear role selection prevents a child phone from being confused with a parent account screen. */
class MainActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(28))
            setBackgroundColor(NoirUi.BACKGROUND)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(content) })

        content.addView(TextView(this).apply {
            text = getString(R.string.app_name).uppercase()
            textSize = 12f
            letterSpacing = .14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(NoirUi.GOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        content.addView(TextView(this).apply {
            text = "Guidance that keeps family life moving"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(NoirUi.TEXT)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "Set healthy boundaries, respond to SOS alerts, and see only the safety information you choose to share."
            textSize = 15f
            setTextColor(NoirUi.MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(24))
        })

        content.addView(roleCard("P", "Parent or guardian", "Sign in or create your private family dashboard.", "Continue as parent") {
            startActivity(Intent(this, ParentModeActivity::class.java))
        })
        content.addView(roleCard("C", "Set up a child device", "Use a one-time code from the parent dashboard. Permissions are explained step by step.", "Set up this device") {
            startActivity(Intent(this, ChildModeActivity::class.java))
        })
        content.addView(NoirUi.secondaryButton(this, "Explore the app as a guest") { startActivity(Intent(this@MainActivity, GuestPreviewActivity::class.java)) }.apply {
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) }
        })
        content.addView(TextView(this).apply {
            text = "For parents and legal guardians only. Location sharing is visible on the child phone and controlled from the family dashboard."
            textSize = 12f
            setTextColor(NoirUi.MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(18), dp(10), 0)
        })
    }

    private fun roleCard(icon: String, title: String, body: String, action: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(16))
        background = NoirUi.interactiveBackground(this@MainActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 18)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(7), 0, dp(5)) }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = icon; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(NoirUi.BACKGROUND)
                background = NoirUi.rounded(this@MainActivity, NoirUi.GOLD, NoirUi.GOLD_DIM, 20)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(this@MainActivity).apply { text = title; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NoirUi.TEXT) })
                addView(TextView(this@MainActivity).apply { text = body; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, dp(3), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(NoirUi.primaryButton(this@MainActivity, action, onClick).apply {
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(15), 0, 0) }
        })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
