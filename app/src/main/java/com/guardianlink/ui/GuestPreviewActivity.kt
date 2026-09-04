package com.guardianlink.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Guest Mode: local product exploration, not a parent dashboard. It intentionally looks and
 * navigates differently from ParentModeActivity/ChildModeActivity -- a persistent "Guest Mode"
 * badge, a distinct teal accent layered on the shared adaptive NoirUi base (so it still respects
 * light/dark, but is never mistaken for a signed-in screen), a flat card-based home instead of a
 * multi-level dashboard, and explicit locked previews for the two features that only make sense
 * with a real family (location, app approvals). No network calls are made from this screen and
 * no real family/child/location/approval/chat/device data is ever read here.
 */
class GuestPreviewActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var paused = false
    private enum class GuestPage { HOME, CONTROLS, SAFETY, APPS, TIME, CHAT }
    private var page = GuestPage.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(28)); setBackgroundColor(BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BACKGROUND); addView(content) })
        showHome()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (page == GuestPage.HOME) finish() else showHome()
    }

    private fun showHome() {
        page = GuestPage.HOME
        content.removeAllViews()
        content.addView(guestBadge())
        content.addView(TextView(this).apply { text = "See what Guardian Link does"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(10), 0, dp(4)) })
        content.addView(TextView(this).apply {
            text = "A local, no-account walkthrough. Nothing here is sent, saved, or monitored -- create a family to use it for real."
            textSize = 14f; setTextColor(MUTED); setPadding(0, 0, 0, dp(14))
        })
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = margins(0, 4)
            addView(primary("Create account") { startActivity(android.content.Intent(this@GuestPreviewActivity, ParentModeActivity::class.java)) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(0, 0, dp(4), 0) })
            addView(secondary("Sign in") { startActivity(android.content.Intent(this@GuestPreviewActivity, ParentModeActivity::class.java)) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(4), 0, 0, 0) })
        })
        content.addView(TextView(this).apply { text = "Explore a feature"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(20), 0, dp(8)) })
        content.addView(previewCard("Parent controls", "Pause access, manage apps, set daily limits and bedtime.") { showControls() })
        content.addView(previewCard("Family chat & quick messages", "Private typed messages, voice notes, and one-tap templates.") { showChatDemo() })
        content.addView(previewCard("Safety & browser rules", "Category filters, keyword rules, and SOS alerts.") { showSafety() })
        content.addView(lockedCard("Location sharing", "Shows a real child's live position and check-in history. Requires a signed-in family so it never shows anyone's actual location here."))
        content.addView(lockedCard("App approval requests", "Shows a real child's install/unblock/enable requests and lets a parent decide them. Requires a signed-in family for the same reason."))
        content.addView(secondary("Exit Guest mode") { finish() }.apply { layoutParams = margins(18, 0) })
    }

    private fun showControls() {
        page = GuestPage.CONTROLS
        content.removeAllViews(); content.addView(guestBadge()); content.addView(pageTitle("Parent controls")); content.addView(note("Each section has a single purpose. In the real app, changes are sent to the paired child device."))
        content.addView(Switch(this).apply {
            text = "Pause child access (demo)"; textSize = 15f; setTextColor(NAVY); isChecked = paused
            setOnCheckedChangeListener { _, enabled -> paused = enabled; showControls() }
        })
        content.addView(secondary("App controls") { showAppDemo() })
        content.addView(navRow("Daily allowance" to { showMessage("Demo: daily allowance editor opens here.") }, "Time requests" to { showTimeDemo() }))
        content.addView(note(if (paused) "Demo state: all child apps are paused." else "Demo state: access is available. Tap Pause & bedtime to preview an instant stop."))
        content.addView(secondary("Back to preview home") { showHome() })
    }

    private fun showAppDemo() {
        page = GuestPage.APPS
        content.removeAllViews(); content.addView(guestBadge()); content.addView(pageTitle("Manage installed apps")); content.addView(note("A real child device reports its launchable apps here. The parent can select one or many to allow, block, add to pause, or set a daily limit. New installs -- and unblock/enable requests -- wait for parent approval first."))
        listOf("YouTube — 30 min/day", "Chrome — Allowed", "Roblox — Awaiting approval").forEach { content.addView(note(it)) }
        content.addView(navRow("Allow selected" to { showMessage("Demo: selected app allowed.") }, "Block selected" to { showMessage("Demo: selected app blocked.") }))
        content.addView(secondary("Back to controls") { showControls() })
    }

    private fun showTimeDemo() {
        page = GuestPage.TIME
        content.removeAllViews(); content.addView(guestBadge()); content.addView(pageTitle("Extra screen time")); content.addView(note("A child can request time. In a signed-in family, Grant queues a one-day bonus that the child receives on its next sync."))
        content.addView(section("Request for 30 extra minutes"))
        content.addView(navRow("Grant request" to { showMessage("Demo: 30 minutes granted for today.") }, "Decline" to { showMessage("Demo: request declined.") }))
        content.addView(secondary("Back to controls") { showControls() })
    }

    private fun showSafety() {
        page = GuestPage.SAFETY
        content.removeAllViews(); content.addView(guestBadge()); content.addView(pageTitle("Safety & location")); content.addView(note("Location sharing, safe places, SOS, and family browser rules are grouped here. They are visible to the child and enabled only with the appropriate Android permissions."))
        content.addView(navRow("Browser rules" to { showMessage("Demo: website, YouTube Shorts, and keyword rules are configured here.") }, "SOS alerts" to { showMessage("Demo: SOS uses a visible parent alarm and notification.") }))
        content.addView(lockedCard("Location sharing", "Requires a signed-in family -- see the card on the preview home."))
        content.addView(secondary("Back to preview home") { showHome() })
    }

    private fun showChatDemo() {
        page = GuestPage.CHAT
        content.removeAllViews(); content.addView(guestBadge()); content.addView(pageTitle("Stay connected")); content.addView(note("Parent: Driver is on the way.\nChild: I am waiting at the stop.\n\nIn a signed-in family this screen sends private typed messages and short voice notes between the selected parent and child device."))
        content.addView(navRow("Send demo text" to { showMessage("Demo: message sent.") }, "Play voice note" to { showMessage("Demo: voice-note player starts here.") }))
        content.addView(secondary("Back to preview home") { showHome() })
    }

    private fun showMessage(text: String) { NoirUi.dialogBuilder(this).setMessage(text).setPositiveButton("OK", null).show() }

    /** The one element every guest screen shares: an unmistakable, non-authenticated indicator. */
    private fun guestBadge() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(6)); layoutParams = margins(0, 0)
        background = GradientDrawable().apply { setColor(GUEST_ACCENT_DIM); cornerRadius = dp(20).toFloat(); setStroke(dp(1), GUEST_ACCENT) }
        addView(TextView(this@GuestPreviewActivity).apply { text = "●"; textSize = 10f; setTextColor(GUEST_ACCENT); setPadding(0, 0, dp(6), 0) })
        addView(TextView(this@GuestPreviewActivity).apply { text = "GUEST MODE — LOCAL PREVIEW, NOT SIGNED IN"; textSize = 11f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(GUEST_ACCENT) })
    }

    private fun previewCard(title: String, body: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); layoutParams = margins(0, 8)
        background = interactiveCard(SURFACE, SURFACE_RAISED, GUEST_ACCENT_DIM)
        addView(TextView(this@GuestPreviewActivity).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY) })
        addView(TextView(this@GuestPreviewActivity).apply { text = body; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(3), 0, dp(6)) })
        addView(TextView(this@GuestPreviewActivity).apply { text = "PREVIEW →"; textSize = 11f; letterSpacing = .06f; typeface = Typeface.DEFAULT_BOLD; setTextColor(GUEST_ACCENT) })
        setOnClickListener { action() }
    }

    /** Deliberately not a full page: a locked card is the whole story for a real-data feature in Guest mode. */
    private fun lockedCard(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); layoutParams = margins(0, 8)
        background = rounded(SURFACE, BORDER)
        addView(LinearLayout(this@GuestPreviewActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@GuestPreviewActivity).apply { text = "🔒 "; textSize = 15f; setTextColor(MUTED) })
            addView(TextView(this@GuestPreviewActivity).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(MUTED) })
        })
        addView(TextView(this@GuestPreviewActivity).apply { text = body; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(3), 0, dp(8)) })
        addView(secondary("Sign in to use this") { startActivity(android.content.Intent(this@GuestPreviewActivity, ParentModeActivity::class.java)) })
    }

    private fun pageTitle(text: String) = TextView(this).apply { this.text = text; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(10), 0, dp(6)) }
    private fun section(text: String) = TextView(this).apply { this.text = text; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(18), 0, dp(6)) }
    private fun note(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(MUTED); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(SURFACE, BORDER); layoutParams = margins(0, 8) }
    private fun primary(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setTextColor(BACKGROUND); background = interactive(GUEST_ACCENT, GUEST_ACCENT_DIM, GUEST_ACCENT_DIM); setOnClickListener { action() } }
    private fun secondary(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setTextColor(NAVY); background = interactive(SURFACE, SURFACE_RAISED, BORDER); setOnClickListener { action() }; layoutParams = margins(8, 0) }
    private fun navRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = margins(4, 0); addView(secondary(first.first, first.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) }); addView(secondary(second.first, second.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) }) }
    private fun margins(top: Int, bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun interactive(normal: Int, active: Int, stroke: Int) = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(GUEST_ACCENT_DIM, GUEST_ACCENT))
        addState(intArrayOf(android.R.attr.state_hovered), rounded(active, GUEST_ACCENT))
        addState(intArrayOf(), rounded(normal, stroke))
    }
    private fun interactiveCard(normal: Int, active: Int, stroke: Int) = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(active, GUEST_ACCENT))
        addState(intArrayOf(), rounded(normal, stroke))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private companion object {
        val BACKGROUND get() = NoirUi.BACKGROUND
        val SURFACE get() = NoirUi.SURFACE
        val SURFACE_RAISED get() = NoirUi.SURFACE_RAISED
        val NAVY get() = NoirUi.TEXT
        val MUTED get() = NoirUi.MUTED
        val BORDER get() = NoirUi.SURFACE_RAISED
        /** A teal accent, deliberately distinct from NoirUi.GOLD, so guest screens read as their
         * own visual mode at a glance while still respecting light/dark. */
        val GUEST_ACCENT get() = if (NoirUi.isDarkCached) 0xFF4FD1C5.toInt() else 0xFF0F766E.toInt()
        val GUEST_ACCENT_DIM get() = if (NoirUi.isDarkCached) 0xFF2A5B56.toInt() else 0xFFB7E4DE.toInt()
    }
}
