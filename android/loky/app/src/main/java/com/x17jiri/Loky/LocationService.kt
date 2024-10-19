package com.x17jiri.Loky

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executor

class LocationService: Service() {
	companion object {
		val __isRunning = MutableStateFlow(false)
		val isRunning: StateFlow<Boolean> = __isRunning.asStateFlow()
	}
	
	private val CHANNEL_ID = "Locodile.LocationService"
	private val NOTIFICATION_ID = 1
	private lateinit var serviceScope: CoroutineScope
	private lateinit var server: ServerInterface
	private lateinit var contacts: StateFlow<List<Contact>>
	private lateinit var powerManager: PowerManager
	private lateinit var wakeLock: PowerManager.WakeLock
	private lateinit var locationManager: LocationManager
	private var locationListener: LocationListener? = null

	override fun onCreate() {
		super.onCreate()

		serviceScope = CoroutineScope(Dispatchers.IO)
		server = this.__server

		val contactFlow = this.__contactsMan.flow().map { list ->
			list.filter { contact -> contact.send }
		}
		contacts = contactFlow.stateIn(serviceScope, SharingStarted.Eagerly, emptyList())

		powerManager = getSystemService(POWER_SERVICE) as PowerManager
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Locodile:LocationService")

		locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

		createNotificationChannel()
		__isRunning.value = true
	}

	private fun createNotificationChannel() {
		val channel = NotificationChannel(
			CHANNEL_ID,
			"x17 Loky Location Service",
			NotificationManager.IMPORTANCE_DEFAULT
		)
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(channel)
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

	@SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
		try {
			val oldListener = locationListener
			if (oldListener != null) {
				locationManager.removeUpdates(oldListener)
			}

			val locationRequest =
					LocationRequest.Builder(10_000L)
						.setMinUpdateIntervalMillis(5_000L)
						.setMaxUpdateDelayMillis(15_000L)
						.setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
						.build()

			val newListener = object : LocationListener {
				val __serviceScope = serviceScope
				val __server = server
				val __contacts = contacts

				override fun onLocationChanged(locations: List<Location>) {
					if (locations.isNotEmpty()) {
						onLocationChanged(locations.last())
					}
				}

				override fun onLocationChanged(location: Location) {
					__serviceScope.launch {
						__server.sendLoc(location, __contacts.value)
					}
				}
			}
			locationListener = newListener

			locationManager.requestLocationUpdates(
				LocationManager.FUSED_PROVIDER,
				locationRequest,
				Executor { command -> command.run() },
				newListener
			)
		} catch (e: Exception) {
			Log.e("Locodile.LocationService", "Lost location permission. Could not request updates.", e)
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, createNotification())
		wakeLock.acquire()
		requestLocationUpdates()
		return START_STICKY
	}

	override fun onDestroy() {
		wakeLock.release()
		super.onDestroy()

		val listener = locationListener
		if (listener != null) {
			locationManager.removeUpdates(listener)
		}

		serviceScope.cancel()
		__isRunning.value = false
	}

	override fun onBind(intent: Intent?): IBinder? = null
}


