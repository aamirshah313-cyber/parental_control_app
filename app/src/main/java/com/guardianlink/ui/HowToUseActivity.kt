package com.guardianlink.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Role-specific guidance that is available in the app, not hidden in external documentation. */
class HowToUseActivity : android.app.Activity() {
    private val isParent get() = intent.getBooleanExtra(EXTRA_PARENT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(content) })
        content.addView(NoirUi.eyebrow(this, "How to use"))
        content.addView(NoirUi.title(this, if (isParent) "Parent guide" else "Child device guide").apply { setPadding(0, dp(5), 0, dp(5)) })
        content.addView(TextView(this).apply { text = if (isParent) "A short guide to the tools in your family dashboard." else "A clear guide to what is enabled on this child device."; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10)) })
        if (isParent) parentGuide(content) else childGuide(content)
        content.addView(NoirUi.secondaryButton(this, "Back") { finish() }.apply { layoutParams = margins(16) })
    }

    private fun parentGuide(content: LinearLayout) {
        content.addView(guideCard("1", "Create your family", "Sign in, create one family, then choose Pair a child. A pairing code is single-use and expires after the time you choose."))
        content.addView(guideCard("2", "Choose a child device", "Each distinct child name appears once. Open a child card to see controls, the latest shared position, and its current protection rules."))
        content.addView(guideCard("3", "Use quick controls", "Pause managed apps, pause all child apps, set a 30-minute pause, or resume access. Use Delivery status to confirm the child device synced."))
        content.addView(guideCard("4", "Set lasting rules", "Manage reported apps, approve new installations, choose bedtime, add browser category filters and blocked keywords, and control whether visible location sharing is enabled."))
        content.addView(guideCard("5", "Stay coordinated", "Quick Messages provides preset pickup and safety updates. Latest shared position is on the dashboard; older check-ins are in Location log."))
        content.addView(guideCard("6", "Respond to alerts", "Keep parent alerts enabled for SOS and app-approval notices. An SOS requires you to contact the child and stop the alarm when handled."))
    }

    private fun childGuide(content: LinearLayout) {
        content.addView(guideCard("1", "Pair this device", "Paste the one-time code from the parent dashboard. If already paired, use Sync rules instead of pairing again."))
        content.addView(guideCard("2", "Activate protection", "Complete Step 5 only with a parent or guardian. Android then asks for notification and Usage Access permissions."))
        content.addView(guideCard("3", "Know what is visible", "Location sharing is optional, visibly indicated by an Android notification, and works only if the parent enables it."))
        content.addView(guideCard("4", "Ask for help", "Use SOS for urgent situations. The parent receives an alarm and should contact you. Use Quick Messages for non-urgent pickup and arrival updates."))
        content.addView(guideCard("5", "Use the supervised browser", "The supervised browser applies parent category, keyword, and YouTube Shorts rules. It does not read private messages or record the microphone/camera."))
    }

    private fun guideCard(number: String, title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(NoirUi.SURFACE, NoirUi.SURFACE_RAISED); layoutParams = margins(7)
        addView(TextView(this@HowToUseActivity).apply { text = number; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = android.view.Gravity.CENTER; setTextColor(NoirUi.BACKGROUND); background = rounded(NoirUi.GOLD, NoirUi.GOLD); layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)) })
        addView(LinearLayout(this@HowToUseActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0); addView(TextView(this@HowToUseActivity).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NoirUi.TEXT) }); addView(TextView(this@HowToUseActivity).apply { text = body; textSize = 13f; setTextColor(NoirUi.MUTED); setPadding(0, dp(3), 0, 0) }) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_PARENT = "parent_mode"
        fun parentIntent(context: android.content.Context) = android.content.Intent(context, HowToUseActivity::class.java).putExtra(EXTRA_PARENT, true)
        fun childIntent(context: android.content.Context) = android.content.Intent(context, HowToUseActivity::class.java)
    }
}
