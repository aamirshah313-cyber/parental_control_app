package com.guardianlink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.enforcement.LocationService
import com.guardianlink.enforcement.ProtectionService
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.PairingClient
import com.guardianlink.sync.PolicySynchronizer
import com.guardianlink.R

/** Five-step child onboarding. Runtime Android permissions are deliberately deferred to step 5. */
class ChildModeActivity : android.app.Activity() {
    private lateinit var status: TextView
    private val setupPrefs by lazy { getSharedPreferences("guardian_child_setup", MODE_PRIVATE) }
    private var pairingInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(24), dp(20), dp(28)); setBackgroundColor(Color.rgb(246, 248, 252)) }
        root.addView(TextView(this).apply { text = "CHILD DEVICE SETUP"; textSize = 12f; letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(19, 102, 214)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) })
        root.addView(TextView(this).apply { text = "Set up protection together"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)); setPadding(0, dp(5), 0, dp(4)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) })
        root.addView(TextView(this).apply { text = "Pair this phone first. Android permissions are requested only in the final activation step."; textSize = 14f; setTextColor(Color.rgb(70, 82, 102)); setPadding(0, 0, 0, dp(12)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) })

        root.addView(stepHeader("1", "Pair this child phone", "Paste the single-use code shown in the parent dashboard."))
        val pairingCode = EditText(this).apply { hint = "Paste one-time pairing code"; setSingleLine() }
        root.addView(pairingCode)
        val pairButton = Button(this).apply {
            text = "Pair this device"
            setOnClickListener {
                val existingSession = DeviceSessionStore(this@ChildModeActivity)
                if (existingSession.isPaired()) {
                    status.text = "This child device is already paired. Syncing rules…"
                    Thread { PolicySynchronizer(this@ChildModeActivity).sync() }.start()
                    return@setOnClickListener
                }
                if (pairingInProgress) return@setOnClickListener
                pairingInProgress = true
                isEnabled = false
                status.text = "Pairing…"
                Thread {
                    val result = PairingClient().claim(pairingCode.text.toString(), android.os.Build.MODEL)
                    runOnUiThread {
                        pairingInProgress = false
                        if (result == null) {
                            isEnabled = true
                            status.text = "Pairing failed. The code may be expired, already used, or the internet connection is unavailable."
                        }
                        else {
                            DeviceSessionStore(this@ChildModeActivity).save(result.deviceId, result.accessToken, result.refreshToken)
                            text = "This device is paired"
                            status.text = "Paired. Continue with the recommended setup steps below."
                            Thread { PolicySynchronizer(this@ChildModeActivity).sync() }.start()
                        }
                    }
                }.start()
            }
        }
        root.addView(pairButton)

        root.addView(stepHeader("2", "Recommended protection settings", "No permissions are requested in this step. The parent chooses rules from their dashboard."))
        root.addView(infoCard("Supervised browser can block YouTube Shorts and selected keywords. Bedtime, app approval, and location sharing are controlled by the parent."))
        root.addView(stepHeader("3", "Download parent rules", "Sync after pairing and whenever the parent changes rules."))
        root.addView(Button(this).apply {
            text = "Sync rules now"
            setOnClickListener { Thread { val ok = PolicySynchronizer(this@ChildModeActivity).sync(); runOnUiThread { status.text = if (ok) "Rules synced." else "Could not sync rules." } }.start() }
        })
        root.addView(stepHeader("4", "Optional location sharing", "Location stays off until the parent enables it. It is always visible through an Android notification."))

        root.addView(stepHeader("5", "Activate Android protection", "This is the only step that opens Android permission and Usage Access settings."))
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
        styleControls(root)
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 248, 252)); addView(root) })
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
        status.text = "Enable Usage Access for ${getString(R.string.app_name)}, then return here. Protection is starting."
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
            val sent = session.api()?.postEvent("sos", org.json.JSONObject().apply {
                put("message", "Child pressed SOS")
            }) == true
            runOnUiThread { status.text = if (sent) "SOS sent. The parent alarm should sound within 5 seconds." else "SOS could not be sent. Check the internet connection and Supabase SOS migration." }
        }.start()
    }

    private fun styleControls(root: LinearLayout) {
        for (index in 0 until root.childCount) {
            when (val child = root.getChildAt(index)) {
                is Button -> child.apply {
                    isAllCaps = false; textSize = 15f; minHeight = dp(48)
                    when {
                        text.toString().startsWith("SOS") -> { setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.rgb(190, 45, 65)) }
                        text.toString().startsWith("Start visible") || text.toString().startsWith("Open supervised") || text.toString().startsWith("Sync") -> { setTextColor(Color.rgb(19, 102, 214)); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), Color.rgb(171, 204, 244)) } }
                        else -> { setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.rgb(19, 102, 214)) }
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(8)) }
                }
                is EditText -> child.apply {
                    setPadding(dp(14), dp(4), dp(14), dp(4)); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), Color.rgb(196, 208, 225)) }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, dp(10)) }
                }
            }
        }
    }

    private fun stepHeader(number: String, title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(dp(14), dp(12), dp(14), dp(12)); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(224, 229, 238)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, dp(4)) }
        addView(TextView(this@ChildModeActivity).apply { text = number; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.rgb(19, 102, 214)); background = GradientDrawable().apply { setColor(Color.rgb(232, 242, 255)); shape = GradientDrawable.OVAL }; layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)) })
        addView(LinearLayout(this@ChildModeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0); addView(TextView(this@ChildModeActivity).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(17, 43, 78)) }); addView(TextView(this@ChildModeActivity).apply { text = body; textSize = 13f; setTextColor(Color.rgb(70, 82, 102)); setPadding(0, dp(3), 0, 0) }) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun infoCard(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.rgb(70, 82, 102)); setPadding(dp(14), dp(12), dp(14), dp(12)); background = GradientDrawable().apply { setColor(Color.rgb(232, 242, 255)); cornerRadius = dp(14).toFloat() }; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(2), 0, 0) } }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
