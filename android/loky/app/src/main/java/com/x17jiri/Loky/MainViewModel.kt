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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

sealed class Screen {
    object Loading : Screen()
    object Login : Screen()
    object Other : Screen()
}

data class AppState(
    val currentScreen: Screen = Screen.Loading,
)

class MainViewModel(private var context: Context): ViewModel() {
	val credMan = CredentialsManager(context.dataStore)
	val server = ServerInterface(context)
	val isLocationServiceRunning = LocationServiceState.isRunning
	val database = AppDatabase.getInstance(context)
	val contactsMan = ContactsManager(database)

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

	init {
		runBlocking {
			credMan.init()
		}
		viewModelScope.launch {
			credMan.objserve()
		}
		viewModelScope.launch {
			contactsMan.init()
		}

		val cred = credMan.credentials.value
		if (cred.user.isEmpty() || cred.passwd.isEmpty()) {
			_appState.value = AppState(Screen.Login)
		} else {
	        login()
		}

		viewModelScope.launch {
			LocationServiceState.locationFlow.collect {
				sendLoc(it)
			}
		}
	}

	fun login() {
		val cred = credMan.credentials.value
		viewModelScope.launch {
			_appState.value = AppState(Screen.Loading)
			val updatedCred = server.login(cred)
			if (updatedCred != null) {
				credMan.credentials.value = updatedCred
				_appState.value = AppState(Screen.Other)
			} else {
				_appState.value = AppState(Screen.Login)
			}
		}
	}

	fun sendLoc(loc: Location) {
		val cred = credMan.credentials.value
		viewModelScope.launch {
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

