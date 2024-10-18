package com.x17jiri.Loky

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Credentials(
	val user: String = "",
	val passwd: String = "",
)

data class TmpCredentials(
	val id: Long = 0,
	val token: String = "",
)

class Keys(
	val publicKey: String = "",
	val privateKey: String = "",
	val keyHash: String = "",
	val keyOwner: String = "",
) {
	fun areValid(username: String): Boolean =
		publicKey.isNotEmpty()
		&& privateKey.isNotEmpty()
		&& keyHash.isNotEmpty()
		&& keyOwner == username
}

// TODO: use "Jetpack Security library" and store credentials/keys encrypted
class CredentialsManager(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
) {
	companion object {
		val __userKey: Preferences.Key<String> = stringPreferencesKey("login.user")
		val __passwdKey: Preferences.Key<String> = stringPreferencesKey("login.passwd")

		val __idKey: Preferences.Key<String> = stringPreferencesKey("login.id")
		val __tokenKey: Preferences.Key<String> = stringPreferencesKey("login.token")

		val __publicKeyKey = stringPreferencesKey("key.public")
		val __privateKeyKey = stringPreferencesKey("key.private")
		val __keyHashKey = stringPreferencesKey("key.hash")
		val __keyOwnerKey = stringPreferencesKey("key.owner")
	}

	fun __getCred(p: Preferences): Credentials {
		return Credentials(
			user = p[__userKey] ?: "",
			passwd = p[__passwdKey] ?: "",
		)
	}

	fun credFlow(): Flow<Credentials> {
		return __dataStore.data.map { data -> __getCred(data) }
	}

	val cred = credFlow().stateIn(coroutineScope, SharingStarted.Eagerly, Credentials())

	fun __getTmpCred(p: Preferences): TmpCredentials {
		return TmpCredentials(
			id = p[__idKey]?.toLongOrNull() ?: 0,
			token = p[__tokenKey] ?: "",
		)
	}

	fun tmpCredFlow(): Flow<TmpCredentials> {
		return __dataStore.data.map { data -> __getTmpCred(data) }
	}

	val tmpCred = tmpCredFlow().stateIn(coroutineScope, SharingStarted.Eagerly, TmpCredentials())

	fun __getKeys(p: Preferences): Keys {
		return Keys(
			publicKey = p[__publicKeyKey] ?: "",
			privateKey = p[__privateKeyKey] ?: "",
			keyHash = p[__keyHashKey] ?: "",
			keyOwner = p[__keyOwnerKey] ?: "",
		)
	}

	fun keysFlow(): Flow<Keys> {
		return __dataStore.data.map { data -> __getKeys(data) }
	}

	val keys = keysFlow().stateIn(coroutineScope, SharingStarted.Eagerly, Keys())

	suspend fun updateCred(func: (Credentials) -> Credentials) {
		__dataStore.edit {
			val oldCreds = __getCred(it)
			val newCreds = func(oldCreds)
			if (newCreds.user != oldCreds.user) {
				it[__userKey] = newCreds.user
			}
			if (newCreds.passwd != oldCreds.passwd) {
				it[__passwdKey] = newCreds.passwd
			}
		}
	}

	suspend fun updateTmpCred(func: (TmpCredentials) -> TmpCredentials) {
		__dataStore.edit {
			val oldCreds = __getTmpCred(it)
			val newCreds = func(oldCreds)
			if (newCreds.id != oldCreds.id) {
				it[__idKey] = newCreds.id.toString()
			}
			if (newCreds.token != oldCreds.token) {
				it[__tokenKey] = newCreds.token
			}
		}
	}

	suspend fun updateKeys(func: (Keys) -> Keys) {
		__dataStore.edit {
			val oldKeys = __getKeys(it)
			val newKeys = func(oldKeys)
			if (newKeys.publicKey != oldKeys.publicKey) {
				it[__publicKeyKey] = newKeys.publicKey
			}
			if (newKeys.privateKey != oldKeys.privateKey) {
				it[__privateKeyKey] = newKeys.privateKey
			}
			if (newKeys.keyHash != oldKeys.keyHash) {
				it[__keyHashKey] = newKeys.keyHash
			}
			if (newKeys.keyOwner != oldKeys.keyOwner) {
				it[__keyOwnerKey] = newKeys.keyOwner
			}
		}
	}
}

