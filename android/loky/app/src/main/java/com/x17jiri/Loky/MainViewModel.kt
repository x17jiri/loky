package com.x17jiri.Loky

import android.content.Context
import android.content.Intent
import android.location.Location
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

class MainViewModel(private var context: Context): ViewModel() {
	val credMan = CredentialsManager(context.dataStore)
	val server = ServerInterface(context, this)
	val isLocationServiceRunning = LocationServiceState.isRunning
	val database = AppDatabase.getInstance(context)
	val contactsMan = ContactsManager(database, viewModelScope)

	init {
		runBlocking {
			credMan.init()
		}
		viewModelScope.launch {
			credMan.objserve()
		}
		viewModelScope.launch(Dispatchers.IO) {
			contactsMan.init()
		}

		viewModelScope.launch {
			LocationServiceState.locationFlow.collect {
				sendLoc(it)
			}
		}
	}

	fun sendLoc(loc: Location) {
		val cred = credMan.credentials.value
		viewModelScope.launch(Dispatchers.IO) {
			server.sendLoc(cred, loc)
		}
	}

	fun startLocationService() {
		context.startForegroundService(Intent(context, LocationService::class.java))
	}

	fun stopLocationService() {
		context.stopService(Intent(context, LocationService::class.java))
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

