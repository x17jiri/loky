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

class MainViewModel(context: Context): ViewModel() {
	val profileStore = context.__profileStore
	val contactsStore = context.__contactsStore
	val inboxMan = context.__inboxMan
	val recvChanStateStore = context.__recvChanStateStore
	val settings = context.__settings
	val server = context.__server
	val receiver = Receiver(server, inboxMan, recvChanStateStore, viewModelScope)
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
