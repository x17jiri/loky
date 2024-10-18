package com.x17jiri.Loky

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainViewModel(val context: Context): ViewModel() {
	val credMan = context.__credMan
	val contactsMan = context.__contactsMan
	val server = context.__server
	var receiver = Receiver(this)

	fun startLocationService() {
		try {
			//requestIgnoreBatteryOptimization()
			val serviceIntent = Intent(context, LocationService::class.java)
			context.startForegroundService(serviceIntent)
		} catch (e: Exception) {
			Log.d("Locodile", "MainViewModel.startLocationService: e=$e")
		}
	}

	fun stopLocationService() {
		val intent = Intent(
			Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
			android.net.Uri.parse("package:${context.packageName}"),
			context,
			LocationService::class.java
		)
		context.stopService(intent)
	}

	fun requestIgnoreBatteryOptimization() {
		try {
			val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
				data = Uri.parse("package:${context.packageName}")
			}
			context.startActivity(intent)
		} catch (e: Exception) {
			Log.d("Locodile", "MainViewModel.requestIgnoreBatteryOptimization: e=$e")
		}
	}
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
			@Suppress("UNCHECKED_CAST")
			return MainViewModel(context.applicationContext) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class")
	}
}
