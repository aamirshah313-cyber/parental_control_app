package com.guardianlink.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.FamilyChatMessage
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import java.io.File

/** Private parent/child typed chat with intentionally short, user-recorded voice notes. No carrier SMS or third-party chat service. */
class FamilyChatActivity : android.app.Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var status: TextView
    private lateinit var composer: EditText
    private lateinit var record: Button
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { loadMessages(); handler.postDelayed(this, 12_000) } }
    private val isParent get() = intent.getBooleanExtra(EXTRA_PARENT, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(28)); setBackgroundColor(NoirUi.BACKGROUND) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NoirUi.BACKGROUND); addView(root) })
        val person = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: if (isParent) "Child" else "Parent"
        root.addView(NoirUi.eyebrow(this, "PRIVATE FAMILY CHAT"))
        root.addView(NoirUi.title(this, "Chat with $person").apply { setPadding(0, dp(5), 0, dp(4)) })
        root.addView(TextView(this).apply { text = "Typed messages and voice notes stay inside this paired family. Voice notes are limited to 5 MB and are not carrier SMS."; textSize = 14f; setTextColor(NoirUi.MUTED); setPadding(0, 0, 0, dp(10)) })
        status = TextView(this).apply { text = "Loading conversation…"; setTextColor(NoirUi.GOLD); setPadding(dp(12), dp(10), dp(12), dp(10)); background = NoirUi.rounded(this@FamilyChatActivity, NoirUi.SURFACE_RAISED, NoirUi.SURFACE_RAISED, 14) }
        root.addView(status)
        composer = EditText(this).apply {
            hint = "Write a message"; setTextColor(NoirUi.TEXT); setHintTextColor(NoirUi.MUTED); minLines = 2; maxLines = 4
            background = NoirUi.rounded(this@FamilyChatActivity, NoirUi.SURFACE, NoirUi.SURFACE_RAISED, 14)
            setPadding(dp(14), dp(8), dp(14), dp(8)); layoutParams = margins(10)
        }
        root.addView(composer)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = margins(0) }
        actions.addView(NoirUi.primaryButton(this, "Send text") { sendText() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
        record = NoirUi.secondaryButton(this, "Record voice") { toggleRecording() }
        actions.addView(record, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), 0, 0, 0) })
        root.addView(actions)
        root.addView(NoirUi.secondaryButton(this, "Ask Guardian Guide") { startActivity(GuardianGuideActivity.intent(this, isParent)) }.apply { layoutParams = margins(8) })
        root.addView(TextView(this).apply { text = "Conversation"; textSize = 17f; setTextColor(NoirUi.TEXT); setPadding(0, dp(14), 0, dp(4)) })
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(messages)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { stopRecorder(false); player?.release(); super.onDestroy() }

    private fun sendText() {
        val text = composer.text.toString().trim()
        if (text.isBlank()) { status.text = "Write a message first."; return }
        if (text.length > 600) { status.text = "Messages are limited to 600 characters."; return }
        status.text = "Sending message…"
        Thread {
            val sent = if (isParent) parentApi()?.let { api ->
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID) ?: return@let false
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return@let false
                api.sendChatMessage(familyId, deviceId, text)
            } ?: false else DeviceSessionStore(this).api()?.sendChatMessage(text) == true
            runOnUiThread { if (sent) { composer.setText(""); status.text = "Message sent."; loadMessages() } else status.text = "Message could not be sent. Run the family-chat migration and check the connection." }
        }.start()
    }

    private fun toggleRecording() {
        if (recorder != null) stopRecorder(true) else startRecording()
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            status.text = "Allow microphone access, then tap Record voice again."
            return
        }
        val target = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val started = runCatching {
            MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(64_000); setAudioSamplingRate(22_050)
                setOutputFile(target.absolutePath); prepare(); start()
            }
        }.getOrNull()
        if (started == null) { status.text = "Could not start recording on this phone."; target.delete(); return }
        recorder = started; recordingFile = target; record.text = "Stop & send voice"; status.text = "Recording… tap Stop & send voice when finished."
    }

    private fun stopRecorder(send: Boolean) {
        val active = recorder ?: return
        val file = recordingFile
        recorder = null; recordingFile = null
        runCatching { active.stop() }; active.release(); record.text = "Record voice"
        if (!send || file == null || !file.isFile || file.length() < 500) { file?.delete(); return }
        if (file.length() > MAX_VOICE_BYTES) { file.delete(); status.text = "Voice note is too large. Keep it short (under 5 MB)."; return }
        status.text = "Uploading private voice note…"
        Thread {
            val sent = if (isParent) parentApi()?.let { api ->
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID) ?: return@let false
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return@let false
                api.uploadVoice(deviceId, file)?.let { audioPath -> api.sendChatMessage(familyId, deviceId, "", audioPath) } == true
            } ?: false else DeviceSessionStore(this).api()?.let { api -> api.uploadVoice(file)?.let { audioPath -> api.sendChatMessage("", audioPath) } == true } ?: false
            file.delete()
            runOnUiThread { if (sent) { status.text = "Voice note sent."; loadMessages() } else status.text = "Voice note could not be sent. Run the family-chat migration and check the connection." }
        }.start()
    }

    private fun loadMessages() {
        Thread {
            val result = if (isParent) parentApi()?.let { api -> intent.getStringExtra(EXTRA_DEVICE_ID)?.let(api::chatMessages) } else DeviceSessionStore(this).api()?.chatMessages()?.map { FamilyChatMessage(it.id, it.senderRole, it.body, it.audioPath, it.createdAt) }
            runOnUiThread { render(result) }
        }.start()
    }

    private fun render(items: List<FamilyChatMessage>?) {
        messages.removeAllViews()
        when {
            items == null -> status.text = "Connect this device and sign in again to use Family Chat."
            items.isEmpty() -> status.text = "No chat messages yet. Send the first update."
            else -> {
                status.text = "${items.size} message${if (items.size == 1) "" else "s"} • refreshes while this screen is open"
                items.forEach { messages.addView(messageCard(it)) }
            }
        }
    }

    private fun messageCard(message: FamilyChatMessage) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val mine = (isParent && message.senderRole == "parent") || (!isParent && message.senderRole == "child")
        setPadding(dp(13), dp(10), dp(13), dp(10)); background = NoirUi.rounded(this@FamilyChatActivity, if (mine) NoirUi.SURFACE_RAISED else NoirUi.SURFACE, if (mine) NoirUi.GOLD_DIM else NoirUi.SURFACE_RAISED, 16)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(if (mine) dp(34) else 0, dp(6), if (mine) 0 else dp(34), 0) }
        addView(TextView(this@FamilyChatActivity).apply { text = if (message.senderRole == "parent") "Parent" else "Child"; textSize = 12f; setTextColor(NoirUi.GOLD) })
        if (message.body.isNotBlank()) addView(TextView(this@FamilyChatActivity).apply { text = message.body; textSize = 15f; setTextColor(NoirUi.TEXT); setPadding(0, dp(3), 0, dp(3)) })
        message.audioPath?.let { path -> addView(NoirUi.secondaryButton(this@FamilyChatActivity, "▶ Play voice note") { playVoice(path) }.apply { minHeight = dp(40) }) }
        addView(TextView(this@FamilyChatActivity).apply { text = message.createdAt.replace('T', ' ').substringBefore('.'); textSize = 11f; setTextColor(NoirUi.MUTED); setPadding(0, dp(4), 0, 0) })
    }

    private fun playVoice(audioPath: String) {
        status.text = "Loading voice note…"
        Thread {
            val source = if (isParent) parentApi()?.let { it.voiceObjectUrl(audioPath) to it.voiceRequestHeaders() } else DeviceSessionStore(this).api()?.let { it.voiceObjectUrl(audioPath) to it.voiceRequestHeaders() }
            runOnUiThread {
                if (source == null) { status.text = "Could not access this voice note."; return@runOnUiThread }
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(this@FamilyChatActivity, Uri.parse(source.first), source.second)
                    setOnPreparedListener { start(); status.text = "Playing voice note…" }
                    setOnCompletionListener { status.text = "Voice note finished." }
                    setOnErrorListener { _, _, _ -> status.text = "This voice note could not be played."; true }
                    prepareAsync()
                }
            }
        }.start()
    }

    private fun parentApi(): ParentApi? = ParentSessionStore(this).ensureFresh()?.let(::ParentApi)
    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(top), 0, 0) }
    private fun dp(value: Int) = NoirUi.dp(this, value)

    companion object {
        private const val EXTRA_PARENT = "parent_mode"; private const val EXTRA_DEVICE_ID = "device_id"; private const val EXTRA_DEVICE_NAME = "device_name"; private const val EXTRA_FAMILY_ID = "family_id"
        private const val REQUEST_RECORD_AUDIO = 42; private const val MAX_VOICE_BYTES = 5L * 1024L * 1024L
        fun parentIntent(context: Context, familyId: String, deviceId: String, deviceName: String) = android.content.Intent(context, FamilyChatActivity::class.java).putExtra(EXTRA_PARENT, true).putExtra(EXTRA_FAMILY_ID, familyId).putExtra(EXTRA_DEVICE_ID, deviceId).putExtra(EXTRA_DEVICE_NAME, deviceName)
        fun childIntent(context: Context) = android.content.Intent(context, FamilyChatActivity::class.java)
    }
}
