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
	val groupsMan = GroupsManager(context.dataStore)
	val server = ServerInterface(context)
	val isLocationServiceRunning = LocationServiceState.isRunning

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

	init {
		viewModelScope.launch {
			credMan.init()
		}
		viewModelScope.launch {
			credMan.objserve()
		}
		viewModelScope.launch {
			groupsMan.init()
		}
		viewModelScope.launch {
			groupsMan.observe_order()
		}
		viewModelScope.launch {
			groupsMan.objserve_groups()
		}

		val cred = credMan.credentialsFlow.value
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
		val cred: Credentials = credMan.credentialsFlow.value
		viewModelScope.launch {
			_appState.value = AppState(Screen.Loading)
			val writeCert = server.login(cred)

			credMan.writeCertFlow.value = writeCert

			_appState.value =
				if (writeCert != null) {
					AppState(Screen.Other)
				} else {
					AppState(Screen.Login)
				}
		}
	}

	fun sendLoc(loc: Location) {
		val writeCert = credMan.writeCertFlow.value
		if (writeCert != null) {
			viewModelScope.launch {
				server.sendLoc(writeCert, loc)
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

