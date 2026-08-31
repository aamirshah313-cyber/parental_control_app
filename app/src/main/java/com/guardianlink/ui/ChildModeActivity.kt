package com.guardianlink.ui

import android.Manifest
import android.app.role.RoleManager
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.guardianlink.R
import com.guardianlink.enforcement.AppInventoryReporter
import com.guardianlink.enforcement.LocationService
import com.guardianlink.enforcement.ProtectionService
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.PairingClient
import com.guardianlink.sync.PolicySynchronizer

/** Child hub: setup remains available, while daily actions are grouped into clear destinations. */
class ChildModeActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val setupPrefs by lazy { getSharedPreferences("guardian_child_setup", MODE_PRIVATE) }
    private var pairingInProgress = false
    private var lastStatus = "Choose an area below. Parent rules stay active after their last successful sync."
    private var section = ChildSection.HOME

    private enum class ChildSection { HOME, SAFETY, CONNECT, HELP }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoirUi.apply(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(NoirUi.BACKGROUND)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(content) })
        buildHub()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (section == ChildSection.HOME) finish() else {
            section = ChildSection.HOME
            buildHub()
        }
    }

    private fun buildHub() {
        content.removeAllViews()
        val paired = DeviceSessionStore(this).isPaired()
        content.addView(NoirUi.eyebrow(this, "CHILD SPACE"))
        content.addView(NoirUi.title(this, if (paired) "This device" else "Set up this device").apply { setPadding(0, dp(5), 0, dp(4)) })
        content.addView(TextView(this).apply {
            text = if (paired) "Your parent manages safety rules. You can sync, ask for help, and use family tools here." else "Pair this phone with a parent before activating protection or sending family updates."
            textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10))
        })
        content.addView(statusCard(if (paired) "Paired child device • rules are checked locally" else "Not paired yet • start from Home"))
        content.addView(navigationRow(ChildSection.HOME, ChildSection.SAFETY))
        content.addView(navigationRow(ChildSection.CONNECT, ChildSection.HELP))
        when (section) {
            ChildSection.HOME -> buildHome(paired)
            ChildSection.SAFETY -> buildSafety(paired)
            ChildSection.CONNECT -> buildConnect(paired)
            ChildSection.HELP -> buildHelp()
        }
        status = statusCard(lastStatus)
        content.addView(status)
    }

    private fun buildHome(paired: Boolean) {
        content.addView(sectionTitle("Setup & device"))
        if (!paired) {
            content.addView(note("Enter the single-use code shown by the parent. If this phone was already paired, use Sync rules instead of pairing again."))
            val pairingCode = EditText(this).apply {
                hint = "Paste one-time pairing code"; setSingleLine(); setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED)
                background = NoirUi.rounded(this@ChildModeActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14); setPadding(dp(14), dp(4), dp(14), dp(4))
            }
            content.addView(pairingCode, margins(6))
            content.addView(NoirUi.primaryButton(this, "Pair this device") { pair(pairingCode.text.toString()) }.apply { layoutParams = margins(6) })
            content.addView(note("After pairing, return here to sync rules and complete the clearly explained Android protection step."))
            return
        }

        content.addView(actionRow("Sync rules" to { syncRules() }, "Check readiness" to { checkDeviceReadiness() }))
        val protection = Switch(this).apply {
            text = "Protection monitoring"; textSize = 15f; setTextColor(NoirUi.TEXT)
            isChecked = setupPrefs.getBoolean("permissions_unlocked", false)
            setOnCheckedChangeListener { _, enabled -> setProtectionEnabled(this, enabled) }
        }
        content.addView(protection, margins(4))
        content.addView(note("Turn this on to complete Android’s visible notification and Usage Access setup. Turning it off stops Guardian Link’s protection service on this phone."))
        content.addView(NoirUi.secondaryButton(this, "Re-pair with a new parent code") { showRePairing() }.apply { layoutParams = margins(4) })
        content.addView(note("Use this only when a parent has retired this device and created a new one-time code. It replaces this phone’s local family connection after the new code is accepted."))
        content.addView(sectionTitle("Keep your parent informed"))
        content.addView(NoirUi.secondaryButton(this, "Refresh installed apps") { refreshInstalledApps() }.apply { layoutParams = margins(4) })
        content.addView(note("Use Connect → Ask parent for extra time when you need more of an active daily allowance."))
    }

    private fun buildSafety(paired: Boolean) {
        content.addView(sectionTitle("Safety tools"))
        if (!paired) content.addView(note("Pair this device first to download family rules. The supervised browser can still be opened, but no parent policy is available until pairing succeeds."))
        content.addView(primaryDanger("SOS — alert parent") { sendSos() }.apply { layoutParams = margins(6) })
        content.addView(note("Use SOS only for a real urgent situation. It sends a visible alarm to the paired parent when both phones have internet access."))
        content.addView(sectionTitle("Family Browser"))
        content.addView(actionRow("Open Family Browser" to { startActivity(SafeBrowserActivity.intent(this)) }, "Make it default" to { requestDefaultBrowser() }))
        content.addView(note("Category filters, websites, YouTube Shorts, and custom keywords are checked only in Family Browser. Your parent controls these rules."))
        content.addView(sectionTitle("Visible location"))
        val parentLocationAllowed = com.guardianlink.policy.PolicyStore(this).load().locationEnabled
        val location = Switch(this).apply {
            text = "Share location with parent"; textSize = 15f; setTextColor(NoirUi.TEXT)
            isEnabled = paired && parentLocationAllowed
            isChecked = setupPrefs.getBoolean(CHILD_LOCATION_ENABLED, false)
            setOnCheckedChangeListener { _, enabled -> setLocationSharingEnabled(this, enabled) }
        }
        content.addView(location, margins(4))
        content.addView(note(if (parentLocationAllowed) "This toggle controls this phone’s visible location service. Android shows a persistent notification while it is on." else "Your parent has not enabled location sharing for this child yet. The toggle becomes available after the next rule sync."))
    }

    private fun buildConnect(paired: Boolean) {
        content.addView(sectionTitle("Family connection"))
        if (!paired) content.addView(note("Pair this device before sending updates or messages to a parent."))
        content.addView(actionRow("Quick updates" to { startActivity(QuickMessagesActivity.childIntent(this)) }, "Family chat" to { startActivity(FamilyChatActivity.childIntent(this)) }))
        content.addView(note("Quick updates show only the latest preset message. Use Family Chat for a full typed or voice-note conversation."))
        content.addView(NoirUi.secondaryButton(this, "Notifications") { startActivity(NotificationsActivity.childIntent(this)) }.apply { layoutParams = margins(6) })
        content.addView(NoirUi.secondaryButton(this, "Ask parent for extra time") { requestMoreTime() }.apply { layoutParams = margins(6) })
    }

    private fun buildHelp() {
        content.addView(sectionTitle("Help & appearance"))
        val darkMode = Switch(this).apply {
            text = "Dark appearance"; textSize = 15f; setTextColor(NoirUi.TEXT); isChecked = NoirUi.isDark(this@ChildModeActivity)
            setOnCheckedChangeListener { _, checked -> AppTheme.setDark(this@ChildModeActivity, checked); NoirUi.apply(this@ChildModeActivity); recreate() }
        }
        content.addView(darkMode, margins(4))
        content.addView(note("Appearance is saved for the whole app. Switch off Dark appearance for light mode."))
        content.addView(actionRow("Guardian Guide" to { startActivity(GuardianGuideActivity.intent(this, false)) }, "How to use" to { startActivity(HowToUseActivity.childIntent(this)) }))
        content.addView(note("The Guardian Guide is an on-device help tool. It does not send your questions to a cloud AI service."))
    }

    private fun showRePairing() {
        val code = EditText(this).apply {
            hint = "Paste new one-time pairing code"; setSingleLine(); setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED)
            background = NoirUi.rounded(this@ChildModeActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14); setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Re-pair this device?")
            .setMessage("Ask the parent to retire the old child entry first, then create a new code. A successful new code replaces this phone’s local pairing.")
            .setView(code)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Replace pairing") { _, _ -> pair(code.text.toString(), replacingExisting = true) }
            .show()
    }

    private fun pair(code: String, replacingExisting: Boolean = false) {
        val existingSession = DeviceSessionStore(this)
        if (existingSession.isPaired() && !replacingExisting) { showStatus("This device is already paired. Syncing rules…"); syncRules(); return }
        if (code.isBlank()) { showStatus("Paste the pairing code from the parent app first."); return }
        if (pairingInProgress) return
        pairingInProgress = true; showStatus("Pairing this device…")
        Thread {
            val result = PairingClient().claim(code, "")
            runOnUiThread {
                pairingInProgress = false
                if (result == null) showStatus("Pairing failed. The code may be expired, already used, or the connection is unavailable.")
                else {
                    DeviceSessionStore(this).save(result.deviceId, result.accessToken, result.refreshToken)
                    lastStatus = "Paired successfully. Sync rules, then activate Android protection with your parent."
                    Thread { PolicySynchronizer(this).sync() }.start()
                    buildHub()
                }
            }
        }.start()
    }

    private fun syncRules() {
        if (!DeviceSessionStore(this).isPaired()) { showStatus("Pair this phone before syncing family rules."); return }
        showStatus("Syncing family rules…")
        Thread { val ok = PolicySynchronizer(this).sync(); runOnUiThread { showStatus(if (ok) "Rules synced successfully." else "Could not sync rules. Your last downloaded rules remain active.") } }.start()
    }

    private fun completeStepFive() {
        if (!DeviceSessionStore(this).isPaired()) { showStatus("Pair this phone before activating protection."); return }
        setupPrefs.edit().putBoolean("permissions_unlocked", true).apply()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        Thread { PolicySynchronizer(this).sync() }.start()
        startForegroundService(Intent(this, ProtectionService::class.java))
        showStatus("Enable Usage Access for ${getString(R.string.app_name)}, then return here. Protection is starting.")
    }

    private fun setProtectionEnabled(toggle: Switch, enabled: Boolean) {
        if (enabled) {
            if (!DeviceSessionStore(this).isPaired()) { toggle.isChecked = false; showStatus("Pair this phone before enabling protection."); return }
            completeStepFive()
        } else {
            setupPrefs.edit().putBoolean("permissions_unlocked", false).apply()
            stopService(Intent(this, ProtectionService::class.java))
            stopService(Intent(this, LocationService::class.java))
            showStatus("Protection monitoring is off on this phone. Android Usage Access can still be changed in Settings.")
        }
    }

    /** Makes every prerequisite visible instead of reporting a generic sync or protection failure. */
    private fun checkDeviceReadiness() {
        val paired = DeviceSessionStore(this).isPaired()
        val appOps = getSystemService(AppOpsManager::class.java)
        val usageReady = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        val notificationsReady = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val setupReady = setupPrefs.getBoolean("permissions_unlocked", false)
        val policy = com.guardianlink.policy.PolicyStore(this).load()
        val locationReady = !policy.locationEnabled || (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val problems = buildList {
            if (!paired) add("pairing is missing")
            if (!setupReady) add("protection activation is incomplete")
            if (!usageReady) add("Usage Access is off")
            if (!notificationsReady) add("notifications are blocked")
            if (!locationReady) add("location permission is needed for the parent’s enabled location rule")
        }
        showStatus(if (problems.isEmpty()) "Device readiness: all local requirements are ready. Rules v${policy.version} are stored on this phone." else "Device readiness needs attention: ${problems.joinToString("; ")}. Open Activate protection or Location sharing to resolve it.")
    }

    private fun setLocationSharingEnabled(toggle: Switch, enabled: Boolean) {
        if (!enabled) {
            setupPrefs.edit().putBoolean(CHILD_LOCATION_ENABLED, false).apply()
            stopService(Intent(this, LocationService::class.java))
            showStatus("Location sharing is off on this phone.")
            return
        }
        if (!DeviceSessionStore(this).isPaired()) { toggle.isChecked = false; showStatus("Pair this device before enabling visible location sharing."); return }
        if (!com.guardianlink.policy.PolicyStore(this).load().locationEnabled) { toggle.isChecked = false; showStatus("Your parent has not enabled location sharing for this child device."); return }
        if (!setupPrefs.getBoolean("permissions_unlocked", false)) { toggle.isChecked = false; showStatus("Turn on Protection monitoring from Home before enabling location sharing."); return }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            toggle.isChecked = false
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 15)
            showStatus("Allow location, then turn this toggle on again to start visible sharing.")
            return
        }
        setupPrefs.edit().putBoolean(CHILD_LOCATION_ENABLED, true).apply()
        startForegroundService(Intent(this, LocationService::class.java))
        showStatus("Visible location sharing is on. Android shows a persistent notification while it runs.")
    }

    private fun sendSos() {
        val session = DeviceSessionStore(this)
        if (!session.isPaired()) { showStatus("Pair this child phone before sending SOS."); return }
        showStatus("Sending SOS alert…")
        Thread {
            val sent = session.api()?.postEvent("sos", org.json.JSONObject().apply { put("message", "Child pressed SOS") }) == true
            runOnUiThread { showStatus(if (sent) "SOS sent. The parent alarm should sound within a few seconds." else "SOS could not be sent. Check the internet connection and Supabase SOS migration.") }
        }.start()
    }

    private fun requestMoreTime() {
        val session = DeviceSessionStore(this)
        if (!session.isPaired()) { showStatus("Pair this child phone before requesting extra time."); return }
        if (com.guardianlink.policy.PolicyStore(this).load().dailyScreenLimitMinutes <= 0) {
            showStatus("Your parent has not set a daily screen-time limit, so extra time is not needed.")
            return
        }
        val options = intArrayOf(15, 30, 45, 60)
        android.app.AlertDialog.Builder(this).setTitle("Ask for extra time").setMessage("Choose the extra screen time you need today. Your parent decides whether to grant it.")
            .setItems(options.map { "$it minutes" }.toTypedArray()) { _, which ->
                showStatus("Sending time request…")
                Thread { val sent = session.api()?.requestMoreScreenTime(options[which]) == true; runOnUiThread { showStatus(if (sent) "Request sent. Sync after your parent responds." else "Could not send request. Check the connection and the professional-controls migration.") } }.start()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun refreshInstalledApps() {
        if (!DeviceSessionStore(this).isPaired()) { showStatus("Pair this device before sending its app list."); return }
        showStatus("Refreshing installed apps for parent…")
        Thread { val sent = AppInventoryReporter(this).reportNow(); runOnUiThread { showStatus(if (sent) "App list sent. Your parent can manage apps from their dashboard." else "Could not send the app list. Check the connection, then try again.") } }.start()
    }

    private fun requestDefaultBrowser() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 31)
                showStatus("Choose ${getString(R.string.app_name)} as the browser to check normal links against family rules.")
            } else showStatus("This app is already the default browser, or this phone does not offer browser-role selection.")
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            showStatus("Choose ${getString(R.string.app_name)} as the browser in Android Settings.")
        }
    }

    private fun showStatus(message: String) { lastStatus = message; if (::status.isInitialized) status.text = message }

    private fun navigationRow(first: ChildSection, second: ChildSection) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; layoutParams = margins(4)
        listOf(first, second).forEachIndexed { index, target ->
            addView(NoirUi.secondaryButton(this@ChildModeActivity, if (section == target) "• ${label(target)}" else label(target)) { section = target; buildHub() }.apply { isSelected = section == target }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(if (index == 0) 0 else dp(4), 0, if (index == 0) dp(4) else 0, 0) })
        }
    }

    private fun actionRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; layoutParams = margins(4)
        addView(NoirUi.secondaryButton(this@ChildModeActivity, first.first, first.second), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(4), 0) })
        addView(NoirUi.secondaryButton(this@ChildModeActivity, second.first, second.second), LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), 0, 0, 0) })
    }

    private fun primaryDanger(text: String, action: () -> Unit) = NoirUi.primaryButton(this, text, action).apply { setTextColor(NoirUi.TEXT); background = NoirUi.interactiveBackground(this@ChildModeActivity, NoirUi.DANGER, NoirUi.DANGER, NoirUi.DANGER, 16) }
    private fun statusCard(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(NoirUi.GOLD); setPadding(dp(14), dp(11), dp(14), dp(11)); background = NoirUi.rounded(this@ChildModeActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14); layoutParams = margins(6) }
    private fun note(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(NoirUi.MUTED); setPadding(dp(14), dp(11), dp(14), dp(11)); background = NoirUi.rounded(this@ChildModeActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14); layoutParams = margins(5) }
    private fun sectionTitle(text: String) = TextView(this).apply { this.text = text; textSize = 17f; setTextColor(NoirUi.TEXT); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(14), 0, dp(4)) }
    private fun label(target: ChildSection) = target.name.lowercase().replaceFirstChar { it.uppercase() }
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)
    private companion object { const val CHILD_LOCATION_ENABLED = "child_location_enabled" }
}
