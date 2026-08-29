package com.guardianlink.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import java.util.Locale

/** A low-frequency, no-key map view. It presents the child's latest check-in, not deceptive continuous tracking. */
class LiveLocationActivity : android.app.Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var details: TextView
    private lateinit var mapPrompt: TextView
    private var latestLatitude: Double? = null
    private var latestLongitude: Double? = null

    private val refresh = object : Runnable {
        override fun run() {
            loadLatestLocation()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); setBackgroundColor(Color.rgb(246, 248, 252)) }
        val name = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Child device"
        root.addView(TextView(this).apply { text = "LATEST SHARED POSITION"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f; setTextColor(Color.rgb(19, 102, 214)) })
        root.addView(TextView(this).apply { text = "$name location"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)); setPadding(0, dp(4), 0, 0) })
        root.addView(TextView(this).apply { text = "Latest check-in only • refreshes every 30 seconds while this screen is open"; setTextColor(Color.rgb(70, 82, 102)); setPadding(0, dp(6), 0, dp(8)) })
        details = TextView(this).apply { setTextColor(Color.rgb(17, 80, 130)); setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.rgb(232, 242, 255)) }
        root.addView(details)
        mapPrompt = TextView(this).apply { text = "Google Maps\nOpen the latest coordinate in Google Maps for directions, navigation, and the full map view."; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)); setPadding(dp(18), dp(24), dp(18), dp(24)); gravity = Gravity.CENTER; background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(224, 229, 238)) } }
        root.addView(mapPrompt, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, dp(10), 0, 0) })
        root.addView(button("Refresh now") { loadLatestLocation() })
        root.addView(button("Open in Google Maps") { latestLatitude?.let { latitude -> latestLongitude?.let { longitude -> openExternalMap(latitude, longitude) } } })
        root.addView(button("View location log") { startActivity(LocationLogActivity.intent(this, intent.getStringExtra(EXTRA_DEVICE_ID) ?: return@button, name)) })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun loadLatestLocation() {
        val session = ParentSessionStore(this).load() ?: run { details.text = "Sign in again to view this location."; return }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        details.text = "Loading latest check-in…"
        Thread {
            val location = ParentApi(ParentSessionStore(this).ensureFresh() ?: session).latestLocation(deviceId)
            runOnUiThread {
                if (location == null) {
                    details.text = "No shared location yet. Enable location sharing on the child phone and start its visible location service."
                    return@runOnUiThread
                }
                latestLatitude = location.latitude
                latestLongitude = location.longitude
                details.text = String.format(Locale.US, "%.5f, %.5f • ±%s • %s", location.latitude, location.longitude, location.accuracyMeters?.let { "${it.toInt()} m" } ?: "unknown", location.recordedAt)
                mapPrompt.text = "Google Maps ready\nTap “Open in Google Maps” to view ${String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)} and start navigation."
            }
        }.start()
    }

    private fun openExternalMap(latitude: Double, longitude: Double) {
        MapNavigator.openCoordinates(this, latitude, longitude)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.rgb(19, 102, 214)); minHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) }
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_DEVICE_NAME = "device_name"
        fun intent(context: android.content.Context, deviceId: String, deviceName: String) =
            Intent(context, LiveLocationActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)
    }
}
