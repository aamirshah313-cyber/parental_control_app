package com.guardianlink.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.FamilyMessage
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import java.util.Locale

/** Preset-only in-app family messages. It deliberately does not impersonate or send carrier SMS. */
class QuickMessagesActivity : android.app.Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var state: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadMessages(); handler.postDelayed(this, 15_000) }
    }
    private val isParent get() = intent.getBooleanExtra(EXTRA_PARENT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BACKGROUND); addView(root) })
        val person = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: if (isParent) "Child" else "Parent"
        root.addView(TextView(this).apply { text = "QUICK MESSAGES"; textSize = 12f; letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(BLUE) })
        root.addView(TextView(this).apply { text = "Stay coordinated with $person"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply { text = "Preset in-app messages only — no phone number, carrier SMS, or typed chat."; textSize = 14f; setTextColor(MUTED); setPadding(0, 0, 0, dp(10)) })
        state = infoCard("Loading messages…")
        root.addView(state)
        root.addView(TextView(this).apply { text = if (isParent) "Send to child" else "Send to parent"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(16), 0, dp(6)) })
        quickTemplates().chunked(2).forEach { row ->
            root.addView(templateRow(row))
        }
        root.addView(Button(this).apply {
            text = "Refresh conversation"; isAllCaps = false; textSize = 14f; setTextColor(BLUE); background = outlined(); layoutParams = margins(10); setOnClickListener { loadMessages() }
        })
        root.addView(TextView(this).apply { text = "Conversation"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NAVY); setPadding(0, dp(18), 0, dp(4)) })
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(messages)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun quickTemplates(): List<QuickTemplate> = if (isParent) listOf(
        QuickTemplate("driver_on_way", "Driver is on the way to pick you up from school."),
        QuickTemplate("at_pickup", "I am here for pickup."),
        QuickTemplate("running_late", "I am running late. Please wait safely."),
        QuickTemplate("parent_call_me", "Please call me when you can.")
    ) else listOf(
        QuickTemplate("waiting_at_stop", "I am waiting for the driver at the stop."),
        QuickTemplate("reached_school", "I have reached school safely."),
        QuickTemplate("need_pickup", "I need a pickup, please."),
        QuickTemplate("child_call_me", "Please call me when you can.")
    )

    private fun templateRow(items: List<QuickTemplate>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = margins(4)
        items.forEachIndexed { index, template ->
            addView(Button(this@QuickMessagesActivity).apply {
                text = template.body; textSize = 12f; isAllCaps = false; gravity = Gravity.CENTER; setTextColor(BLUE); background = outlined(); minHeight = dp(64)
                setOnClickListener { send(template) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(if (index == 0) 0 else dp(4), 0, if (index == items.lastIndex) 0 else dp(4), 0) })
        }
        if (items.size == 1) addView(android.view.View(this@QuickMessagesActivity), LinearLayout.LayoutParams(0, 1, 1f))
    }

    private fun send(template: QuickTemplate) {
        state.text = "Sending message…"
        Thread {
            val sent = if (isParent) {
                val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load()
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
                session != null && deviceId != null && familyId != null && ParentApi(session).sendQuickMessage(familyId, deviceId, template.key, template.body)
            } else {
                DeviceSessionStore(this).api()?.sendQuickMessage(template.key, template.body) == true
            }
            runOnUiThread {
                state.text = if (sent) "Message sent. It appears when the other device refreshes." else "Message could not be sent. Run the quick-messages Supabase migration and check the connection."
                if (sent) loadMessages()
            }
        }.start()
    }

    private fun loadMessages() {
        Thread {
            val result: List<FamilyMessage>? = if (isParent) {
                val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load()
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                if (session == null || deviceId == null) null else ParentApi(session).quickMessages(deviceId)
            } else {
                DeviceSessionStore(this).api()?.quickMessages()?.map { FamilyMessage(it.id, it.senderRole, it.templateKey, it.body, it.createdAt) }
            }
            runOnUiThread { render(result) }
        }.start()
    }

    private fun render(items: List<FamilyMessage>?) {
        messages.removeAllViews()
        when {
            items == null -> state.text = "Connect this device and sign in again to use Quick Messages."
            items.isEmpty() -> state.text = "No messages yet. Use a preset above to send the first update."
            else -> {
                state.text = "${items.size} recent message${if (items.size == 1) "" else "s"} • refreshes while this screen is open"
                items.forEach { messages.addView(messageCard(it)) }
            }
        }
    }

    private fun messageCard(message: FamilyMessage) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val mine = (isParent && message.senderRole == "parent") || (!isParent && message.senderRole == "child")
        setPadding(dp(13), dp(10), dp(13), dp(10)); background = rounded(if (mine) 0xFFE8F1FF.toInt() else Color.WHITE, if (mine) 0xFFABD0F4.toInt() else BORDER)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(if (mine) dp(34) else 0, dp(6), if (mine) 0 else dp(34), 0) }
        addView(TextView(this@QuickMessagesActivity).apply { text = if (message.senderRole == "parent") "Parent" else "Child"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(BLUE) })
        addView(TextView(this@QuickMessagesActivity).apply { text = message.body; textSize = 15f; setTextColor(NAVY); setPadding(0, dp(3), 0, dp(3)) })
        addView(TextView(this@QuickMessagesActivity).apply { text = message.createdAt.replace('T', ' ').substringBefore('.'); textSize = 11f; setTextColor(MUTED) })
    }

    private fun infoCard(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(MUTED); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.WHITE, BORDER) }
    private fun outlined() = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0xFFABD0F4.toInt()) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class QuickTemplate(val key: String, val body: String)

    companion object {
        private const val EXTRA_PARENT = "parent_mode"
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_DEVICE_NAME = "device_name"
        private const val EXTRA_FAMILY_ID = "family_id"
        private const val BACKGROUND = 0xFFF6F8FC.toInt()
        private const val NAVY = 0xFF112B4E.toInt()
        private const val MUTED = 0xFF465266.toInt()
        private const val BLUE = 0xFF1366D6.toInt()
        private const val BORDER = 0xFFE0E5EE.toInt()

        fun parentIntent(context: android.content.Context, familyId: String, deviceId: String, deviceName: String) =
            android.content.Intent(context, QuickMessagesActivity::class.java)
                .putExtra(EXTRA_PARENT, true).putExtra(EXTRA_FAMILY_ID, familyId).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)

        fun childIntent(context: android.content.Context) = android.content.Intent(context, QuickMessagesActivity::class.java)
    }
}
