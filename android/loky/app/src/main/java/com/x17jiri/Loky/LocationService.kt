package com.x17jiri.Loky

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object LocationServiceState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun setIsRunning(isRunning: Boolean) {
        _isRunning.value = isRunning
    }
}

class LocationService: Service() {
	private val CHANNEL_ID = "LocationServiceChannel"
	private val NOTIFICATION_ID = 1
	private lateinit var fusedLocationClient: FusedLocationProviderClient
	private lateinit var locationCallback: LocationCallback
	private val job = Job()
	private val scope = CoroutineScope(Dispatchers.IO + job)

	override fun onCreate() {
		super.onCreate()
		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
		createNotificationChannel()
		setupLocationCallback()
		LocationServiceState.setIsRunning(true)
	}

	private fun createNotificationChannel() {
		val channel = NotificationChannel(
			CHANNEL_ID,
			"Location Service Channel",
			NotificationManager.IMPORTANCE_DEFAULT
		)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(channel)
	}

	private fun setupLocationCallback() {
		locationCallback = object : LocationCallback() {
			override fun onLocationResult(locationResult: LocationResult) {
				for (location in locationResult.locations) {
					// Do something with the location

					// Example: You can emit this location to a Flow or update a Room database
					scope.launch {
						processLocation(location)
					}
				}
			}
		}
	}

	private suspend fun processLocation(location: Location) {
		// Implement your location processing logic here
		// For example, save to database or send to a server
		Log.d("LocationService", "processLocation.1: $location")
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, createNotification())
		requestLocationUpdates()
		return START_STICKY
	}

	private fun createNotification(): Notification {
		val notificationIntent = Intent(this, MainActivity::class.java)
		val pendingIntent = PendingIntent.getActivity(
			this, 0, notificationIntent,
			PendingIntent.FLAG_IMMUTABLE
		)

		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("x17 Loky")
			.setContentText("x17 Loky is tracking your location...")
			.setSmallIcon(android.R.drawable.ic_menu_mylocation)
			.setContentIntent(pendingIntent)
			.build()
	}

	private fun requestLocationUpdates() {
		val locationRequest = LocationRequest.Builder(10_000).build()

		try {
			fusedLocationClient.requestLocationUpdates(
				locationRequest,
				locationCallback,
				Looper.getMainLooper()
			)
		} catch (e: SecurityException) {
			Log.e("LocationService", "Lost location permission. Could not request updates.", e)
		}
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onDestroy() {
		super.onDestroy()
		fusedLocationClient.removeLocationUpdates(locationCallback)
		scope.cancel()
		LocationServiceState.setIsRunning(false)
	}
}