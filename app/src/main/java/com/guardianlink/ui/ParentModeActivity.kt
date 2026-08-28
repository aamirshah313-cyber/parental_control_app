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
        content.addView(title("Parent dashboard"))
        content.addView(note("Sign in with your Supabase parent account. Child-device sessions cannot open this screen."))
        val email = field("Parent email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(email); content.addView(password)
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
        addStatus()
    }

    private fun buildDashboard(devices: List<DeviceRecord>) {
        content.removeAllViews()
        content.addView(title("Guardian Link — Parent"))
        val activeFamily = family
        if (activeFamily == null) {
            content.addView(note("No family was found for this account. Create one before pairing a child phone."))
            val name = field("Family name").apply { setText("My family") }
            content.addView(name)
            content.addView(button("Create family") { createFamily(name.text.toString()) })
            content.addView(button("Sign out") { signOut() })
            addStatus()
            return
        }
        // Start while this activity is visible; Android permits the foreground receiver to continue afterward.
        startForegroundService(android.content.Intent(this, SosAlertService::class.java))
        content.addView(note("Family: ${activeFamily.name}"))
        content.addView(button("Refresh devices") { refreshFamily() })
        content.addView(button("Enable SOS receiver") { startForegroundService(android.content.Intent(this, SosAlertService::class.java)); setStatus("SOS receiver enabled. It checks for alerts every 5 seconds.") })
        content.addView(button("Stop SOS alarm / receiver") { SosAlertService.stopAlarm(this); setStatus("SOS receiver stopped.") })
        content.addView(button("Sign out") { signOut() })

        content.addView(section("Pair a child phone"))
        val childName = field("Child/device name").apply { setText("Child phone") }
        val durations = listOf("10 minutes" to 600, "30 minutes" to 1_800, "1 hour" to 3_600, "24 hours" to 86_400)
        val durationPicker = Spinner(this).apply { adapter = ArrayAdapter(this@ParentModeActivity, android.R.layout.simple_spinner_dropdown_item, durations.map { it.first }) }
        content.addView(childName); content.addView(durationPicker)
        val pairingCodeOutput = TextView(this).apply { text = lastPairingMessage.orEmpty() }
        val copyPairingCode = button("Copy pairing code") {
            val code = lastPairingCode
            if (code == null) setStatus("Generate a pairing code first.")
            else {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Guardian Link pairing code", code))
                setStatus("Pairing code copied. Paste it on the child phone.")
            }
        }.apply { isEnabled = lastPairingCode != null }
        content.addView(button("Generate one-time pairing code") {
            createPairing(childName.text.toString(), durations[durationPicker.selectedItemPosition].second, pairingCodeOutput, copyPairingCode)
        })
        content.addView(pairingCodeOutput)
        content.addView(copyPairingCode)

        content.addView(section("Child devices"))
        if (devices.isEmpty()) content.addView(note("No child device paired yet."))
        devices.forEach { device ->
            val seen = device.lastSeenAt?.replace('T', ' ')?.substringBefore('.') ?: "not yet reported"
            content.addView(button("${device.displayName} — last seen: $seen") { selectDevice(device) })
        }
        selectedDevice?.let { device -> buildDeviceControls(device) }
        addStatus()
    }

    private fun buildDeviceControls(device: DeviceRecord) {
        content.addView(section("Controls for ${device.displayName}"))
        content.addView(button("View recent safety activity") { startActivity(ActivityTimelineActivity.intent(this, device.id, device.displayName)) })
        content.addView(button("Pause managed apps now") { sendCommand("pause", null) })
        content.addView(button("Pause for 30 minutes") { sendCommand("pause", System.currentTimeMillis() + 30 * 60_000) })
        content.addView(button("Resume access") { sendCommand("resume", null) })
        content.addView(button("Check command delivery") { checkCommandDelivery() })

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

        content.addView(section("Approve a newly installed app"))
        val packageName = field("Android package name, for example com.example.app")
        content.addView(packageName)
        content.addView(button("Approve this app") {
            val app = packageName.text.toString().trim()
            if (app.isBlank()) setStatus("Enter an Android package name.")
            else publishPolicy(selectedPolicy.copy(approvedPackages = selectedPolicy.approvedPackages + app, blockedPackages = selectedPolicy.blockedPackages - app))
        })
        content.addView(button("Block this app") {
            val app = packageName.text.toString().trim()
            if (app.isBlank()) setStatus("Enter an Android package name.")
            else publishPolicy(selectedPolicy.copy(blockedPackages = selectedPolicy.blockedPackages + app, approvedPackages = selectedPolicy.approvedPackages - app))
        })
        content.addView(section("Latest shared location"))
        content.addView(button("Load latest location") { loadLocation() })
        content.addView(button("View latest location on map") { startActivity(LiveLocationActivity.intent(this, device.id, device.displayName)) })

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
            val api = ParentApi(current)
            val loadedFamily = api.families().firstOrNull()
            val devices = if (loadedFamily == null) emptyList() else api.devices(loadedFamily.id)
            runOnUiThread { family = loadedFamily; buildDashboard(devices); setStatus("Dashboard updated.") }
        }.start()
    }

    private fun createFamily(name: String) {
        val current = session ?: return
        if (name.isBlank()) { setStatus("Enter a family name."); return }
        setStatus("Creating family…")
        Thread {
            val result = ParentApi(current).createFamily(name)
            runOnUiThread { if (result == null) setStatus("Could not create family.") else { family = result; refreshFamily() } }
        }.start()
    }

    private fun createPairing(childName: String, validity: Int, output: TextView, copyButton: Button) {
        val current = session ?: return
        val activeFamily = family ?: return
        if (childName.isBlank()) { setStatus("Enter a child/device name."); return }
        setStatus("Creating pairing code…")
        Thread {
            val result = ParentApi(current).createPairing(activeFamily.id, childName, validity)
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
            val remote = ParentApi(current).activePolicy(device.id)
            runOnUiThread { selectedVersion = remote?.version ?: 0; selectedPolicy = remote?.policy ?: ChildPolicy(); refreshFamily() }
        }.start()
    }

    private fun sendCommand(command: String, expiry: Long?) {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Sending $command command…")
        Thread {
            val ok = ParentApi(current).sendCommand(device.id, command, expiry)
            runOnUiThread { setStatus(if (ok) "Command sent. The child phone applies it when it next syncs." else "Could not send command.") }
        }.start()
    }

    private fun checkCommandDelivery() {
        val current = session ?: return
        val device = selectedDevice ?: return
        setStatus("Checking ${device.displayName}'s sync status…")
        Thread {
            val latest = ParentApi(current).latestCommandStatus(device.id)
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
            val ok = ParentApi(current).publishPolicy(device.id, selectedVersion, policy)
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
            val location = ParentApi(current).latestLocation(device.id)
            runOnUiThread {
                setStatus(location?.let { "Latest location: ${it.latitude}, ${it.longitude}\nAccuracy: ${it.accuracyMeters?.let { meters -> "${meters.toInt()} m" } ?: "unknown"}\nRecorded: ${it.recordedAt}" }
                    ?: "No shared location yet. Enable location sharing and grant location permission on the child phone.")
            }
        }.start()
    }

    private fun findPlace(query: String, name: EditText, latitude: EditText, longitude: EditText) {
        if (query.isBlank()) { setStatus("Enter a place name to search."); return }
        setStatus("Searching for $query…")
        Thread {
            val result = runCatching { Geocoder(this).getFromLocationName(query, 1)?.firstOrNull() }.getOrNull()
            runOnUiThread {
                if (result == null) setStatus("Place not found. Try a more specific name or enter coordinates manually.")
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
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")))
    }

    private fun signOut() { sessionStore.clear(); session = null; family = null; selectedDevice = null; lastPairingMessage = null; lastPairingCode = null; buildSignIn() }
    private fun title(text: String) = TextView(this).apply {
        this.text = text; textSize = 25f; gravity = Gravity.CENTER_HORIZONTAL; setTextColor(Color.rgb(17, 43, 78)); typeface = Typeface.DEFAULT_BOLD
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
