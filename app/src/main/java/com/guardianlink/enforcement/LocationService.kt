package com.guardianlink.enforcement

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import com.guardianlink.policy.PolicyStore
import com.guardianlink.R
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.SupabaseApi

/** Visible, opt-in location sharing. It deliberately never runs as a hidden tracker. */
class LocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var store: PolicyStore

    override fun onCreate() {
        super.onCreate()
        store = PolicyStore(this)
        createChannel()
        startForeground(1002, android.app.Notification.Builder(this, "location_sharing")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("${getString(R.string.app_name)} location sharing is active")
            .setContentText("Location is shared with the parent family when enabled by policy")
            .setOngoing(true)
            .build())
        if (!hasLocationPermission()) { reportStatus("permission_denied"); stopSelf(); return }
        locationManager = getSystemService(LocationManager::class.java)
        requestUpdates()
    }

    private fun requestUpdates() {
        val policy = store.load()
        if (!policy.locationEnabled || !getSharedPreferences("guardian_child_setup", MODE_PRIVATE).getBoolean("child_location_enabled", false)) { stopSelf(); return }
        val interval = policy.locationIntervalMinutes.coerceIn(5, 120) * 60_000L
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (providers.isEmpty()) { reportStatus("services_disabled"); return }
        providers.forEach { provider -> locationManager.requestLocationUpdates(provider, interval, 100f, this) }
        val lastKnown = providers.mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }.firstOrNull()
        if (lastKnown != null) upload(lastKnown) else reportStatus("waiting")
    }

    override fun onLocationChanged(location: Location) {
        if (!store.load().locationEnabled || !getSharedPreferences("guardian_child_setup", MODE_PRIVATE).getBoolean("child_location_enabled", false)) { stopSelf(); return }
        upload(location)
    }

    /** Best-effort: a device with no connectivity cannot report its own "offline" status either,
     * so the parent UI infers that case from staleness instead of relying on this call. */
    private fun reportStatus(status: String) {
        val session = DeviceSessionStore(this)
        if (!session.isPaired()) return
        Thread { session.api()?.updateLocationStatus(status) }.start()
    }

    private fun upload(location: Location) {
        val session = DeviceSessionStore(this)
        if (!session.isPaired()) return
        Thread {
            val api = session.api() ?: return@Thread
            // Do not emit a false "location updated" event or change safe-place state when the location row was rejected.
            if (!api.postLocation(location.latitude, location.longitude, location.accuracy.takeIf { location.hasAccuracy() })) return@Thread
            api.updateLocationStatus("available")
            api.postEvent("location_update", org.json.JSONObject().apply { put("accuracy_meters", location.accuracy) })
            reportSafePlaceTransitions(location, api)
        }.start()
    }

    private fun reportSafePlaceTransitions(location: Location, api: SupabaseApi) {
        val prefs = getSharedPreferences("guardian_safe_places", MODE_PRIVATE)
        val current = store.load().safePlaces.firstOrNull { place ->
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, place.latitude, place.longitude, results)
            results[0] <= place.radiusMeters
        }?.name
        val previous = prefs.getString("current_safe_place", null)
        if (previous == current) return
        previous?.let { api.postEvent("safe_place_exited", org.json.JSONObject().apply { put("name", it) }) }
        current?.let { api.postEvent("safe_place_entered", org.json.JSONObject().apply { put("name", it) }) }
        prefs.edit().putString("current_safe_place", current).apply()
    }

    private fun hasLocationPermission() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("location_sharing", "Child location sharing", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { if (::locationManager.isInitialized) locationManager.removeUpdates(this); super.onDestroy() }
}
