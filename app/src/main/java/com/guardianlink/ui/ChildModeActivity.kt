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
import com.guardianlink.enforcement.ProtectionService
import com.guardianlink.sync.PolicySynchronizer
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.PairingClient

class ChildModeActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40) }
        root.addView(TextView(this).apply { text = "Child device setup"; textSize = 24f })
        val pairingCode = EditText(this).apply { hint = "Paste pairing code from parent"; setSingleLine() }
        root.addView(pairingCode)
        val pairingStatus = TextView(this)
        root.addView(Button(this).apply {
            text = "Pair this device"
            setOnClickListener {
                pairingStatus.text = "Pairing…"
                Thread {
                    val result = PairingClient().claim(pairingCode.text.toString(), android.os.Build.MODEL)
                    runOnUiThread {
                        if (result == null) {
                            pairingStatus.text = "Pairing failed. Check the code and your internet connection."
                        } else {
                            DeviceSessionStore(this@ChildModeActivity).save(result.deviceId, result.accessToken, result.refreshToken)
                            pairingStatus.text = "This child device is paired."
                            Thread { PolicySynchronizer(this@ChildModeActivity).sync() }.start()
                        }
                    }
                }.start()
            }
        })
        root.addView(pairingStatus)
        root.addView(Button(this).apply { text = "Grant app-usage access"; setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } })
        root.addView(Button(this).apply { text = "Enable protection"; setOnClickListener { startProtection() } })
        root.addView(Button(this).apply { text = "Open supervised browser"; setOnClickListener { startActivity(SafeBrowserActivity.intent(this@ChildModeActivity)) } })
        setContentView(root)
    }

    private fun startProtection() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }
        Thread { PolicySynchronizer(this).sync() }.start()
        startForegroundService(Intent(this, ProtectionService::class.java))
    }
}
