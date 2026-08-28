package com.guardianlink.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.sync.DeviceEvent
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore

/** A privacy-minimised, parent-readable event timeline for one child device. */
class ActivityTimelineActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var state: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); setBackgroundColor(Color.rgb(246, 248, 252)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 248, 252)); addView(content) })
        val name = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Child device"
        content.addView(TextView(this).apply { text = "$name activity"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)); gravity = Gravity.CENTER_HORIZONTAL })
        content.addView(TextView(this).apply { text = "Important safety and protection events only — no browsing history or message content."; setTextColor(Color.rgb(70, 82, 102)); setPadding(0, dp(10), 0, dp(12)) })
        state = TextView(this).apply { setTextColor(Color.rgb(17, 80, 130)); setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.rgb(232, 242, 255)); text = "Loading activity…" }
        content.addView(state)
        loadEvents()
    }

    private fun loadEvents() {
        val session = ParentSessionStore(this).load() ?: run { state.text = "Sign in again to view activity."; return }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        Thread {
            val events = ParentApi(ParentSessionStore(this).ensureFresh() ?: session).recentEvents(deviceId)
            runOnUiThread {
                if (events.isEmpty()) state.text = "No safety events recorded yet."
                else {
                    state.text = "Latest ${events.size} event${if (events.size == 1) "" else "s"}"
                    events.forEach { content.addView(eventCard(it)) }
                }
            }
        }.start()
    }

    private fun eventCard(event: DeviceEvent) = TextView(this).apply {
        text = "${eventTitle(event)}\n${event.createdAt.replace('T', ' ').substringBefore('.')}"
        textSize = 15f; setTextColor(Color.rgb(35, 50, 70)); setPadding(dp(14), dp(12), dp(14), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), Color.rgb(224, 229, 238)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) }
    }

    private fun eventTitle(event: DeviceEvent): String = when (event.eventType) {
        "sos" -> "SOS alert — ${event.details.optString("message", "Child pressed SOS")}" 
        "safe_place_entered" -> "Arrived at safe place: ${event.details.optString("name", "Saved place")}" 
        "safe_place_exited" -> "Left safe place: ${event.details.optString("name", "Saved place")}" 
        "app_installed" -> "New app detected: ${event.details.optString("package_name", "Unknown app")}" 
        "shorts_block" -> "YouTube Shorts blocked in supervised browser"
        "keyword_block" -> "Blocked keyword detected in supervised browser"
        "schedule_block" -> "Bedtime or schedule block applied"
        "location_update" -> "Location check-in recorded"
        else -> event.eventType.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_DEVICE_NAME = "device_name"
        fun intent(context: android.content.Context, deviceId: String, deviceName: String) =
            android.content.Intent(context, ActivityTimelineActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)
    }
}
