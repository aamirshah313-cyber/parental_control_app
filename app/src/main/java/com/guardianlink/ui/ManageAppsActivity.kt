package com.guardianlink.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.model.ChildPolicy
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import com.guardianlink.sync.ReportedApp

/** Parent control surface for the child-reported, launchable app inventory. */
class ManageAppsActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var apps: List<ReportedApp> = emptyList()
    private var policy = ChildPolicy()
    private var policyVersion = 0
    private val selected = linkedMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); setBackgroundColor(Color.rgb(246, 248, 252)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 248, 252)); addView(content) })
        content.addView(TextView(this).apply { text = "Manage child apps"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)) })
        content.addView(TextView(this).apply { text = "Select one or more reported apps, then block, allow, or include them in a managed pause. New apps awaiting approval are marked below."; setTextColor(Color.rgb(70, 82, 102)); setPadding(0, dp(8), 0, dp(10)) })
        status = TextView(this).apply { text = "Loading reported apps…"; setTextColor(Color.rgb(17, 80, 130)); setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.rgb(232, 242, 255)) }
        content.addView(status)
        load()
    }

    private fun load() {
        val session = ParentSessionStore(this).load() ?: run { status.text = "Sign in again to manage apps."; return }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        Thread {
            val api = ParentApi(session)
            val remotePolicy = api.activePolicy(deviceId)
            apps = api.reportedApps(deviceId)
            policy = remotePolicy?.policy ?: ChildPolicy()
            policyVersion = remotePolicy?.version ?: 0
            runOnUiThread(::render)
        }.start()
    }

    private fun render() {
        while (content.childCount > 3) content.removeViewAt(3)
        selected.clear()
        if (apps.isEmpty()) {
            status.text = "No app inventory yet. Open the child app, sync rules, and wait a moment. Newly installed apps appear automatically after installation."
            return
        }
        status.text = "${apps.size} reported app${if (apps.size == 1) "" else "s"}. ${apps.count { it.pendingApproval }} awaiting parent approval."
        val selectAll = CheckBox(this).apply { text = "Select all reported apps"; setOnCheckedChangeListener { _, checked -> selected.values.forEach { it.isChecked = checked } } }
        content.addView(selectAll)
        apps.forEach { app ->
            val state = when {
                app.packageName in policy.blockedPackages -> "Blocked"
                app.pendingApproval && app.packageName !in policy.approvedPackages -> "Awaiting approval"
                app.packageName in policy.managedPackages -> "Managed"
                else -> "Allowed"
            }
            val box = CheckBox(this).apply { text = "${app.displayName} — $state\n${app.packageName}"; setPadding(0, dp(6), 0, dp(6)) }
            selected[app.packageName] = box
            content.addView(box)
        }
        content.addView(primaryButton("Block selected apps") { updatePolicy { it.copy(blockedPackages = it.blockedPackages + chosen(), approvedPackages = it.approvedPackages - chosen()) } })
        content.addView(primaryButton("Allow / unblock selected apps") { updatePolicy { it.copy(blockedPackages = it.blockedPackages - chosen(), approvedPackages = it.approvedPackages + chosen()) } })
        content.addView(primaryButton("Add selected to managed pause") { updatePolicy { it.copy(managedPackages = it.managedPackages + chosen()) } })
        content.addView(primaryButton("Remove selected from managed pause") { updatePolicy { it.copy(managedPackages = it.managedPackages - chosen()) } })
    }

    private fun chosen(): Set<String> = selected.filterValues { it.isChecked }.keys

    private fun updatePolicy(transform: (ChildPolicy) -> ChildPolicy) {
        val packages = chosen()
        if (packages.isEmpty()) { status.text = "Select at least one app first."; return }
        val session = ParentSessionStore(this).load() ?: return
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        status.text = "Sending app rules to child device…"
        Thread {
            val updated = transform(policy)
            val ok = ParentApi(session).publishPolicy(deviceId, policyVersion, updated)
            runOnUiThread {
                if (ok) { policy = updated.copy(version = policyVersion + 1); policyVersion += 1; status.text = "Rules sent. They apply when the child device next syncs."; render() }
                else status.text = "Could not update app rules. Refresh the parent dashboard, then try again."
            }
        }.start()
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.rgb(19, 102, 214)); minHeight = dp(48); setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_DEVICE_ID = "device_id"
        fun intent(context: android.content.Context, deviceId: String) = android.content.Intent(context, ManageAppsActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId)
    }
}
