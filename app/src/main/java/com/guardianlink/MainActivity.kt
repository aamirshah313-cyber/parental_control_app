package com.guardianlink

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.ui.ChildModeActivity
import com.guardianlink.ui.ParentModeActivity

class MainActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
        }
        root.addView(TextView(this).apply {
            text = "${getString(R.string.app_name)}\nParental controls that work offline"
            textSize = 24f
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "I am the parent"
            setOnClickListener { startActivity(Intent(this@MainActivity, ParentModeActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "Set up this child device"
            setOnClickListener { startActivity(Intent(this@MainActivity, ChildModeActivity::class.java)) }
        })
        setContentView(root)
    }
}
