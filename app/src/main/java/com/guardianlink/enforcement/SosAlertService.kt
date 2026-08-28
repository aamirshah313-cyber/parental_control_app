package com.guardianlink.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore

/** Parent-side opt-in receiver. It polls the family's RLS-scoped SOS events without a paid push service. */
class SosAlertService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private val prefs by lazy { getSharedPreferences("guardian_sos", MODE_PRIVATE) }

    private val poll = object : Runnable {
        override fun run() {
            pollForSos()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1003, android.app.Notification.Builder(this, "sos_receiver")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Guardian Link SOS receiver is active")
            .setContentText("An alarm will play for a child SOS alert")
            .setOngoing(true)
            .build())
        handler.post(poll)
    }

    private fun pollForSos() {
        val session = ParentSessionStore(this).load() ?: return
        Thread {
            val latest = ParentApi(session).recentSosAlerts().firstOrNull() ?: return@Thread
            if (latest.id == prefs.getString("last_sos_id", null)) return@Thread
            prefs.edit().putString("last_sos_id", latest.id).apply()
            handler.post { playAlarm() }
        }.start()
    }

    private fun playAlarm() {
        ringtone?.stop()
        toneGenerator?.release()
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
            it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 15_000)
        }
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.also { it.play() }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("sos_receiver", "SOS receiver", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacks(poll); ringtone?.stop(); toneGenerator?.release(); super.onDestroy() }

    companion object {
        fun stopAlarm(context: Context) {
            context.stopService(Intent(context, SosAlertService::class.java))
        }
    }
}
