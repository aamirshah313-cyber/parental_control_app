package com.guardianlink.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.guardianlink.enforcement.SosAlertService
import java.util.Locale

/** Parent-facing, visible SOS alert with an accessible blinking message and spoken announcement. */
class SosAlertActivity : android.app.Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var bright = true
    private var voice: TextToSpeech? = null
    private lateinit var root: LinearLayout
    private val blink = object : Runnable {
        override fun run() {
            root.setBackgroundColor(if (bright) Color.rgb(198, 40, 40) else Color.rgb(120, 10, 10))
            bright = !bright
            handler.postDelayed(this, 550)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "A child pressed the SOS button."
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)) }
        root.addView(TextView(this).apply { text = "SOS ALERT"; textSize = 36f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        root.addView(TextView(this).apply { text = "$message\n\nContact your child now."; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0, dp(24), 0, dp(24)) })
        root.addView(Button(this).apply { text = "Stop SOS alarm"; isAllCaps = false; setOnClickListener { SosAlertService.stopAlarm(this@SosAlertActivity); finish() } })
        setContentView(root)
        handler.post(blink)
        voice = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                voice?.language = Locale.US
                voice?.speak("Emergency SOS alert. $message", TextToSpeech.QUEUE_FLUSH, null, "guardian-sos-screen")
            }
        }
    }

    override fun onDestroy() { handler.removeCallbacks(blink); voice?.stop(); voice?.shutdown(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_MESSAGE = "message"
        fun intent(context: android.content.Context, message: String) = android.content.Intent(context, SosAlertActivity::class.java).putExtra(EXTRA_MESSAGE, message)
    }
}
