package com.guardianlink.ui

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.content.ClipData
import android.content.ClipboardManager
import android.location.Geocoder
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.guardianlink.model.ChildPolicy
import com.guardianlink.model.ScheduleRule
import com.guardianlink.model.SafePlace
import com.guardianlink.sync.DeviceRecord
import com.guardianlink.sync.FamilyRecord
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSession
import com.guardianlink.sync.ParentSessionStore
import com.guardianlink.enforcement.SosAlertService
import com.guardianlink.R
import java.time.DayOfWeek
import java.time.LocalTime

/** Parent-only dashboard. Remote operations use the parent's scoped Supabase session. */
class ParentModeActivity : android.app.Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val sessionStore by lazy { ParentSessionStore(this) }
    private var session: ParentSession? = null
    private var family: FamilyRecord? = null
    private var selectedDevice: DeviceRecord? = null
    private var selectedVersion = 0
    private var selectedPolicy = ChildPolicy()
    /** Kept only in memory: raw pairing codes must never be persisted after display. */
    private var lastPairingMessage: String? = null
    private var lastPairingCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = sessionStore.load()
        buildScreen()
        if (session != null) refreshFamily()
    }

    private fun buildScreen() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 248, 252))
            addView(content, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))
        })
        if (session == null) buildSignIn() else buildDashboard(emptyList())
    }

    private fun buildSignIn() {
        content.removeAllViews()
        content.addView(eyebrow("PARENT PORTAL"))
        content.addView(title("Your family, at a glance"))
        content.addView(note("Sign in to your private dashboard. New parents can create an account in under a minute."))
        content.addView(secondaryButton("Read privacy and child-data notice") { showPrivacyNotice() })
        val email = field("Parent email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val guardianConsent = CheckBox(this).apply {
            text = "I confirm that I am the parent or legal guardian authorized to supervise this child device."
        }
        content.addView(section("Account details"))
        content.addView(email); content.addView(password)
        content.addView(guardianConsent)
        content.addView(button("Sign in") {
            setStatus("Signing in…")
            Thread {
                val signedIn = ParentApi.signIn(email.text.toString(), password.text.toString())
                runOnUiThread {
                    if (signedIn == null) setStatus("Sign-in failed. Check your email, password, and Supabase configuration.")
                    else { session = signedIn; sessionStore.save(signedIn); refreshFamily() }
                }
            }.start()
        })
        content.addView(button("Create parent account") {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()
            if (emailText.isBlank() || passwordText.length < 8) { setStatus("Enter an email and a password of at least 8 characters."); return@button }
            if (!guardianConsent.isChecked) { setStatus("Confirm that you are authorized to supervise the child device before creating an account."); return@button }
            setStatus("Creating parent account…")
            Thread {
                val result = ParentApi.signUp(emailText, passwordText)
                runOnUiThread {
                    when {
                        result == null -> setStatus("Account creation failed. Check Supabase configuration or use a different email.")
                        result.session != null -> { session = result.session; sessionStore.save(result.session); refreshFamily() }
                        result.confirmationRequired -> setStatus("Account created. Confirm the email sent by Supabase, then return and sign in.")
                    }
                }
            }.start()
        })
        addStatus()
    }

    private fun showPrivacyNotice() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Privacy and child-data notice")
            .setMessage(
                "This app is for parents or legal guardians supervising a child device with appropriate notice and consent. " +
                    "Depending on the rules you enable, it can process the child device name, installed-app reports, safety events, and visible location check-ins. " +
                    "This information is sent to the Supabase project configured by the app publisher and is available to the authorized parent account.\n\n" +
                    "Do not use the app to monitor adults or anyone without legal authority and their required consent. " +
                    "The publisher must provide a completed privacy policy, support contact, and data-deletion process before public release."
            )
            .setPositiveButton("I understand", null)
            .show()
    }

    private fun showPairingDialog() {
        val dialogContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val childName = field("Child or device name").apply { setText("Child phone") }
        val durations = listOf("10 minutes" to 600, "30 minutes" to 1_800, "1 hour" to 3_600, "24 hours" to 86_400)
        val durationPicker = Spinner(this).apply { adapter = ArrayAdapter(this@ParentModeActivity, android.R.layout.simple_spinner_dropdown_item, durations.map { it.first }) }
        val output = TextView(this).apply {
            text = lastPairingMessage.orEmpty(); textSize = 14f; setTextColor(Color.rgb(17, 80, 130)); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.rgb(232, 242, 255), Color.rgb(171, 204, 244))
        }
        val copy = button("Copy pairing code") {
            val code = lastPairingCode
            if (code == null) setStatus("Generate a pairing code first.")
            else {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("${getString(R.string.app_name)} pairing code", code))
                setStatus("Pairing code copied. Paste it on the child phone.")
            }
        }.apply { isEnabled = lastPairingCode != null }
        dialogContent.addView(note("Create a single-use code, then paste it into Step 1 on the child device."))
        dialogContent.addView(childName)
        dialogContent.addView(durationPicker)
        dialogContent.addView(button("Generate one-time code") { createPairing(childName.text.toString(), durations[durationPicker.selectedItemPosition].second, output, copy) })
        dialogContent.addView(output)
        dialogContent.addView(copy)
        android.app.AlertDialog.Builder(this)
            .setTitle("Pair a child device")
            .setView(dialogContent)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAlertControls() {
        val dialogContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        dialogContent.addView(note("Keep parent alerts enabled to hear SOS alerts and app-approval requests. Android may ask for notification permission."))
        dialogContent.addView(button("Enable parent alerts") { enableParentAlerts() })
        dialogContent.addView(secondaryButton("Stop current SOS alarm") { SosAlertService.stopAlarm(this); setStatus("SOS alarm stopped.") })
        android.app.AlertDialog.Builder(this)
            .setTitle("Parent alerts")
            .setView(dialogContent)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun summaryCard(familyName: String, deviceCount: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(Color.rgb(232, 242, 255), Color.rgb(171, 204, 244))
        layoutParams = layoutParams(0, 10)
        addView(TextView(this@ParentModeActivity).apply { text = familyName; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)) })
        addView(TextView(this@ParentModeActivity).apply { text = "$deviceCount child device${if (deviceCount == 1) "" else "s"} linked • Choose a device to manage its protection."; textSize = 14f; setTextColor(Color.rgb(50, 78, 116)); setPadding(0, dp(4), 0, 0) })
    }

    private fun deviceCard(name: String, lastSeen: String, selected: Boolean, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(if (selected) Color.rgb(232, 242, 255) else Color.WHITE, if (selected) Color.rgb(171, 204, 244) else Color.rgb(224, 229, 238))
        layoutParams = layoutParams(0, 8)
        addView(TextView(this@ParentModeActivity).apply { text = name; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)) })
        addView(TextView(this@ParentModeActivity).apply { text = "Last device check-in: $lastSeen"; textSize = 13f; setTextColor(Color.rgb(70, 82, 102)); setPadding(0, dp(3), 0, dp(10)) })
        addView(secondaryButton(if (selected) "Managing this device" else "Open controls", action).apply { isEnabled = !selected })
    }

    private fun actionRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = layoutParams(0, 6)
        addView(compactAction(first.first, first.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
        addView(compactAction(second.first, second.second), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) })
    }

    private fun compactAction(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setTextColor(Color.rgb(19, 102, 214)); background = rounded(Color.WHITE, Color.rgb(171, 204, 244)); setOnClickListener { action() }
    }

    private fun buildDashboard(devices: List<DeviceRecord>) {
        content.removeAllViews()
        content.addView(eyebrow("FAMILY DASHBOARD"))
        content.addView(title("${getString(R.string.app_name)}"))
        val activeFamily = family
        if (activeFamily == null) {
            content.addView(note("Start by naming your family. You can then create a private pairing code for each child device."))
            val name = field("Family name").apply { setText("My family") }
            content.addView(name)
            content.addView(button("Create family") { createFamily(name.text.toString()) })
            content.addView(secondaryButton("Sign out") { signOut() })
            addStatus()
            return
        }
        // Start while this activity is visible; Android permits the foreground receiver to continue afterward.
        startForegroundService(android.content.Intent(this, SosAlertService::class.java))
        content.addView(summaryCard(activeFamily.name, devices.size))
        content.addView(actionRow(
            "Pair a child" to { showPairingDialog() },
            "Alerts" to { showAlertControls() }
        ))
        content.addView(secondaryButton("How to use this dashboard") { startActivity(HowToUseActivity.parentIntent(this)) })
        content.addView(secondaryButton("Refresh dashboard") { refreshFamily() })

        content.addView(section("Child devices"))
        if (devices.isEmpty()) content.addView(note("No child device paired yet. Use “Pair a child” to create a one-time code."))
        devices.forEach { device ->
            val seen = device.lastSeenAt?.replace('T', ' ')?.substringBefore('.') ?: "not yet reported"
            content.addView(deviceCard(device.displayName, seen, selectedDevice?.id == device.id) { selectDevice(device) })
        }
        selectedDevice?.let { device -> buildDeviceControls(device) }
        addStatus()
    }

    private fun buildDeviceControls(device: DeviceRecord) {
        content.addView(section("${device.displayName} controls"))
        content.addView(note("Choose a quick action, then adjust longer-term rules below. Commands are applied when the child device next syncs."))
        content.addView(actionRow(
            "Pause managed" to { sendCommand("pause", null) },
            "Pause all" to { sendCommand("pause", null, "all_child_apps") }
        ))
        content.addView(actionRow(
            "Pause 30 min" to { sendCommand("pause", System.currentTimeMillis() + 30 * 60_000) },
            "Resume access" to { sendCommand("resume", null) }
        ))
        content.addView(actionRow(
            "Safety activity" to { startActivity(ActivityTimelineActivity.intent(this, device.id, device.displayName)) },
            "Quick messages" to { family?.let { startActivity(QuickMessagesActivity.parentIntent(this, it.id, device.id, device.displayName)) } }
        ))
        content.addView(secondaryButton("Check command delivery") { checkCommandDelivery() })

        content.addView(section("Rules"))
        val keywords = field("Blocked keywords (comma separated)").apply { setText(selectedPolicy.blockedKeywords.joinToString(", ")) }
        val bedtimeEnabled = CheckBox(this).apply { text = "Enable daily bedtime"; isChecked = selectedPolicy.schedules.isNotEmpty() }
        val bedtimeStart = field("Bedtime starts (24-hour time)").apply { setText(selectedPolicy.schedules.firstOrNull()?.start?.toString() ?: "21:00") }
        val bedtimeEnd = field("Bedtime ends (24-hour time)").apply { setText(selectedPolicy.schedules.firstOrNull()?.end?.toString() ?: "07:00") }
        val approval = CheckBox(this).apply { text = "Block newly installed apps until approved"; isChecked = selectedPolicy.requireAppApproval }
        val location = CheckBox(this).apply { text = "Enable visible child location sharing"; isChecked = selectedPolicy.locationEnabled }
        val interval = field("Location interval in minutes (5–120)", InputType.TYPE_CLASS_NUMBER).apply { setText(selectedPolicy.locationIntervalMinutes.toString()) }
        listOf(keywords, bedtimeEnabled, bedtimeStart, bedtimeEnd, approval, location, interval).forEach(content::addView)
        content.addView(button("Save and send rules") {
            val schedule = if (bedtimeEnabled.isChecked) runCatching {
                listOf(ScheduleRule(DayOfWeek.entries.toSet(), LocalTime.parse(bedtimeStart.text.toString().trim()), LocalTime.parse(bedtimeEnd.text.toString().trim()), "Bedtime"))
            }.getOrElse { setStatus("Use valid times such as 21:00 and 07:00."); return@button } else emptyList()
            publishPolicy(selectedPolicy.copy(
                blockedKeywords = keywords.text.toString().split(',').map(String::trim).filter(String::isNotBlank).toSet(),
                schedules = schedule,
                requireAppApproval = approval.isChecked,
                locationEnabled = location.isChecked,
                locationIntervalMinutes = interval.text.toString().toIntOrNull()?.coerceIn(5, 120) ?: 15
            ))
        })

        content.addView(section("App approvals and selected-app controls"))
        content.addView(note("Review the child’s reported apps, including apps waiting for approval. Select multiple apps to allow, block, or include in a managed pause."))
        content.addView(button("Manage child apps") { startActivity(ManageAppsActivity.intent(this, device.id)) })
        content.addView(section("Latest shared position"))
        val latestLocation = note("Loading the latest shared check-in…")
        content.addView(latestLocation)
        content.addView(actionRow(
            "Open live map" to { startActivity(LiveLocationActivity.intent(this, device.id, device.displayName)) },
            "Location log" to { startActivity(LocationLogActivity.intent(this, device.id, device.displayName)) }
        ))
        loadLatestLocationPreview(device, latestLocation)

        content.addView(section("Safe places"))
        content.addView(note(selectedPolicy.safePlaces.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.name}: ${it.radiusMeters} m" } ?: "No safe places configured."))
        val safeSearch = field("Search a place, for example Beaconhouse School Karachi")
        val safeName = field("Safe-place name, for example School")
        val safeLatitude = field("Latitude, for example 24.8607", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        val safeLongitude = field("Longitude, for example 67.0011", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        val safeRadius = field("Radius in metres (50–5000)", InputType.TYPE_CLASS_NUMBER).apply { setText("150") }
        content.addView(safeSearch)
        content.addView(button("Find place coordinates") { findPlace(safeSearch.text.toString(), safeName, safeLatitude, safeLongitude) })
        listOf(safeName, safeLatitude, safeLongitude, safeRadius).forEach(content::addView)
        content.addView(button("Use child's latest check-in as point") { useLatestLocationForSafePlace(safeLatitude, safeLongitude) })
        content.addView(button("Preview safe place on map") { openMapPreview(safeLatitude.text.toString(), safeLongitude.text.toString()) })
        content.addView(button("Add safe place") {
            val place = runCatching {
                SafePlace(
                    safeName.text.toString().trim().also { require(it.isNotBlank()) },
                    safeLatitude.text.toString().toDouble().also { require(it in -90.0..90.0) },
                    safeLongitude.text.toString().toDouble().also { require(it in -180.0..180.0) },
                    safeRadius.text.toString().toInt().coerceIn(50, 5_000)
                )
            }.getOrNull()
            if (place == null) setStatus("Enter a name, valid latitude/longitude, and radius.")
            else publishPolicy(selectedPolicy.copy(safePlaces = selectedPolicy.safePlaces.filterNot { it.name.equals(place.name, true) } + place))
        })
        content.addView(button("Clear all safe places") { publishPolicy(selectedPolicy.copy(safePlaces = emptyList())) })
    }

    private fun refreshFamily() {
        val current = session ?: return
        setStatus("Loading dashboard…")
        Thread {
            val api = ParentApi(usableParentSession(current))
            val loadedFamily = api.families().firstOrNull()
            val devices = if (loadedFamily == null) emptyList() else api.devices(loadedFamily.id)
            // Keep the current selection when possible; otherwise open the first child so the dashboard shows one latest position, not a history feed.
            val deviceToShow = devices.firstOrNull { it.id == selectedDevice?.id } ?: devices.firstOrNull()
            val remotePolicy = deviceToShow?.let { api.activePolicy(it.id) }
            runOnUiThread {
                family = loadedFamily
                selectedDevice = deviceToShow
                selectedVersion = remotePolicy?.version ?: 0
                selectedPolicy = remotePolicy?.policy ?: ChildPolicy()
                buildDashboard(devices)
                setStatus("Dashboard updated.")
            }
        }.start()
    }

    private fun createFamily(name: String) {
        val current = session ?: return
        if (name.isBlank()) { setStatus("Enter a family name."); return }
        setStatus("Creating family…")
        Thread {
            val result = ParentApi(usableParentSession(current)).createFamily(name)
            runOnUiThread { if (result == null) setStatus("Could not create family.") else { family = result; refreshFamily() } }
        }.start()
    }

    private fun enableParentAlerts() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 21)
        }
        startForegroundService(android.content.Intent(this, SosAlertService::class.java))
        setStatus("Parent alerts enabled. SOS and new-app approval requests are checked every 5 seconds while the receiver remains active.")
    }

    private fun createPairing(childName: String, validity: Int, output: TextView, copyButton: Button) {
        val current = session ?: return
        val activeFamily = family ?: return
        if (childName.isBlank()) { setStatus("Enter a child/device name."); return }
        setStatus("Creating pairing code…")
        Thread {
            val result = ParentApi(usableParentSession(current)).createPairing(activeFamily.id, childName, validity)
            runOnUiThread {
                val message = result?.let { "Pairing code (single-use): ${it.code}\nValid for ${it.expiresInSeconds / 60} minutes. Paste it on the child phone." }
                    ?: "Could not create pairing code. Confirm the updated Edge Function is deployed."
                if (result != null) {
                    lastPairingMessage = message
                    lastPairingCode = result.code
                    copyButton.isEnabled = true
                }
                output.text = message
                setStatus(message)
                // Do not refresh here: rebuilding the dashboard would erase the only display of the raw, hashed-on-server code.
            }
        }.start()
    }

    private fun selectDevice(device: DeviceRecord) {
        val current = session ?: return
        selectedDevice = device
        setStatus("Loading ${device.displayName}'s policy…")
        Thread {
            val remote = ParentApi(usableParentSession(current)).activePolicy(device.id)
            runOnUiThread { selectedVersion = remote?.version ?: 0; selectedPolicy = remote?.policy ?: ChildPolicy(); refreshFamily() }
        }.start()
    }

    private fun sendCommand(command: String, expiry: Long?, scope: String = "managed_apps") {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Sending $command command…")
        Thread {
            val ok = ParentApi(usableParentSession(current)).sendCommand(device.id, command, expiry, scope)
            runOnUiThread { setStatus(if (ok) "Command sent. The child phone applies it when it next syncs." else "Could not send command.") }
        }.start()
    }

    private fun checkCommandDelivery() {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Checking ${device.displayName}'s sync status…")
        Thread {
            val latest = ParentApi(usableParentSession(current)).latestCommandStatus(device.id)
            runOnUiThread {
                val lastSeen = device.lastSeenAt?.replace('T', ' ')?.substringBefore('.') ?: "not reported yet"
                setStatus(
                    latest?.let { command ->
                        val acknowledged = command.acknowledgement?.let { state -> "$state at ${command.acknowledgedAt?.replace('T', ' ')?.substringBefore('.')}" } ?: "waiting for the child device to sync"
                        "Child last seen: $lastSeen\nLatest ${command.commandType} command: $acknowledged."
                    } ?: "Child last seen: $lastSeen\nNo remote command has been sent yet."
                )
            }
        }.start()
    }

    private fun publishPolicy(policy: ChildPolicy) {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Publishing rules…")
        Thread {
            val ok = ParentApi(usableParentSession(current)).publishPolicy(device.id, selectedVersion, policy)
            runOnUiThread {
                if (ok) { selectedVersion += 1; selectedPolicy = policy.copy(version = selectedVersion); setStatus("Rules sent to ${device.displayName}.") }
                else setStatus("Could not publish rules. Try refreshing the device and try again.")
            }
        }.start()
    }

    private fun loadLocation() {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Loading latest location…")
        Thread {
            val location = ParentApi(usableParentSession(current)).latestLocation(device.id)
            runOnUiThread {
                setStatus(location?.let { "Latest location: ${it.latitude}, ${it.longitude}\nAccuracy: ${it.accuracyMeters?.let { meters -> "${meters.toInt()} m" } ?: "unknown"}\nRecorded: ${it.recordedAt}" }
                    ?: "No shared location yet. Enable location sharing and grant location permission on the child phone.")
            }
        }.start()
    }

    private fun loadLatestLocationPreview(device: DeviceRecord, target: TextView) {
        val current = session ?: return
        Thread {
            val location = ParentApi(usableParentSession(current)).latestLocation(device.id)
            runOnUiThread {
                target.text = location?.let {
                    "${String.format(java.util.Locale.US, "%.5f, %.5f", it.latitude, it.longitude)}\n" +
                        "Updated ${it.recordedAt.replace('T', ' ').substringBefore('.')} • ±${it.accuracyMeters?.let { meters -> "${meters.toInt()} m" } ?: "unknown"}"
                } ?: "No shared location yet. Enable visible location sharing on the child device to receive the next check-in."
            }
        }.start()
    }

    private fun findPlace(query: String, name: EditText, latitude: EditText, longitude: EditText) {
        if (query.isBlank()) { setStatus("Enter a place name to search."); return }
        setStatus("Searching for $query…")
        Thread {
            val result = runCatching { if (Geocoder.isPresent()) Geocoder(this).getFromLocationName(query, 1)?.firstOrNull() else null }.getOrNull()
            runOnUiThread {
                if (result == null) {
                    MapNavigator.searchPlace(this, query)
                    setStatus("Automatic coordinate lookup is unavailable on this phone. Map search was opened; choose the place there, then enter its coordinates here, or use the child's latest check-in as the point.")
                }
                else {
                    name.setText(result.featureName ?: query)
                    latitude.setText(result.latitude.toString())
                    longitude.setText(result.longitude.toString())
                    setStatus("Place found. Review the point on the map before saving the safe place.")
                }
            }
        }.start()
    }

    private fun openMapPreview(latitudeText: String, longitudeText: String) {
        val latitude = latitudeText.toDoubleOrNull()
        val longitude = longitudeText.toDoubleOrNull()
        if (latitude == null || longitude == null) { setStatus("Find a place or enter valid coordinates first."); return }
        MapNavigator.openCoordinates(this, latitude, longitude)
    }

    private fun useLatestLocationForSafePlace(latitude: EditText, longitude: EditText) {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Loading the child’s latest check-in…")
        Thread {
            val location = ParentApi(usableParentSession(current)).latestLocation(device.id)
            runOnUiThread {
                if (location == null) setStatus("No child location is available yet. Enable visible location sharing on the child phone, then wait for a check-in.")
                else {
                    latitude.setText(location.latitude.toString())
                    longitude.setText(location.longitude.toString())
                    setStatus("Latest check-in inserted. Preview the point on the map, adjust the radius, then save the safe place.")
                }
            }
        }.start()
    }

    private fun signOut() { sessionStore.clear(); session = null; family = null; selectedDevice = null; lastPairingMessage = null; lastPairingCode = null; buildSignIn() }
    private fun usableParentSession(fallback: ParentSession) = sessionStore.ensureFresh() ?: fallback
    private fun eyebrow(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; letterSpacing = .12f; setTextColor(Color.rgb(19, 102, 214)); typeface = Typeface.DEFAULT_BOLD
        layoutParams = layoutParams(0, 4)
    }
    private fun title(text: String) = TextView(this).apply {
        this.text = text; textSize = 27f; setTextColor(Color.rgb(17, 43, 78)); typeface = Typeface.DEFAULT_BOLD
        layoutParams = layoutParams(0, 12)
    }
    private fun section(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(Color.rgb(17, 43, 78)); typeface = Typeface.DEFAULT_BOLD
        layoutParams = layoutParams(20, 8)
    }
    private fun note(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.rgb(70, 82, 102)); setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(Color.WHITE, Color.rgb(224, 229, 238)); layoutParams = layoutParams(0, 10)
    }
    private fun field(hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        this.hint = hint; this.inputType = inputType; textSize = 15f; setPadding(dp(14), dp(4), dp(14), dp(4))
        background = rounded(Color.WHITE, Color.rgb(196, 208, 225)); layoutParams = layoutParams(0, 8)
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.rgb(19, 102, 214))
        minHeight = dp(48); layoutParams = layoutParams(0, 8); setOnClickListener { action() }
    }
    private fun secondaryButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 15f; isAllCaps = false; setTextColor(Color.rgb(19, 102, 214)); background = rounded(Color.WHITE, Color.rgb(171, 204, 244))
        minHeight = dp(48); layoutParams = layoutParams(0, 8); setOnClickListener { action() }
    }
    private fun addStatus() {
        status = TextView(this).apply {
            textSize = 14f; setTextColor(Color.rgb(17, 80, 130)); setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(232, 242, 255), Color.rgb(171, 204, 244)); layoutParams = layoutParams(16, 0)
        }
        content.addView(status)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun layoutParams(top: Int, bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(14).toFloat(); setStroke(dp(1), stroke) }
    private fun setStatus(text: String) { if (::status.isInitialized) status.text = text }
}
