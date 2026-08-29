package com.guardianlink.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.guardianlink.R
import com.guardianlink.policy.PolicyEngine
import com.guardianlink.policy.PolicyStore
import com.guardianlink.ui.BlockingActivity
import com.guardianlink.ui.QuickMessagesActivity

class ProtectionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: PolicyStore
    private lateinit var monitor: UsageMonitor
    private val engine = PolicyEngine()
    private var lastBlockedPackage: String? = null
    private var lastCloudSync = 0L
    private val messagePrefs by lazy { getSharedPreferences("guardian_child_messages", MODE_PRIVATE) }

    private val check = object : Runnable {
        override fun run() {
            enforceForegroundApp()
            syncCloudIfDue()
            handler.postDelayed(this, 3_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PolicyStore(this)
        monitor = UsageMonitor(this)
        createChannel()
        startForeground(1001, android.app.Notification.Builder(this, "protection")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("${getString(R.string.app_name)} protection is active")
            .setContentText("Time rules and app limits are being enforced")
            .setOngoing(true)
            .build())
        handler.post(check)
    }

    private fun enforceForegroundApp() {
        val packageName = monitor.currentForegroundPackage() ?: return
        if (packageName == applicationContext.packageName) return
        val policy = store.load()
        val allowance = policy.dailyScreenLimitMinutes + store.dailyBonusMinutes()
        val effectivePolicy = if (allowance == policy.dailyScreenLimitMinutes) policy else policy.copy(dailyScreenLimitMinutes = allowance)
        val decision = engine.appDecision(effectivePolicy, packageName, monitor.usedMinutesToday(packageName), monitor.totalScreenMinutesToday())
        if (decision.blocked && packageName != lastBlockedPackage) {
            lastBlockedPackage = packageName
            startActivity(BlockingActivity.intent(this, decision.reason ?: "Unavailable", packageName))
        } else if (!decision.blocked) lastBlockedPackage = null
    }

    private fun syncCloudIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastCloudSync < 30_000) return
        lastCloudSync = now
        Thread { com.guardianlink.sync.PolicySynchronizer(this).sync() }.start()
        Thread { checkForParentMessage() }.start()
    }

    private fun checkForParentMessage() {
        val message = com.guardianlink.sync.DeviceSessionStore(this).api()?.latestQuickMessage("parent") ?: return
        if (message.id == messagePrefs.getString("last_parent_message_id", null)) return
        messagePrefs.edit().putString("last_parent_message_id", message.id).apply()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        // Notification entry includes the child hub, preserving the same Back behaviour as an in-app visit.
        val open = PendingIntent.getActivities(this, 2201, arrayOf(
            Intent(this, com.guardianlink.ui.ChildModeActivity::class.java),
            QuickMessagesActivity.childIntent(this)
        ), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(2201, android.app.Notification.Builder(this, "protection")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Message from parent")
            .setContentText(message.body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(message.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("protection", getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacks(check); super.onDestroy() }
}
