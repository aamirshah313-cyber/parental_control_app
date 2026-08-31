package com.guardianlink.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import com.guardianlink.sync.TimeRequest
import org.json.JSONObject

/** Parent review queue for one-day child screen-time requests. No free-text or sensitive content is collected. */
class TimeRequestsActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var requests: List<TimeRequest> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(content) })
        content.addView(TextView(this).apply { text = "TIME REQUESTS"; textSize = 12f; letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NoirUi.GOLD) })
        content.addView(TextView(this).apply { text = "Extra screen time"; textSize = 27f; typeface = Typeface.create("serif", Typeface.NORMAL); setTextColor(NoirUi.TEXT); setPadding(0, dp(5), 0, dp(4)) })
        content.addView(TextView(this).apply { text = "A granted request adds time only for today. The child must sync before it takes effect."; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10)) })
        status = TextView(this).apply { setPadding(dp(12), dp(10), dp(12), dp(10)); setTextColor(NoirUi.GOLD); setBackgroundColor(NoirUi.SURFACE_RAISED) }
        content.addView(status)
        load()
    }

    private fun load() {
        val session = ParentSessionStore(this).load() ?: run { status.text = "Sign in again to view time requests."; return }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        status.text = "Loading requests…"
        Thread { requests = ParentApi(ParentSessionStore(this).ensureFresh() ?: session).pendingTimeRequests(deviceId); runOnUiThread(::render) }.start()
    }

    private fun render() {
        while (content.childCount > 4) content.removeViewAt(4)
        if (requests.isEmpty()) {
            status.text = "No pending requests."
            return
        }
        status.text = "${requests.size} request${if (requests.size == 1) "" else "s"} awaiting your decision."
        requests.forEach { request ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(NoirUi.SURFACE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), NoirUi.SURFACE_RAISED) }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) }
            }
            card.addView(TextView(this).apply { text = "Request for ${request.requestedMinutes} extra minutes"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NoirUi.TEXT) })
            card.addView(TextView(this).apply { text = "Received ${request.createdAt.replace('T', ' ').substringBefore('.')}"; textSize = 13f; setTextColor(NoirUi.MUTED); setPadding(0, dp(3), 0, dp(8)) })
            card.addView(row("Grant ${request.requestedMinutes} min", { resolve(request, true) }, Pair("Decline", { resolve(request, false) })))
            content.addView(card)
        }
    }

    private fun resolve(request: TimeRequest, grant: Boolean) {
        val session = ParentSessionStore(this).load() ?: return
        status.text = if (grant) "Granting extra time…" else "Declining request…"
        Thread {
            val api = ParentApi(ParentSessionStore(this).ensureFresh() ?: session)
            // Keep a request pending if the companion command could not be queued; the parent can safely retry.
            val commandSent = !grant || api.sendCommand(request.deviceId, "grant_time", payload = JSONObject().put("minutes", request.requestedMinutes))
            val resolved = commandSent && api.resolveTimeRequest(request.id, if (grant) "granted" else "declined", if (grant) request.requestedMinutes else null)
            runOnUiThread {
                status.text = if (resolved && commandSent) if (grant) "Granted. The child receives ${request.requestedMinutes} extra minutes after the next sync." else "Request declined." else "Could not update the request. Try again."
                if (resolved && commandSent) load()
            }
        }.start()
    }

    private fun row(first: String, firstAction: () -> Unit, second: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(compact(first, firstAction), LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(4), 0) })
        addView(compact(second.first, second.second), LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), 0, 0, 0) })
    }
    private fun compact(text: String, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 13f; isAllCaps = false; setTextColor(NoirUi.BACKGROUND); background = NoirUi.interactiveBackground(this@TimeRequestsActivity, NoirUi.GOLD, NoirUi.GOLD_DIM, NoirUi.GOLD_DIM, 14); setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val EXTRA_DEVICE_ID = "device_id"; fun intent(context: android.content.Context, deviceId: String) = android.content.Intent(context, TimeRequestsActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId) }
}
