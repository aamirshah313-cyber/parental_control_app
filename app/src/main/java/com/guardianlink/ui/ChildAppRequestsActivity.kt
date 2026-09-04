package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.model.AppActionRequestStatus
import com.guardianlink.policy.PolicyStore
import com.guardianlink.sync.ChildAppActionRequest
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.ReportedApp

/** Child-side counterpart to ApprovalRequestsActivity: request an app be unblocked/enabled, or
 * ask to install something new, then watch the request move from pending to a decision. Nothing
 * here performs the action itself -- the parent decides, and only Guardian Link's own app-level
 * block is ever lifted (see DeviceCapabilities). */
class ChildAppRequestsActivity : android.app.Activity() {
    private lateinit var status: TextView
    private lateinit var appsList: LinearLayout
    private lateinit var requestsList: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { load(); handler.postDelayed(this, 15_000) } }
    private var pendingActionPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(root) })
        root.addView(NoirUi.eyebrow(this, "APP REQUESTS"))
        root.addView(NoirUi.title(this, "Ask your parent").apply { setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = "Request to install a new app, or unblock/enable one your parent has restricted. Nothing changes until they decide."
            textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(8))
        })
        status = TextView(this).apply { text = "Loading…"; textSize = 14f; setTextColor(NoirUi.GOLD); setPadding(dp(14), dp(11), dp(14), dp(11)); background = NoirUi.rounded(this@ChildAppRequestsActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14) }
        root.addView(status, margins(6))
        root.addView(NoirUi.secondaryButton(this, "Refresh") { load() }.apply { layoutParams = margins(8) })

        root.addView(TextView(this).apply { text = "Request to install a new app"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(18), 0, dp(4)) })
        val installName = field("App name, for example Minecraft")
        val installPackage = field("Package name, for example com.mojang.minecraftpe")
        root.addView(installName); root.addView(installPackage)
        root.addView(NoirUi.primaryButton(this, "Send install request") {
            val name = installName.text.toString().trim(); val pkg = installPackage.text.toString().trim()
            if (name.isBlank() || pkg.isBlank()) status.text = "Enter both the app name and its package name."
            else sendRequest(name, pkg, "install") { installName.setText(""); installPackage.setText("") }
        }.apply { layoutParams = margins(8) })

        root.addView(TextView(this).apply { text = "Your apps"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(18), 0, dp(4)) })
        appsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appsList)

        root.addView(TextView(this).apply { text = "Your requests"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(18), 0, dp(4)) })
        requestsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(requestsList)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun load() {
        val api = DeviceSessionStore(this).api() ?: run { status.text = "Pair and sign in this device to send requests."; return }
        val policy = PolicyStore(this).load()
        Thread {
            val apps = api.reportedApps()
            val requests = api.appActionRequests()
            runOnUiThread {
                status.text = "${apps.size} reported app${if (apps.size == 1) "" else "s"} • ${requests.count { it.status == "pending" }} request${if (requests.count { it.status == "pending" } == 1) "" else "s"} waiting"
                renderApps(apps, policy)
                renderRequests(requests)
            }
        }.start()
    }

    private fun renderApps(apps: List<ReportedApp>, policy: com.guardianlink.model.ChildPolicy) {
        appsList.removeAllViews()
        if (apps.isEmpty()) { appsList.addView(note("No reported apps yet.")); return }
        apps.forEach { app ->
            val state = AppActionRequestStatus.classify(app.packageName, policy.blockedPackages, policy.approvedPackages, app.pendingApproval)
            val awaitingApproval = state == AppActionRequestStatus.AppState.AWAITING_APPROVAL
            val blocked = state == AppActionRequestStatus.AppState.BLOCKED
            val action = AppActionRequestStatus.requestableAction(state)
            appsList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = margins(4)
                background = NoirUi.rounded(this@ChildAppRequestsActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14)
                addView(LinearLayout(this@ChildAppRequestsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@ChildAppRequestsActivity).apply { text = app.displayName; textSize = 15f; setTextColor(NoirUi.TEXT) })
                    addView(TextView(this@ChildAppRequestsActivity).apply { text = if (awaitingApproval) "Awaiting approval" else if (blocked) "Blocked" else "Allowed"; textSize = 12f; setTextColor(NoirUi.MUTED) })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (action != null) {
                    val busy = pendingActionPackage == app.packageName
                    addView(NoirUi.secondaryButton(this@ChildAppRequestsActivity, if (busy) "Sending…" else "Request ${action}") {
                        if (!busy) sendRequest(app.displayName, app.packageName, action)
                    }.apply { isEnabled = !busy; minHeight = dp(40) })
                }
            })
        }
    }

    private fun renderRequests(requests: List<ChildAppActionRequest>) {
        requestsList.removeAllViews()
        if (requests.isEmpty()) { requestsList.addView(note("You have not sent any requests yet.")); return }
        requests.forEach { requestsList.addView(requestCard(it)) }
    }

    private fun requestCard(item: ChildAppActionRequest): LinearLayout {
        val expired = AppActionRequestStatus.isExpired(item.status, item.expiresAt)
        val (label, tint) = when {
            expired -> "Request expired without a decision." to NoirUi.MUTED
            item.status == "pending" -> "Waiting for parent approval" to NoirUi.GOLD
            item.status == "approved" -> "Approved" to NoirUi.GOLD
            item.status == "denied" -> "Request declined by parent." to NoirUi.MUTED
            else -> item.status to NoirUi.MUTED
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = margins(4)
            background = NoirUi.rounded(this@ChildAppRequestsActivity, if (item.status == "pending" && !expired) NoirUi.SURFACE_RAISED else NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14)
            addView(TextView(this@ChildAppRequestsActivity).apply { text = "${item.appName} • ${item.action}"; textSize = 15f; setTextColor(NoirUi.TEXT) })
            addView(TextView(this@ChildAppRequestsActivity).apply { text = label; textSize = 13f; setTextColor(tint); setPadding(0, dp(2), 0, 0) })
            addView(TextView(this@ChildAppRequestsActivity).apply { text = item.requestedAt.replace('T', ' ').substringBefore('.'); textSize = 11f; setTextColor(NoirUi.MUTED); setPadding(0, dp(2), 0, 0) })
        }
    }

    private fun sendRequest(appName: String, packageName: String, action: String, onSent: () -> Unit = {}) {
        val api = DeviceSessionStore(this).api() ?: return
        pendingActionPackage = packageName
        status.text = "Sending request…"
        Thread {
            val sent = api.requestAppAction(appName, packageName, action)
            runOnUiThread {
                pendingActionPackage = null
                status.text = if (sent) "Request sent. Waiting for parent approval." else "Could not send the request. Check the connection and try again."
                if (sent) onSent()
                load()
            }
        }.start()
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint; inputType = InputType.TYPE_CLASS_TEXT; textSize = 15f; setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED)
        setPadding(dp(14), dp(4), dp(14), dp(4)); background = NoirUi.rounded(this@ChildAppRequestsActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14)
        layoutParams = margins(6)
    }
    private fun note(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(dp(12), dp(10), dp(12), dp(10)); background = NoirUi.rounded(this@ChildAppRequestsActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14); layoutParams = margins(4) }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)

    companion object {
        fun intent(context: Context) = Intent(context, ChildAppRequestsActivity::class.java)
    }
}
