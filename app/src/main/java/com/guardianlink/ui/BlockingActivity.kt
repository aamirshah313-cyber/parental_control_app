package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockingActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48) }
        root.addView(TextView(this).apply { text = "This app is unavailable\n${intent.getStringExtra(EXTRA_REASON)}"; textSize = 24f; gravity = Gravity.CENTER })
        root.addView(Button(this).apply { text = "Go to home screen"; setOnClickListener { finishAndRemoveTask() } })
        setContentView(root)
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        fun intent(context: Context, reason: String, packageName: String) = Intent(context, BlockingActivity::class.java).apply {
            putExtra(EXTRA_REASON, reason)
            putExtra("package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
