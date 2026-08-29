package com.guardianlink.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.model.ChildPolicy
import com.guardianlink.model.AppLimit
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
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(content) })
        content.addView(TextView(this).apply { text = "APP CONTROLS"; textSize = 12f; letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NoirUi.GOLD) })
        content.addView(TextView(this).apply { text = "Manage child apps"; textSize = 27f; typeface = Typeface.create("serif", Typeface.NORMAL); setTextColor(NoirUi.TEXT); setPadding(0, dp(5), 0, dp(4)) })
        content.addView(TextView(this).apply { text = "Select apps, then use a focused action below. Apps awaiting approval are called out clearly."; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10)) })
        status = TextView(this).apply { text = "Loading reported apps…"; setTextColor(NoirUi.GOLD); setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(NoirUi.SURFACE_RAISED) }
        content.addView(status)
        load()
    }

    private fun load() {
        val session = ParentSessionStore(this).load() ?: run { status.text = "Sign in again to manage apps."; return }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
        Thread {
            val api = ParentApi(ParentSessionStore(this).ensureFresh() ?: session)
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
        val selectAll = CheckBox(this).apply { text = "Select all reported apps"; setTextColor(NoirUi.TEXT); setOnCheckedChangeListener { _, checked -> selected.values.forEach { it.isChecked = checked } } }
        content.addView(selectAll)
        apps.forEach { app ->
            val state = when {
                app.packageName in policy.blockedPackages -> "Blocked"
                app.pendingApproval && app.packageName !in policy.approvedPackages -> "Awaiting approval"
                policy.appLimits.firstOrNull { it.packageName == app.packageName } != null -> "${policy.appLimits.first { it.packageName == app.packageName }.dailyMinutes} min/day"
                app.packageName in policy.managedPackages -> "Managed"
                else -> "Allowed"
            }
            val box = CheckBox(this).apply {
                text = "${app.displayName} — $state\n${app.packageName}"
                textSize = 14f; setTextColor(NoirUi.TEXT); setPadding(dp(12), dp(8), dp(12), dp(8))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(NoirUi.SURFACE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), NoirUi.SURFACE_RAISED) }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(5), 0, dp(2)) }
            }
            selected[app.packageName] = box
            content.addView(box)
        }
        content.addView(actionRow(
            "Block selected" to { updatePolicy { it.copy(blockedPackages = it.blockedPackages + chosen(), approvedPackages = it.approvedPackages - chosen()) } },
            "Allow selected" to { updatePolicy { it.copy(blockedPackages = it.blockedPackages - chosen(), approvedPackages = it.approvedPackages + chosen()) } }
        ))
        content.addView(actionRow(
            "Add to pause" to { updatePolicy { it.copy(managedPackages = it.managedPackages + chosen()) } },
            "Remove from pause" to { updatePolicy { it.copy(managedPackages = it.managedPackages - chosen()) } }
        ))
        content.addView(TextView(this).apply { text = "Daily limits"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(NoirUi.TEXT); setPadding(0, dp(18), 0, dp(4)) })
        content.addView(TextView(this).apply { text = "Apply a daily limit to every selected app. It works alongside bedtime and instant pause."; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(6)) })
        val dailyMinutes = EditText(this).apply {
            hint = "Minutes per day (5–720)"; inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED); setPadding(dp(14), dp(4), dp(14), dp(4)); background = android.graphics.drawable.GradientDrawable().apply { setColor(NoirUi.SURFACE_RAISED); cornerRadius = dp(14).toFloat(); setStroke(dp(1), NoirUi.SURFACE_RAISED) }
        }
        content.addView(dailyMinutes)
        content.addView(actionRow(
            "Set selected limit" to {
                val minutes = dailyMinutes.text.toString().toIntOrNull()?.coerceIn(5, 720)
                if (minutes == null) status.text = "Enter a daily limit from 5 to 720 minutes."
                else updatePolicy { current -> current.copy(appLimits = current.appLimits.filterNot { it.packageName in chosen() } + chosen().map { AppLimit(it, minutes) }) }
            },
            "Clear selected limits" to { updatePolicy { current -> current.copy(appLimits = current.appLimits.filterNot { it.packageName in chosen() }) } }
        ))
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
            val ok = ParentApi(ParentSessionStore(this).ensureFresh() ?: session).publishPolicy(deviceId, policyVersion, updated)
            runOnUiThread {
                if (ok) { policy = updated.copy(version = policyVersion + 1); policyVersion += 1; status.text = "Rules sent. They apply when the child device next syncs."; render() }
                else status.text = "Could not update app rules. Refresh the parent dashboard, then try again."
            }
        }.start()
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; setTextColor(NoirUi.BACKGROUND); backgroundTintList = ColorStateList.valueOf(NoirUi.GOLD); minHeight = dp(48); setOnClickListener { action() } }
    private fun actionRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, 0) }
        addView(compactButton(first.first, first.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
        addView(compactButton(second.first, second.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) })
    }
    private fun compactButton(label: String, action: () -> Unit) = Button(this).apply { text = label; textSize = 13f; isAllCaps = false; setTextColor(NoirUi.TEXT); background = android.graphics.drawable.GradientDrawable().apply { setColor(NoirUi.SURFACE_RAISED); cornerRadius = dp(14).toFloat(); setStroke(dp(1), NoirUi.SURFACE_RAISED) }; setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_DEVICE_ID = "device_id"
        fun intent(context: android.content.Context, deviceId: String) = android.content.Intent(context, ManageAppsActivity::class.java).putExtra(EXTRA_DEVICE_ID, deviceId)
    }
}
