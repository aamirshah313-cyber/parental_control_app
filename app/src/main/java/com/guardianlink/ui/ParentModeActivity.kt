package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.enforcement.ProtectionService
import com.guardianlink.policy.PolicyStore

/** Demo controls operate on this device. Supabase command sync connects them to paired child devices. */
class ParentModeActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PolicyStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40) }
        root.addView(TextView(this).apply { text = "Parent controls"; textSize = 24f })
        root.addView(Button(this).apply { text = "Pause YouTube now"; setOnClickListener { store.setPause(null); startProtection() } })
        root.addView(Button(this).apply { text = "Pause for 30 minutes"; setOnClickListener { store.setPause(System.currentTimeMillis() + 30 * 60_000); startProtection() } })
        root.addView(Button(this).apply { text = "Resume"; setOnClickListener { store.resume() } })
        root.addView(Button(this).apply { text = "Add sample keyword rule"; setOnClickListener { store.save(store.load().copy(blockedKeywords = store.load().blockedKeywords + "violent prank")) } })
        setContentView(root)
    }

    private fun startProtection() = startForegroundService(Intent(this, ProtectionService::class.java))
}
