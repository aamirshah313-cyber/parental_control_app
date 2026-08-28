package com.guardianlink.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.R

/** Read-only feature tour. It never contacts Supabase or displays family data. */
class GuestPreviewActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(BACKGROUND)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BACKGROUND); addView(content) })

        content.addView(TextView(this).apply {
            text = "EXPLORE SAFELY"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
            setTextColor(BLUE)
        })
        content.addView(TextView(this).apply {
            text = "A calmer way to guide digital life"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(NAVY)
            setPadding(0, dp(6), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "This is a read-only tour. No account, child device, or location information is shown."
            textSize = 15f
            setTextColor(MUTED)
            setPadding(0, 0, 0, dp(16))
        })
        content.addView(featureCard("One clear family dashboard", "See a child’s latest shared position, protection state, and important safety events without opening multiple screens."))
        content.addView(featureCard("Thoughtful controls", "Pause access now, set bedtime, approve new apps, and manage selected apps from focused controls."))
        content.addView(featureCard("Visible safety features", "Location sharing remains visible on the child phone. SOS alerts are designed to get the parent’s attention quickly."))
        content.addView(featureCard("Privacy by design", "No message capture, hidden tracking, microphone recording, or browsing-history surveillance."))
        content.addView(Button(this).apply {
            text = "Create or sign in as a parent"
            isAllCaps = false
            textSize = 16f
            minHeight = dp(52)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(BLUE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(16), 0, 0) }
            setOnClickListener { startActivity(android.content.Intent(this@GuestPreviewActivity, ParentModeActivity::class.java)) }
        })
        content.addView(Button(this).apply {
            text = "Back to welcome"
            isAllCaps = false
            textSize = 15f
            setTextColor(BLUE)
            background = outlined()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) }
            setOnClickListener { finish() }
        })
    }

    private fun featureCard(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(Color.WHITE, BORDER)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(4)) }
        addView(TextView(this@GuestPreviewActivity).apply { text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY) })
        addView(TextView(this@GuestPreviewActivity).apply { text = body; textSize = 14f; setTextColor(MUTED); setPadding(0, dp(5), 0, 0) })
    }

    private fun outlined() = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(14).toFloat(); setStroke(dp(1), BLUE) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(18).toFloat(); setStroke(dp(1), stroke) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xFFF6F8FC.toInt()
        const val NAVY = 0xFF112B4E.toInt()
        const val MUTED = 0xFF465266.toInt()
        const val BLUE = 0xFF1366D6.toInt()
        const val BORDER = 0xFFE0E5EE.toInt()
    }
}
