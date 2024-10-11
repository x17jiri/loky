package com.x17jiri.Loky

import android.content.Context
import android.content.Intent
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

sealed class Screen {
    object Loading : Screen()
    object Login : Screen()
    object Other : Screen()
}

data class AppState(
    val currentScreen: Screen = Screen.Loading,
)

class MainViewModel(private var context: Context): ViewModel() {
	val credetials = CredentialsManager(context.dataStore)
	val server = ServerInterface(context)
	val isLocationServiceRunning = LocationServiceState.isRunning

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

	init {
		viewModelScope.launch {
			credetials.init()
		}

        val cred = credetials.get()
		if (cred.user.isEmpty() || cred.passwd.isEmpty()) {
			_appState.value = AppState(Screen.Login)
		} else {
	        login()
		}
	}

	fun login() {
		val cred: Credentials = credetials.get()
		viewModelScope.launch {
			_appState.value = AppState(Screen.Loading)
			val isLoggedIn = server.login(cred)

			_appState.value = if (isLoggedIn) {
				AppState(currentScreen = Screen.Other)
			} else {
				AppState(currentScreen = Screen.Login)
			}
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

