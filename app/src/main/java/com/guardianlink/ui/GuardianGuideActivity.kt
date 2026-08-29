package com.guardianlink.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A transparent no-cost, on-device guide. It does not send questions to an AI provider or collect conversation history. */
class GuardianGuideActivity : android.app.Activity() {
    private lateinit var conversation: LinearLayout
    private lateinit var question: EditText
    private val isParent get() = intent.getBooleanExtra(EXTRA_PARENT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(root) })
        root.addView(NoirUi.eyebrow(this, "ON-DEVICE GUIDE"))
        root.addView(NoirUi.title(this, "Guardian Guide").apply { setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply { text = "A private, no-cost help chatbot for Guardian Link. It uses on-device guidance rules, not a cloud AI model, and does not upload your questions."; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10)) })
        question = EditText(this).apply { hint = "Ask about SOS, screen time, browser rules…"; setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED); minLines = 2; background = NoirUi.rounded(this@GuardianGuideActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14); setPadding(dp(14), dp(8), dp(14), dp(8)) }
        root.addView(question)
        root.addView(NoirUi.primaryButton(this, "Ask Guardian Guide") { ask() }.apply { layoutParams = margins(8) })
        root.addView(TextView(this).apply { text = "Try a question"; textSize = 15f; setTextColor(NoirUi.TEXT); setPadding(0, dp(12), 0, dp(4)) })
        listOf("How does SOS work?", "How do I sync rules?", "Can YouTube Shorts be blocked?", "How do voice notes work?").forEach { sample ->
            root.addView(NoirUi.secondaryButton(this, sample) { question.setText(sample); ask() }.apply { layoutParams = margins(5) })
        }
        root.addView(TextView(this).apply { text = "Guide conversation"; textSize = 16f; setTextColor(NoirUi.TEXT); setPadding(0, dp(16), 0, dp(4)) })
        conversation = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(conversation)
        addGuide("Hello. I can explain Guardian Link’s safety, communication, and setup features.")
    }

    private fun ask() {
        val text = question.text.toString().trim()
        if (text.isBlank()) return
        addBubble("You", text, true)
        question.setText("")
        addGuide(answerFor(text.lowercase()))
    }

    private fun answerFor(text: String): String = when {
        text.contains("sos") || text.contains("emergency") -> "Use SOS only for a real urgent situation. The child app sends a family alert; the parent dashboard checks for it and plays its visible alarm when both devices have internet access."
        text.contains("sync") || text.contains("rule") -> if (isParent) "Save a rule for the selected child, then ask the child to tap Sync rules. Use Check delivery to see the child’s last check-in and the latest command acknowledgement." else "Tap Sync rules now after the parent changes settings. A successful sync confirms your paired session and downloads the active family rules."
        text.contains("short") || text.contains("youtube") || text.contains("browser") || text.contains("keyword") -> "Keyword and YouTube Shorts filtering works in Guardian Link Family Browser. Make it the child phone’s default browser for ordinary links. It cannot inspect content inside the official YouTube or Chrome apps."
        text.contains("voice") || text.contains("chat") || text.contains("message") -> "Family Chat sends typed messages and short audio notes only between the selected parent and paired child device. Audio is stored in the private Guardian Link voice bucket and plays inside the app; it is not carrier SMS."
        text.contains("location") || text.contains("map") || text.contains("safe place") -> "Location sharing is opt-in, shown with an Android foreground notification, and controlled by the parent rule plus child Android permission. The main dashboard shows only the latest location; older points are in Location Log."
        text.contains("app") || text.contains("install") || text.contains("block") -> "On the child device tap Refresh installed apps for parent, then refresh the list in Parent Manage child apps. Standard Android mode can block first use of a new app; pre-install approval requires Device Owner deployment."
        text.contains("time") || text.contains("pause") || text.contains("bedtime") -> "The parent can pause managed/all child apps, use a schedule, set a daily allowance, or grant a one-day time request. A child device applies command changes at its next sync."
        else -> "I can help with SOS, syncing, browser rules, location, app controls, screen time, family chat, and voice notes. Ask a short question using one of those topics."
    }

    private fun addGuide(text: String) = addBubble("Guardian Guide", text, false)
    private fun addBubble(sender: String, text: String, mine: Boolean) {
        conversation.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10)); background = NoirUi.rounded(this@GuardianGuideActivity, if (mine) NoirUi.SURFACE_RAISED else NoirUi.SURFACE, if (mine) NoirUi.GOLD_DIM else NoirUi.SURFACE_RAISED, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(if (mine) dp(32) else 0, dp(6), if (mine) 0 else dp(32), 0) }
            addView(TextView(this@GuardianGuideActivity).apply { this.text = sender; textSize = 12f; setTextColor(NoirUi.GOLD); gravity = if (mine) Gravity.END else Gravity.START })
            addView(TextView(this@GuardianGuideActivity).apply { this.text = text; textSize = 15f; setTextColor(NoirUi.TEXT); setPadding(0, dp(3), 0, 0) })
        })
    }

    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)
    companion object { private const val EXTRA_PARENT = "parent_mode"; fun intent(context: Context, parentMode: Boolean) = android.content.Intent(context, GuardianGuideActivity::class.java).putExtra(EXTRA_PARENT, parentMode) }
}
