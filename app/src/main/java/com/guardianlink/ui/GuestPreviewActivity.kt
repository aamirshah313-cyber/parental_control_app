package com.guardianlink.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A local, interactive product preview. It contains no account, child, or location data. */
class GuestPreviewActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var paused = false
    private enum class GuestPage { OVERVIEW, CONTROLS, SAFETY, FAMILY, APPS, TIME, CHAT }
    private var page = GuestPage.OVERVIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(28)); setBackgroundColor(BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BACKGROUND); addView(content) })
        // Guest mode opens directly into the working local demo. It never asks for a login to explore the product.
        showDashboard()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (page == GuestPage.OVERVIEW) finish() else showDashboard()
    }

    private fun showDashboard() {
        page = GuestPage.OVERVIEW
        content.removeAllViews()
        content.addView(eyebrow("GUEST MODE • FAMILY DASHBOARD"))
        content.addView(title("Good afternoon, Sam"))
        content.addView(note("Demo family • 1 child device linked • Explore every section locally. Nothing is sent, stored, or monitored in Guest mode."))
        content.addView(navRow("Overview" to { showDashboard() }, "Controls" to { showControls() }))
        content.addView(navRow("Safety" to { showSafety() }, "Family" to { showFamily() }))
        content.addView(section("Maya’s phone"))
        content.addView(note("Protection active • 42% battery\nLast check-in: just now • 68 minutes today"))
        content.addView(navRow(if (paused) "Resume access" to { paused = false; showDashboard() } else "Pause all apps" to { paused = true; showDashboard() }, "View location" to { showSafety() }))
        content.addView(section("Latest shared position"))
        content.addView(note("School area • updated 3 minutes ago\nA real dashboard opens Google Maps only when location sharing is enabled."))
        status = note(if (paused) "Demo: child access is paused." else "Demo: access is currently available.")
        content.addView(status)
    }

    private fun showControls() {
        page = GuestPage.CONTROLS
        content.removeAllViews(); content.addView(eyebrow("DEMO • CONTROLS")); content.addView(title("Maya’s controls")); content.addView(note("Each section has a single purpose. In the real app, changes are sent to the paired child device."))
        content.addView(navRow("Pause & bedtime" to { paused = !paused; showControls() }, "App controls" to { showAppDemo() }))
        content.addView(navRow("Daily allowance" to { showMessage("Demo: daily allowance editor opens here.") }, "Time requests" to { showTimeDemo() }))
        content.addView(note(if (paused) "Demo state: all child apps are paused." else "Demo state: access is available. Tap Pause & bedtime to preview an instant stop."))
        content.addView(secondary("Back to overview") { showDashboard() })
    }

    private fun showAppDemo() {
        page = GuestPage.APPS
        content.removeAllViews(); content.addView(eyebrow("DEMO • APP CONTROLS")); content.addView(title("Manage installed apps")); content.addView(note("A real child device reports its launchable apps here. The parent can select one or many to allow, block, add to pause, or set a daily limit."))
        listOf("YouTube — 30 min/day", "Chrome — Allowed", "Roblox — Awaiting approval").forEach { content.addView(note(it)) }
        content.addView(navRow("Allow selected" to { showMessage("Demo: selected app allowed.") }, "Block selected" to { showMessage("Demo: selected app blocked.") }))
        content.addView(secondary("Back to controls") { showControls() })
    }

    private fun showTimeDemo() {
        page = GuestPage.TIME
        content.removeAllViews(); content.addView(eyebrow("DEMO • TIME REQUESTS")); content.addView(title("Extra screen time")); content.addView(note("A child can request time. In a signed-in family, Grant queues a one-day bonus that the child receives on its next sync."))
        content.addView(section("Request for 30 extra minutes"))
        content.addView(navRow("Grant request" to { showMessage("Demo: 30 minutes granted for today.") }, "Decline" to { showMessage("Demo: request declined.") }))
        content.addView(secondary("Back to controls") { showControls() })
    }

    private fun showSafety() {
        page = GuestPage.SAFETY
        content.removeAllViews(); content.addView(eyebrow("DEMO • SAFETY")); content.addView(title("Safety & location")); content.addView(note("Location sharing, safe places, SOS, and family browser rules are grouped here. They are visible to the child and enabled only with the appropriate Android permissions."))
        content.addView(navRow("Open map" to { showMessage("Demo: Google Maps opens for the latest shared position.") }, "Safe places" to { showMessage("Demo: school and home boundaries are managed here.") }))
        content.addView(navRow("Browser rules" to { showMessage("Demo: website, YouTube Shorts, and keyword rules are configured here.") }, "SOS alerts" to { showMessage("Demo: SOS uses a visible parent alarm and notification.") }))
        content.addView(secondary("Back to overview") { showDashboard() })
    }

    private fun showFamily() {
        page = GuestPage.FAMILY
        content.removeAllViews(); content.addView(eyebrow("GUEST MODE • FAMILY")); content.addView(title("Family connection")); content.addView(note("This guest area demonstrates communication, alerts, and help. It is fully local, so no parent sign-in is needed to explore it."))
        content.addView(navRow("Demo chat" to { showChatDemo() }, "Quick messages" to { showMessage("Demo: driver pickup, safe arrival, and call-me templates appear here.") }))
        content.addView(navRow("Guardian Guide" to { startActivity(GuardianGuideActivity.intent(this, true)) }, "Parent alerts" to { showMessage("Demo: SOS and approval alerts can be previewed after a family is paired.") }))
        content.addView(secondary("Exit Guest mode") { finish() })
        content.addView(secondary("Sign in for real family controls") { startActivity(android.content.Intent(this, ParentModeActivity::class.java)) })
    }

    private fun showChatDemo() {
        page = GuestPage.CHAT
        content.removeAllViews(); content.addView(eyebrow("DEMO • FAMILY CHAT")); content.addView(title("Stay connected")); content.addView(note("Parent: Driver is on the way.\nChild: I am waiting at the stop.\n\nIn a signed-in family this screen sends private typed messages and short voice notes between the selected parent and child device."))
        content.addView(navRow("Send demo text" to { showMessage("Demo: message sent.") }, "Play voice note" to { showMessage("Demo: voice-note player starts here.") }))
        content.addView(secondary("Back to Family") { showFamily() })
    }

    private fun showMessage(text: String) { android.app.AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show() }
    private fun eyebrow(text: String) = TextView(this).apply { this.text = text; textSize = 12f; letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(BLUE) }
    private fun title(text: String) = TextView(this).apply { this.text = text; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(6), 0, dp(8)) }
    private fun section(text: String) = TextView(this).apply { this.text = text; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(18), 0, dp(6)) }
    private fun note(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(MUTED); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(0xFF23242C.toInt(), BORDER); layoutParams = margins(0, 8) }
    private fun primary(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setTextColor(BACKGROUND); background = interactive(BLUE, 0xFF7D6A3B.toInt(), 0xFF7D6A3B.toInt()); setOnClickListener { action() }; layoutParams = margins(12, 0) }
    private fun secondary(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setTextColor(NAVY); background = interactive(0xFF23242C.toInt(), 0xFF2B2D36.toInt(), BORDER); setOnClickListener { action() }; layoutParams = margins(8, 0) }
    private fun navRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = margins(4, 0); addView(secondary(first.first, first.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) }); addView(secondary(second.first, second.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) }) }
    private fun margins(top: Int, bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun interactive(normal: Int, active: Int, stroke: Int) = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(0xFF7D6A3B.toInt(), BLUE))
        addState(intArrayOf(android.R.attr.state_hovered), rounded(active, BLUE))
        addState(intArrayOf(), rounded(normal, stroke))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private companion object { const val BACKGROUND = 0xFF17181E.toInt(); const val NAVY = 0xFFF5F2EA.toInt(); const val MUTED = 0xFFAFAFBA.toInt(); const val BLUE = 0xFFD8B65B.toInt(); const val BORDER = 0xFF2B2D36.toInt() }
}
