package com.guardianlink.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = sessionStore.load()
        buildScreen()
        if (session != null) refreshFamily()
    }

    private fun buildScreen() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding) }
        setContentView(ScrollView(this).apply { addView(content) })
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
        content.addView(note("Family: ${activeFamily.name}"))
        content.addView(button("Refresh devices") { refreshFamily() })
        content.addView(button("Sign out") { signOut() })

        content.addView(section("Pair a child phone"))
        val childName = field("Child/device name").apply { setText("Child phone") }
        val durations = listOf("10 minutes" to 600, "30 minutes" to 1_800, "1 hour" to 3_600, "24 hours" to 86_400)
        val durationPicker = Spinner(this).apply { adapter = ArrayAdapter(this@ParentModeActivity, android.R.layout.simple_spinner_dropdown_item, durations.map { it.first }) }
        content.addView(childName); content.addView(durationPicker)
        content.addView(button("Generate one-time pairing code") {
            createPairing(childName.text.toString(), durations[durationPicker.selectedItemPosition].second)
        })

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
        content.addView(button("Pause managed apps now") { sendCommand("pause", null) })
        content.addView(button("Pause for 30 minutes") { sendCommand("pause", System.currentTimeMillis() + 30 * 60_000) })
        content.addView(button("Resume access") { sendCommand("resume", null) })

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

        content.addView(section("Safe places"))
        content.addView(note(selectedPolicy.safePlaces.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.name}: ${it.radiusMeters} m" } ?: "No safe places configured."))
        val safeName = field("Safe-place name, for example School")
        val safeLatitude = field("Latitude, for example 24.8607", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        val safeLongitude = field("Longitude, for example 67.0011", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        val safeRadius = field("Radius in metres (50–5000)", InputType.TYPE_CLASS_NUMBER).apply { setText("150") }
        listOf(safeName, safeLatitude, safeLongitude, safeRadius).forEach(content::addView)
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

    private fun createPairing(childName: String, validity: Int) {
        val current = session ?: return
        val activeFamily = family ?: return
        if (childName.isBlank()) { setStatus("Enter a child/device name."); return }
        setStatus("Creating pairing code…")
        Thread {
            val result = ParentApi(current).createPairing(activeFamily.id, childName, validity)
            runOnUiThread {
                setStatus(result?.let { "Pairing code (single-use): ${it.code}\nValid for ${it.expiresInSeconds / 60} minutes. Paste it on the child phone." }
                    ?: "Could not create pairing code. Confirm the updated Edge Function is deployed.")
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

    private fun signOut() { sessionStore.clear(); session = null; family = null; selectedDevice = null; buildSignIn() }
    private fun title(text: String) = TextView(this).apply { this.text = text; textSize = 24f; gravity = Gravity.CENTER_HORIZONTAL }
    private fun section(text: String) = TextView(this).apply { this.text = "\n$text"; textSize = 18f }
    private fun note(text: String) = TextView(this).apply { this.text = text }
    private fun field(hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply { this.hint = hint; this.inputType = inputType }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }
    private fun addStatus() { status = TextView(this); content.addView(status) }
    private fun setStatus(text: String) { if (::status.isInitialized) status.text = text }
}
