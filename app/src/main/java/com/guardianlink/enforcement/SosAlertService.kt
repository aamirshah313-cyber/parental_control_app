package com.guardianlink.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.guardianlink.sync.ParentApi
import com.guardianlink.sync.ParentSessionStore
import com.guardianlink.ui.ParentModeActivity
import com.guardianlink.ui.SosAlertActivity
import com.guardianlink.R
import java.util.Locale

/** Parent-side opt-in receiver for SOS alarms and post-install parent-approval requests. */
class SosAlertService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var voice: TextToSpeech? = null
    private val prefs by lazy { getSharedPreferences("guardian_sos", MODE_PRIVATE) }

    private val poll = object : Runnable {
        override fun run() {
            pollForAlerts()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1003, android.app.Notification.Builder(this, "sos_receiver")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${getString(R.string.app_name)} SOS receiver is active")
            .setContentText("SOS alarms and app approval requests are monitored")
            .setOngoing(true)
            .build())
        handler.post(poll)
    }

    private fun pollForAlerts() {
        Thread {
            val session = ParentSessionStore(this).ensureFresh() ?: return@Thread
            val api = ParentApi(session)
            api.recentSosAlerts().firstOrNull()?.let { latest ->
                if (latest.id != prefs.getString("last_sos_id", null)) {
                    prefs.edit().putString("last_sos_id", latest.id).apply()
                    handler.post { playAlarm(latest.message) }
                }
            }
            api.recentAppInstallRequests().firstOrNull()?.let { request ->
                if (request.id != prefs.getString("last_app_request_id", null)) {
                    prefs.edit().putString("last_app_request_id", request.id).apply()
                    handler.post { showAppRequest(request.appName, request.packageName) }
                }
            }
        }.start()
    }

    private fun showAppRequest(appName: String, packageName: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openDashboard = PendingIntent.getActivity(this, 0, Intent(this, ParentModeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(1204, android.app.Notification.Builder(this, "sos_receiver")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("App approval requested")
            .setContentText("Child installed: $appName")
            .setStyle(android.app.Notification.BigTextStyle().bigText("A child app is waiting for approval: $appName ($packageName). Open ${getString(R.string.app_name)} to allow or block it."))
            .setContentIntent(openDashboard)
            .setAutoCancel(true)
            .build())
    }

    private fun playAlarm(message: String) {
        ringtone?.stop()
        toneGenerator?.release()
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
            it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 15_000)
        }
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.also { it.play() }
        speakSos(message)
        showSosNotification(message)
    }

    private fun speakSos(message: String) {
        voice?.stop()
        voice?.shutdown()
        voice = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                voice?.language = Locale.US
                voice?.speak("Emergency SOS alert. $message", TextToSpeech.QUEUE_FLUSH, null, "guardian-sos-service")
            }
        }
    }

    private fun showSosNotification(message: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, 1003, SosAlertActivity.intent(this, message).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(1003, android.app.Notification.Builder(this, "sos_receiver")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS ALERT — child needs help")
            .setContentText(message)
            .setStyle(android.app.Notification.BigTextStyle().bigText("SOS ALERT\n$message\nTap for the blinking emergency screen and spoken alert."))
            .setContentIntent(open)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .build())
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("sos_receiver", "SOS receiver", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacks(poll); ringtone?.stop(); toneGenerator?.release(); voice?.stop(); voice?.shutdown(); super.onDestroy() }

    companion object {
        fun stopAlarm(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(1003)
            context.stopService(Intent(context, SosAlertService::class.java))
        }
    }
}
