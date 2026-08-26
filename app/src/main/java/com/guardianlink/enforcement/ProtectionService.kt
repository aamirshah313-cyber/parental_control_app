package com.guardianlink.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.guardianlink.R
import com.guardianlink.policy.PolicyEngine
import com.guardianlink.policy.PolicyStore
import com.guardianlink.ui.BlockingActivity

class ProtectionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: PolicyStore
    private lateinit var monitor: UsageMonitor
    private val engine = PolicyEngine()
    private var lastBlockedPackage: String? = null
    private var lastCloudSync = 0L

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
            .setContentTitle("Guardian Link protection is active")
            .setContentText("Time rules and app limits are being enforced")
            .setOngoing(true)
            .build())
        handler.post(check)
    }

    private fun enforceForegroundApp() {
        val packageName = monitor.currentForegroundPackage() ?: return
        if (packageName == applicationContext.packageName) return
        val policy = store.load()
        val decision = engine.appDecision(policy, packageName, monitor.usedMinutesToday(packageName))
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
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("protection", getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacks(check); super.onDestroy() }
}
