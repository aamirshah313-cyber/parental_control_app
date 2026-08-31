package com.guardianlink.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.FamilyNotification
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore

/** A durable, recipient-specific inbox. It is independent of Android notification permission. */
class NotificationsActivity : android.app.Activity() {
    private lateinit var state: TextView
    private lateinit var notificationList: LinearLayout
    private var current: List<FamilyNotification> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { loadNotifications(); handler.postDelayed(this, 12_000) } }
    private val isParent get() = intent.getBooleanExtra(EXTRA_PARENT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(root) })
        root.addView(NoirUi.eyebrow(this, "FAMILY INBOX"))
        root.addView(NoirUi.title(this, "Notifications").apply { setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = if (isParent) "A private family-wide inbox for every child update. It is never limited to the dashboard profile currently selected." else "A private record of updates sent to this device. It works even when Android alerts are muted; use Refresh if you have just sent a message."
            textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(8))
        })
        state = TextView(this).apply { text = "Loading notifications…"; textSize = 14f; setTextColor(NoirUi.GOLD); setPadding(dp(14), dp(11), dp(14), dp(11)); background = NoirUi.rounded(this@NotificationsActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14) }
        root.addView(state)
        root.addView(NoirUi.secondaryButton(this, "Refresh notifications") { loadNotifications() }.apply { layoutParams = margins(8) })
        root.addView(NoirUi.secondaryButton(this, "Mark all as read") { markAllRead() }.apply { layoutParams = margins(6) })
        root.addView(TextView(this).apply { text = "Recent"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(14), 0, dp(4)) })
        notificationList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(notificationList)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun loadNotifications() {
        Thread {
            val rows: List<FamilyNotification>? = if (isParent) {
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
                val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load()
                if (familyId == null || session == null) null else ParentApi(session).familyNotifications(familyId)
            } else DeviceSessionStore(this).api()?.notifications()
            runOnUiThread { render(rows) }
        }.start()
    }

    private fun render(rows: List<FamilyNotification>?) {
        notificationList.removeAllViews()
        when {
            rows == null -> state.text = "Connect this device and sign in again to view notifications."
            rows.isEmpty() -> { current = emptyList(); state.text = "No notifications yet. New family messages will appear here." }
            else -> {
                current = rows
                val unread = rows.count { it.readAt == null }
                state.text = "${rows.size} notification${if (rows.size == 1) "" else "s"}${if (unread > 0) " • $unread new" else " • all read"}"
                rows.forEach { notificationList.addView(notificationCard(it)) }
            }
        }
    }

    private fun notificationCard(item: FamilyNotification) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); layoutParams = margins(6)
        background = NoirUi.interactiveBackground(this@NotificationsActivity, if (item.readAt == null) NoirUi.SURFACE_RAISED else NoirUi.SURFACE, NoirUi.SURFACE_RAISED, if (item.readAt == null) NoirUi.GOLD_DIM else NoirUi.SURFACE_RAISED, 16)
        addView(TextView(this@NotificationsActivity).apply { text = if (item.readAt == null) "NEW • ${item.title}" else item.title; textSize = 14f; setTextColor(if (item.readAt == null) NoirUi.GOLD else NoirUi.TEXT) })
        addView(TextView(this@NotificationsActivity).apply { text = item.body; textSize = 15f; setTextColor(NoirUi.TEXT); setPadding(0, dp(4), 0, dp(3)) })
        addView(TextView(this@NotificationsActivity).apply { text = item.createdAt.replace('T', ' ').substringBefore('.'); textSize = 11f; setTextColor(NoirUi.MUTED) })
        setOnClickListener { openConversation(item) }
    }

    private fun openConversation(item: FamilyNotification) {
        if (isParent) {
            val familyId = intent.getStringExtra(EXTRA_FAMILY_ID) ?: return
            startActivity(if (item.eventType == "quick_update") QuickMessagesActivity.parentIntent(this, familyId, item.deviceId, "Child device") else FamilyChatActivity.parentIntent(this, familyId, item.deviceId, "Child device"))
        } else startActivity(if (item.eventType == "quick_update") QuickMessagesActivity.childIntent(this) else FamilyChatActivity.childIntent(this))
    }

    private fun markAllRead() {
        val ids = current.filter { it.readAt == null }.map { it.id }
        if (ids.isEmpty()) { state.text = "All notifications are already read."; return }
        state.text = "Marking notifications as read…"
        Thread {
            val complete = if (isParent) {
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
                val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load()
                familyId != null && session != null && ParentApi(session).markFamilyNotificationsRead(familyId, ids)
            } else DeviceSessionStore(this).api()?.markNotificationsRead(ids) == true
            runOnUiThread { if (complete) loadNotifications() else state.text = "Could not update notifications. Check the notification migration and connection." }
        }.start()
    }

    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)

    companion object {
        private const val EXTRA_PARENT = "parent_mode"
        private const val EXTRA_FAMILY_ID = "family_id"
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_DEVICE_NAME = "device_name"
        fun parentIntent(context: Context, familyId: String, deviceId: String, deviceName: String) = android.content.Intent(context, NotificationsActivity::class.java).putExtra(EXTRA_PARENT, true).putExtra(EXTRA_FAMILY_ID, familyId).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)
        fun childIntent(context: Context) = android.content.Intent(context, NotificationsActivity::class.java)
    }
}
