package com.unibus.bd.core.service

import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.unibus.bd.domain.model.BusLocation
import com.unibus.bd.domain.model.CrowdLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Target SDK 35 compliant Foreground Service for tracking driver campus bus GPS telemetry in real time.
 * Implements dynamic location ping intervals to optimize driver phone battery usage when stationary vs moving.
 */
class DriverLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentBusId: String = "BUS_001"
    private var currentRouteId: String = "ROUTE_01"
    private var currentLicensePlate: String = "DHAKA METRO-JA-11-2233"
    private var currentCrowdLevel: CrowdLevel = CrowdLevel.SEATS_AVAILABLE

    private var isMovingMode: Boolean = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                intent?.let {
                    currentBusId = it.getStringExtra(EXTRA_BUS_ID) ?: currentBusId
                    currentRouteId = it.getStringExtra(EXTRA_ROUTE_ID) ?: currentRouteId
                    currentLicensePlate = it.getStringExtra(EXTRA_LICENSE_PLATE) ?: currentLicensePlate
                }
                startForegroundServiceWithNotification()
                startLocationTracking(isMoving = false)
            }
        }
        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val speedKmH = location.speed * 3.6f
                    val isMoving = speedKmH > MOVING_SPEED_THRESHOLD_KMH

                    // Dynamically adjust location request interval based on movement state
                    if (isMoving != isMovingMode) {
                        startLocationTracking(isMoving = isMoving)
                    }

                    val busLocation = BusLocation(
                        busId = currentBusId,
                        licensePlate = currentLicensePlate,
                        routeId = currentRouteId,
                        lat = location.latitude,
                        lng = location.longitude,
                        speedKmH = speedKmH,
                        bearing = location.bearing,
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        crowdLevel = currentCrowdLevel,
                    )

                    serviceScope.launch {
                        _locationFlow.emit(busLocation)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking(isMoving: Boolean) {
        isMovingMode = isMoving
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val intervalMillis = if (isMoving) MOVING_INTERVAL_MS else STATIONARY_INTERVAL_MS
        val minIntervalMillis = if (isMoving) MOVING_MIN_INTERVAL_MS else STATIONARY_MIN_INTERVAL_MS
        val minDistanceMeters = if (isMoving) MOVING_MIN_DISTANCE_M else STATIONARY_MIN_DISTANCE_M

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minIntervalMillis)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission for driver tracking", e)
        }
    }

    private fun stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startForegroundServiceWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DriverLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UniBus BD Driver Console")
            .setContentText("UniBus Live GPS Active • যাত্রা চলমান")
            .setSmallIcon(R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(activityPendingIntent)
            .addAction(
                R.drawable.ic_menu_close_clear_cancel,
                "Stop Trip / যাত্রা শেষ",
                stopPendingIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "UniBus GPS Telemetry",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows active GPS telemetry status for campus bus drivers"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        serviceScope.cancel()
    }

    companion object {
        const val TAG = "DriverLocationService"
        const val CHANNEL_ID = "bus_telemetry_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.unibus.bd.action.START_LOCATION_SERVICE"
        const val ACTION_STOP = "com.unibus.bd.action.STOP_LOCATION_SERVICE"

        const val EXTRA_BUS_ID = "extra_bus_id"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_LICENSE_PLATE = "extra_license_plate"

        private const val MOVING_SPEED_THRESHOLD_KMH = 5.0f

        // Moving (> 5 km/h): ping every 5,000 ms (5s), min interval 3,000 ms, min distance 5 meters
        private const val MOVING_INTERVAL_MS = 5000L
        private const val MOVING_MIN_INTERVAL_MS = 3000L
        private const val MOVING_MIN_DISTANCE_M = 5.0f

        // Stationary (<= 5 km/h): throttle to 25,000 ms (25s), min interval 15,000 ms, min distance 0 meters
        private const val STATIONARY_INTERVAL_MS = 25000L
        private const val STATIONARY_MIN_INTERVAL_MS = 15000L
        private const val STATIONARY_MIN_DISTANCE_M = 0.0f

        private val _locationFlow = MutableSharedFlow<BusLocation>(extraBufferCapacity = 64)
        val locationFlow: SharedFlow<BusLocation> = _locationFlow.asSharedFlow()
    }
}
