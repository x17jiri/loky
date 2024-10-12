package com.x17jiri.Loky

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Credentials(
	var user: String = "",
	var passwd: String = "",
)

data class WriteCert(
	var token: Long = 0,
	var key: String = "",
)

class CredentialsManager(dataStore: DataStore<Preferences>) {
	private var __dataStore = dataStore
	val credentialsFlow = MutableStateFlow(Credentials())
	val writeCertFlow: MutableStateFlow<WriteCert?> = MutableStateFlow(null)

	private val __userKey: Preferences.Key<String> = stringPreferencesKey("login.user")
	private val __passwdKey: Preferences.Key<String> = stringPreferencesKey("login.passwd")

	suspend fun init() {
		var user: String = ""
		var passwd: String = ""
		__dataStore.data.first().let {
			user = it[__userKey] ?: ""
			passwd = it[__passwdKey] ?: ""
		}
		credentialsFlow.value = Credentials(user, passwd)
	}

	suspend fun objserve() {
		credentialsFlow.collect {
			__storeCredentials(it.user, it.passwd)
		}
	}

	suspend fun __storeCredentials(user: String, passwd: String) {
		Log.d("Locodile", "storing credentials: user=$user, passwd=$passwd")
		__dataStore.edit {
			it[__userKey] = user
			it[__passwdKey] = passwd
		}
	}
}

