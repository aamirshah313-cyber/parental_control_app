package com.guardianlink.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.FamilyChatMessage
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore

/** Preset updates are a latest-status view of the same family conversation used by Family Chat. */
class QuickMessagesActivity : android.app.Activity() {
    private lateinit var latest: LinearLayout
    private lateinit var state: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { loadMessages(); handler.postDelayed(this, 15_000) } }
    private val isParent get() = intent.getBooleanExtra(EXTRA_PARENT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(root) })
        val person = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: if (isParent) "Child" else "Parent"
        root.addView(NoirUi.eyebrow(this, "QUICK UPDATES"))
        root.addView(NoirUi.title(this, "Update $person fast").apply { setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply { text = "Send a short preset for pickup or safety. This screen shows only the latest update, so repeated taps do not build a confusing stack. Use Family Chat for conversation history and voice notes."; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10)) })
        root.addView(NoirUi.secondaryButton(this, "Open Family Chat") {
            if (isParent) startActivity(FamilyChatActivity.parentIntent(this, intent.getStringExtra(EXTRA_FAMILY_ID) ?: return@secondaryButton, intent.getStringExtra(EXTRA_DEVICE_ID) ?: return@secondaryButton, person))
            else startActivity(FamilyChatActivity.childIntent(this))
        }.apply { layoutParams = margins(4) })
        state = statusCard("Loading latest update…")
        root.addView(state)
        root.addView(sectionTitle(if (isParent) "Send to child" else "Send to parent"))
        quickTemplates().chunked(2).forEach { root.addView(templateRow(it)) }
        root.addView(NoirUi.secondaryButton(this, "Refresh latest update") { loadMessages() }.apply { layoutParams = margins(8) })
        root.addView(sectionTitle("Latest update"))
        latest = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(latest)
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
        orientation = LinearLayout.HORIZONTAL; layoutParams = margins(4)
        items.forEachIndexed { index, template ->
            addView(NoirUi.secondaryButton(this@QuickMessagesActivity, template.body) { send(template) }.apply { textSize = 12f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(68), 1f).apply { setMargins(if (index == 0) 0 else dp(4), 0, if (index == items.lastIndex) 0 else dp(4), 0) })
        }
    }

    private fun send(template: QuickTemplate) {
        state.text = "Sending update…"
        Thread {
            val sent = if (isParent) {
                val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load()
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
                session != null && deviceId != null && familyId != null && ParentApi(session).sendChatMessage(familyId, deviceId, template.body, messageKind = MESSAGE_KIND_QUICK, templateKey = template.key)
            } else DeviceSessionStore(this).api()?.sendChatMessage(template.body, messageKind = MESSAGE_KIND_QUICK, templateKey = template.key) == true
            runOnUiThread {
                state.text = if (sent) "Update sent. It is also visible in Family Chat on both devices." else "Update could not be sent. Run the unified-communication migration and check the connection."
                if (sent) loadMessages()
            }
        }.start()
    }

    private fun loadMessages() {
        Thread {
            val result: List<FamilyChatMessage>? = if (isParent) {
                val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load()
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                if (session == null || deviceId == null) null else ParentApi(session).chatMessages(deviceId)
            } else DeviceSessionStore(this).api()?.chatMessages()?.map { FamilyChatMessage(it.id, it.senderRole, it.body, it.audioPath, it.createdAt, it.messageKind, it.templateKey) }
            runOnUiThread { render(result) }
        }.start()
    }

    private fun render(items: List<FamilyChatMessage>?) {
        latest.removeAllViews()
        when {
            items == null -> state.text = "Connect this device and sign in again to use Quick Updates."
            items.filter { it.messageKind == MESSAGE_KIND_QUICK }.isEmpty() -> state.text = "No quick update yet. Send the first preset above."
            else -> {
                state.text = "Latest update • refreshes while this screen is open"
                // Quick Updates is intentionally a status channel, not a duplicate message history.
                latest.addView(messageCard(items.filter { it.messageKind == MESSAGE_KIND_QUICK }.maxByOrNull { it.createdAt } ?: return))
            }
        }
    }

    private fun messageCard(message: FamilyChatMessage) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val mine = (isParent && message.senderRole == "parent") || (!isParent && message.senderRole == "child")
        setPadding(dp(14), dp(12), dp(14), dp(12)); background = NoirUi.rounded(this@QuickMessagesActivity, if (mine) NoirUi.SURFACE_RAISED else NoirUi.SURFACE, if (mine) NoirUi.GOLD_DIM else NoirUi.SURFACE_RAISED, 16)
        addView(TextView(this@QuickMessagesActivity).apply { text = if (message.senderRole == "parent") "Parent" else "Child"; textSize = 12f; setTextColor(NoirUi.GOLD) })
        addView(TextView(this@QuickMessagesActivity).apply { text = message.body; textSize = 16f; setTextColor(NoirUi.TEXT); setPadding(0, dp(4), 0, dp(4)) })
        addView(TextView(this@QuickMessagesActivity).apply { text = message.createdAt.replace('T', ' ').substringBefore('.'); textSize = 11f; setTextColor(NoirUi.MUTED) })
    }

    private fun statusCard(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(NoirUi.GOLD); setPadding(dp(14), dp(12), dp(14), dp(12)); background = NoirUi.rounded(this@QuickMessagesActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14); layoutParams = margins(6) }
    private fun sectionTitle(text: String) = TextView(this).apply { this.text = text; textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(NoirUi.TEXT); setPadding(0, dp(14), 0, dp(5)) }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)
    private data class QuickTemplate(val key: String, val body: String)

    companion object {
        private const val MESSAGE_KIND_QUICK = "quick_update"
        private const val EXTRA_PARENT = "parent_mode"
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_DEVICE_NAME = "device_name"
        private const val EXTRA_FAMILY_ID = "family_id"
        fun parentIntent(context: Context, familyId: String, deviceId: String, deviceName: String) = android.content.Intent(context, QuickMessagesActivity::class.java).putExtra(EXTRA_PARENT, true).putExtra(EXTRA_FAMILY_ID, familyId).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)
        fun childIntent(context: Context) = android.content.Intent(context, QuickMessagesActivity::class.java)
    }
}
