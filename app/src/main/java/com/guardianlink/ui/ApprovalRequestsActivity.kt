package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.enforcement.DeviceCapabilities
import com.guardianlink.model.AppActionRequestStatus
import com.guardianlink.sync.AppActionRequest
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore

/** Parent-facing approval queue for child-side install/unblock/enable attempts. Pending requests
 * are the point of the screen; decided ones remain visible below as a history, never deleted. */
class ApprovalRequestsActivity : android.app.Activity() {
    private lateinit var status: TextView
    private lateinit var capabilityNote: TextView
    private lateinit var pendingList: LinearLayout
    private lateinit var historyList: LinearLayout
    private var deviceNames: Map<String, String> = emptyMap()
    private var deciding: Set<String> = emptySet()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { load(); handler.postDelayed(this, 15_000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(root) })
        root.addView(NoirUi.eyebrow(this, "APPROVAL REQUESTS"))
        root.addView(NoirUi.title(this, "App approvals").apply { setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = "A child cannot install a new app, unblock a blocked app, or enable a restricted app until you decide here."
            textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(6))
        })
        capabilityNote = TextView(this).apply {
            text = DeviceCapabilities.enforcementDescription(this@ApprovalRequestsActivity)
            textSize = 12f; setTextColor(NoirUi.MUTED); setPadding(dp(12), dp(9), dp(12), dp(9))
            background = NoirUi.rounded(this@ApprovalRequestsActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 12)
        }
        root.addView(capabilityNote, margins(6))
        status = TextView(this).apply { text = "Loading requests…"; textSize = 14f; setTextColor(NoirUi.GOLD); setPadding(dp(14), dp(11), dp(14), dp(11)); background = NoirUi.rounded(this@ApprovalRequestsActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14) }
        root.addView(status, margins(10))
        root.addView(NoirUi.secondaryButton(this, "Refresh") { load() }.apply { layoutParams = margins(8) })
        root.addView(TextView(this).apply { text = "Pending"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(16), 0, dp(4)) })
        pendingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(pendingList)
        root.addView(TextView(this).apply { text = "History"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(18), 0, dp(4)) })
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyList)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun load() {
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID) ?: run { status.text = "Open this from a family dashboard."; return }
        val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load() ?: run { status.text = "Sign in again to view approvals."; return }
        Thread {
            val api = ParentApi(session)
            val requests = api.familyAppActionRequests(familyId)
            val devices = api.devices(familyId)
            runOnUiThread {
                deviceNames = devices.associate { it.id to it.displayName }
                render(requests, familyId)
            }
        }.start()
    }

    private fun render(requests: List<AppActionRequest>, familyId: String) {
        pendingList.removeAllViews(); historyList.removeAllViews()
        val pending = requests.filter { it.status == "pending" && !AppActionRequestStatus.isExpired(it.status, it.expiresAt) }
        val decidedOrExpired = requests.filterNot { it.status == "pending" && !AppActionRequestStatus.isExpired(it.status, it.expiresAt) }
        status.text = "${pending.size} pending • ${requests.size} total request${if (requests.size == 1) "" else "s"}"
        if (pending.isEmpty()) pendingList.addView(note("No pending requests right now."))
        pending.forEach { pendingList.addView(requestCard(it, familyId, isPending = true)) }
        if (decidedOrExpired.isEmpty()) historyList.addView(note("No decided requests yet."))
        decidedOrExpired.forEach { historyList.addView(requestCard(it, familyId, isPending = false)) }
    }

    private fun requestCard(item: AppActionRequest, familyId: String, isPending: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12)); layoutParams = margins(6)
        background = NoirUi.rounded(this@ApprovalRequestsActivity, if (isPending) NoirUi.SURFACE_RAISED else NoirUi.SURFACE, if (isPending) NoirUi.GOLD_DIM else NoirUi.SURFACE_RAISED, 16)
        val childName = deviceNames[item.deviceId] ?: "Child device"
        val effectiveStatus = AppActionRequestStatus.effectiveStatus(item.status, item.expiresAt)
        addView(TextView(this@ApprovalRequestsActivity).apply {
            text = "$childName • ${actionLabel(item.action)}"; textSize = 13f; setTextColor(NoirUi.GOLD); typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        addView(LinearLayout(this@ApprovalRequestsActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(2))
            addView(NoirUi.avatar(this@ApprovalRequestsActivity, item.appName).apply { layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)) })
            addView(LinearLayout(this@ApprovalRequestsActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                addView(TextView(this@ApprovalRequestsActivity).apply { text = item.appName; textSize = 16f; setTextColor(NoirUi.TEXT) })
                addView(TextView(this@ApprovalRequestsActivity).apply { text = item.packageName; textSize = 12f; setTextColor(NoirUi.MUTED) })
            })
        })
        addView(TextView(this@ApprovalRequestsActivity).apply {
            text = "Requested ${item.requestedAt.replace('T', ' ').substringBefore('.')} • ${statusLabel(effectiveStatus)}"
            textSize = 12f; setTextColor(NoirUi.MUTED); setPadding(0, dp(2), 0, dp(6))
        })
        if (isPending) {
            val busy = item.id in deciding
            addView(LinearLayout(this@ApprovalRequestsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(NoirUi.primaryButton(this@ApprovalRequestsActivity, if (busy) "Working…" else "Approve") { if (!busy) decide(item, familyId, approve = true) }.apply { isEnabled = !busy; layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(4), 0) } })
                addView(NoirUi.secondaryButton(this@ApprovalRequestsActivity, if (busy) "Working…" else "Deny") { if (!busy) decide(item, familyId, approve = false) }.apply { isEnabled = !busy; layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), 0, 0, 0) } })
            })
        }
    }

    private fun decide(item: AppActionRequest, familyId: String, approve: Boolean) {
        val session = ParentSessionStore(this).ensureFresh() ?: ParentSessionStore(this).load() ?: return
        deciding = deciding + item.id
        status.text = "${if (approve) "Approving" else "Denying"} ${item.appName}…"
        Thread {
            val api = ParentApi(session)
            val decided = api.decideAppActionRequest(item.id, approve)
            if (decided && approve) applyApprovedAction(api, item)
            if (decided) api.sendAppActionDecision(familyId, item.deviceId, item.appName, item.action, approve)
            runOnUiThread {
                deciding = deciding - item.id
                status.text = if (decided) "${item.appName} ${if (approve) "approved" else "denied"}." else "Could not record the decision. Refresh and try again."
                load()
            }
        }.start()
    }

    /** Lifts Guardian Link's own PolicyEngine soft-block; this app has no Device Owner
     * privilege, so it never claims to change the app's real installed/enabled state in Android. */
    private fun applyApprovedAction(api: ParentApi, item: AppActionRequest) {
        val current = api.activePolicy(item.deviceId) ?: return
        val updated = current.policy.copy(
            blockedPackages = current.policy.blockedPackages - item.packageName,
            approvedPackages = current.policy.approvedPackages + item.packageName
        )
        api.publishPolicy(item.deviceId, current.version, updated)
    }

    private fun actionLabel(action: String) = when (action) { "install" -> "Install request"; "unblock" -> "Unblock request"; "enable" -> "Enable request"; else -> action }
    private fun statusLabel(status: String) = when (status) { "pending" -> "Waiting for your decision"; "approved" -> "Approved"; "denied" -> "Denied"; "expired" -> "Expired, unanswered"; else -> status }
    private fun note(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(dp(12), dp(10), dp(12), dp(10)); background = NoirUi.rounded(this@ApprovalRequestsActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14); layoutParams = margins(4) }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)

    companion object {
        private const val EXTRA_FAMILY_ID = "family_id"
        fun intent(context: Context, familyId: String) = Intent(context, ApprovalRequestsActivity::class.java).putExtra(EXTRA_FAMILY_ID, familyId)
    }
}
