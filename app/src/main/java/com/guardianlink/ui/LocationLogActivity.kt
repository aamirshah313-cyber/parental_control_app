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
import com.guardianlink.sync.LocationRecord
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import java.util.Locale

/** Location history is deliberately separate from the dashboard, which shows only the latest shared position. */
class LocationLogActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var state: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BACKGROUND); addView(content) })
        val name = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Child device"
        content.addView(TextView(this).apply { text = "LOCATION LOG"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f; setTextColor(BLUE) })
        content.addView(TextView(this).apply { text = "$name check-ins"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(5), 0, dp(5)) })
        content.addView(TextView(this).apply { text = "Past check-ins are kept here so the main dashboard stays focused on the latest shared position."; textSize = 14f; setTextColor(MUTED); setPadding(0, 0, 0, dp(12)) })
        state = messageCard("Loading location history…")
        content.addView(state)
        content.addView(Button(this).apply {
            text = "Refresh log"; isAllCaps = false; textSize = 15f; setTextColor(BLUE); background = outlined()
            layoutParams = margins(8); setOnClickListener { load() }
        })
        load()
    }

    private fun load() {
        val session = ParentSessionStore(this).load() ?: run { state.text = "Sign in again to view location history."; return }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        state.text = "Loading location history…"
        Thread {
            val locations = ParentApi(ParentSessionStore(this).ensureFresh() ?: session).locationHistory(deviceId)
            runOnUiThread { render(locations) }
        }.start()
    }

    private fun render(locations: List<LocationRecord>) {
        // Keep the header, explanation, state, and Refresh action mounted; rebuild only the dynamic location cards.
        while (content.childCount > 5) content.removeViewAt(5)
        if (locations.isEmpty()) { state.text = "No shared check-ins yet. Enable visible location sharing on the child phone, then wait for the next check-in."; return }
        state.text = "${locations.size} most recent shared check-in${if (locations.size == 1) "" else "s"}"
        locations.forEach { content.addView(locationCard(it)) }
    }

    private fun locationCard(location: LocationRecord) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(13), dp(15), dp(13)); background = rounded(0xFF23242C.toInt(), BORDER); layoutParams = margins(8)
        addView(TextView(this@LocationLogActivity).apply { text = location.recordedAt.replace('T', ' ').substringBefore('.'); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY) })
        addView(TextView(this@LocationLogActivity).apply { text = String.format(Locale.US, "%.5f, %.5f  •  ±%s", location.latitude, location.longitude, location.accuracyMeters?.let { "${it.toInt()} m" } ?: "unknown"); textSize = 14f; setTextColor(MUTED); setPadding(0, dp(4), 0, dp(10)) })
        addView(Button(this@LocationLogActivity).apply { text = "Open this check-in in maps"; isAllCaps = false; textSize = 14f; setTextColor(BLUE); background = outlined(); setOnClickListener { MapNavigator.openCoordinates(this@LocationLogActivity, location.latitude, location.longitude) } })
    }

    private fun messageCard(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(MUTED); setPadding(dp(15), dp(13), dp(15), dp(13)); background = rounded(0xFF23242C.toInt(), BORDER) }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun outlined() = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(14).toFloat(); setStroke(dp(1), BLUE) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        val BACKGROUND get() = NoirUi.BACKGROUND
        val NAVY get() = NoirUi.TEXT
        val MUTED get() = NoirUi.MUTED
        val BLUE get() = NoirUi.GOLD
        val BORDER get() = NoirUi.SURFACE_RAISED
        fun intent(context: android.content.Context, deviceId: String, deviceName: String) =
            android.content.Intent(context, LocationLogActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)
    }
}
