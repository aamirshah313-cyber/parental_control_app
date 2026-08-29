package com.guardianlink

import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.ui.ChildModeActivity
import com.guardianlink.ui.GuestPreviewActivity
import com.guardianlink.ui.ParentModeActivity

/** Clear role selection prevents a child phone from being confused with a parent account screen. */
class MainActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(28))
            setBackgroundColor(BACKGROUND)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BACKGROUND); addView(content) })

        content.addView(TextView(this).apply {
            text = getString(R.string.app_name).uppercase()
            textSize = 12f
            letterSpacing = .14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(BLUE)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        content.addView(TextView(this).apply {
            text = "Guidance that keeps family life moving"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(NAVY)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "Set healthy boundaries, respond to SOS alerts, and see only the safety information you choose to share."
            textSize = 15f
            setTextColor(MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(24))
        })

        content.addView(roleCard("P", "Parent or guardian", "Sign in or create your private family dashboard.", "Continue as parent") {
            startActivity(Intent(this, ParentModeActivity::class.java))
        })
        content.addView(roleCard("C", "Set up a child device", "Use a one-time code from the parent dashboard. Permissions are explained step by step.", "Set up this device") {
            startActivity(Intent(this, ChildModeActivity::class.java))
        })
        content.addView(Button(this).apply {
            text = "Explore the app as a guest"
            isAllCaps = false
            textSize = 15f
            setTextColor(BLUE)
            background = outlined()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener { startActivity(Intent(this@MainActivity, GuestPreviewActivity::class.java)) }
        })
        content.addView(TextView(this).apply {
            text = "For parents and legal guardians only. Location sharing is visible on the child phone and controlled from the family dashboard."
            textSize = 12f
            setTextColor(MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(18), dp(10), 0)
        })
    }

    private fun roleCard(icon: String, title: String, body: String, action: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(16))
        background = rounded(Color.WHITE, BORDER)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(7), 0, dp(5)) }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = icon; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(BLUE)
                background = rounded(0xFFE8F1FF.toInt(), 0xFFE8F1FF.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(this@MainActivity).apply { text = title; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY) })
                addView(TextView(this@MainActivity).apply { text = body; textSize = 14f; setTextColor(MUTED); setPadding(0, dp(3), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(Button(this@MainActivity).apply {
            text = action; isAllCaps = false; textSize = 15f; minHeight = dp(48); setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(BLUE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(15), 0, 0) }
            setOnClickListener { onClick() }
        })
    }

    private fun outlined() = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(14).toFloat(); setStroke(dp(1), BLUE) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(18).toFloat(); setStroke(dp(1), stroke) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xFF17181E.toInt()
        const val NAVY = 0xFFF5F2EA.toInt()
        const val MUTED = 0xFFAFAFBA.toInt()
        const val BLUE = 0xFFD8B65B.toInt()
        const val BORDER = 0xFF2B2D36.toInt()
    }
}
