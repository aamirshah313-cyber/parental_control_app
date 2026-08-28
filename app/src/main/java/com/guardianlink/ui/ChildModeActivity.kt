package com.guardianlink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.enforcement.LocationService
import com.guardianlink.enforcement.ProtectionService
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.PairingClient
import com.guardianlink.sync.PolicySynchronizer

/** Five-step child onboarding. Runtime Android permissions are deliberately deferred to step 5. */
class ChildModeActivity : android.app.Activity() {
    private lateinit var status: TextView
    private val setupPrefs by lazy { getSharedPreferences("guardian_child_setup", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40) }
        root.addView(TextView(this).apply { text = "Child device setup"; textSize = 24f })

        root.addView(TextView(this).apply { text = "Step 1 — Pair this child phone" })
        val pairingCode = EditText(this).apply { hint = "Paste one-time pairing code"; setSingleLine() }
        root.addView(pairingCode)
        root.addView(Button(this).apply {
            text = "Pair this device"
            setOnClickListener {
                status.text = "Pairing…"
                Thread {
                    val result = PairingClient().claim(pairingCode.text.toString(), android.os.Build.MODEL)
                    runOnUiThread {
                        if (result == null) status.text = "Pairing failed. Check the code and internet connection."
                        else {
                            DeviceSessionStore(this@ChildModeActivity).save(result.deviceId, result.accessToken, result.refreshToken)
                            status.text = "Paired. Continue with the recommended setup steps below."
                            Thread { PolicySynchronizer(this@ChildModeActivity).sync() }.start()
                        }
                    }
                }.start()
            }
        })

        root.addView(TextView(this).apply { text = "\nStep 2 — Recommended protection settings (no permissions requested)" })
        root.addView(TextView(this).apply { text = "YouTube Shorts blocked in the supervised browser • bedtime 21:00–07:00 • new apps require parent approval • location sharing off by default" })
        root.addView(TextView(this).apply { text = "\nStep 3 — Download the parent rules" })
        root.addView(Button(this).apply {
            text = "Sync rules now"
            setOnClickListener { Thread { val ok = PolicySynchronizer(this@ChildModeActivity).sync(); runOnUiThread { status.text = if (ok) "Rules synced." else "Could not sync rules." } }.start() }
        })
        root.addView(TextView(this).apply { text = "\nStep 4 — Optional location" })
        root.addView(TextView(this).apply { text = "Location remains off until the parent enables it. No location permission is requested yet." })

        root.addView(TextView(this).apply { text = "\nStep 5 — Activate Android protection permissions" })
        root.addView(Button(this).apply {
            text = "Complete step 5: enable protection"
            setOnClickListener { completeStepFive() }
        })
        root.addView(Button(this).apply {
            text = "Start visible location sharing"
            setOnClickListener { startLocationSharingAfterSetup() }
        })
        root.addView(Button(this).apply {
            text = "SOS — alert parent"
            setOnClickListener { sendSos() }
        })
        root.addView(Button(this).apply { text = "Open supervised browser"; setOnClickListener { startActivity(SafeBrowserActivity.intent(this@ChildModeActivity)) } })
        status = TextView(this)
        root.addView(status)
        setContentView(root)
    }

    private fun completeStepFive() {
        if (!DeviceSessionStore(this).isPaired()) { status.text = "Complete step 1 before activating protection."; return }
        setupPrefs.edit().putBoolean("permissions_unlocked", true).apply()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }
        // Usage Access is an Android Settings consent screen, not a hidden permission request.
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        Thread { PolicySynchronizer(this).sync() }.start()
        startForegroundService(Intent(this, ProtectionService::class.java))
        status.text = "Enable Usage Access for Guardian Link, then return here. Protection is starting."
    }

    private fun startLocationSharingAfterSetup() {
        if (!setupPrefs.getBoolean("permissions_unlocked", false)) { status.text = "Complete step 5 before enabling optional location sharing."; return }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 15)
            status.text = "Allow location only if the parent has enabled location sharing."
            return
        }
        startForegroundService(Intent(this, LocationService::class.java))
        status.text = "Visible location sharing started if the parent policy permits it."
    }

    private fun sendSos() {
        val session = DeviceSessionStore(this)
        if (!session.isPaired()) { status.text = "Pair this child phone before sending SOS."; return }
        status.text = "Sending SOS alert…"
        Thread {
            com.guardianlink.sync.SupabaseApi(session.deviceId!!, session.accessToken!!).postEvent("sos", org.json.JSONObject().apply {
                put("message", "Child pressed SOS")
            })
            runOnUiThread { status.text = "SOS sent. The parent alarm will sound when its SOS receiver syncs." }
        }.start()
    }
}
