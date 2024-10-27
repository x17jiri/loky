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

class SigningKeys(
	val keyPair: SigningKeyPair? = null,
	val keyOwner: String = "",
) {
	fun areValid(username: String): Boolean = keyPair != null && keyOwner == username
}

interface ProfileStore {
	val cred: StateFlow<Credentials>
	val tmpCred: StateFlow<TmpCredentials>
	val signingKeys: StateFlow<SigningKeys>

	suspend fun updateCred(func: (Credentials) -> Credentials)
	suspend fun updateTmpCred(func: (TmpCredentials) -> TmpCredentials)
	suspend fun updateKeys(func: (SigningKeys) -> SigningKeys)
}

class ProfileDataStoreStore(
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
		val __keyOwnerKey = stringPreferencesKey("key.owner")
	}

	fun extractCred(p: Preferences): Credentials {
		return Credentials(
			user = p[__userKey] ?: "",
			passwd = p[__passwdKey] ?: "",
		)
	}

	fun credFlow(): Flow<Credentials> = __dataStore.data.map { data -> extractCred(data) }

	val cred by lazy {
		credFlow().stateIn(coroutineScope, SharingStarted.Eagerly, Credentials())
	}

	fun extractTmpCred(p: Preferences): TmpCredentials {
		return TmpCredentials(
			id = p[__idKey]?.toLongOrNull() ?: 0,
			token = p[__tokenKey] ?: "",
		)
	}

	fun tmpCredFlow(): Flow<TmpCredentials> = __dataStore.data.map { data -> extractTmpCred(data) }

	val tmpCred by lazy {
		tmpCredFlow().stateIn(coroutineScope, SharingStarted.Eagerly, TmpCredentials())
	}

	fun extractKeys(p: Preferences): SigningKeys {
		val publicKey = PublicSigningKey.fromString(p[__publicKeyKey] ?: "").getOrNull()
		val privateKey = PrivateSigningKey.fromString(p[__privateKeyKey] ?: "").getOrNull()
		val owner = p[__keyOwnerKey] ?: ""
		if (publicKey != null && privateKey != null && owner.isNotEmpty()) {
			return SigningKeys(
				keyPair = SigningKeyPair(publicKey, privateKey),
				keyOwner = owner,
			)
		} else {
			return SigningKeys()
		}
	}

	fun keysFlow(): Flow<SigningKeys> = __dataStore.data.map { data -> extractKeys(data) }

	val keys by lazy {
		keysFlow().stateIn(coroutineScope, SharingStarted.Eagerly, SigningKeys())
	}

	suspend fun updateCred(func: (Credentials) -> Credentials) {
		__dataStore.edit { preferences ->
			val oldCreds = extractCred(preferences)
			val newCreds = func(oldCreds)
			if (newCreds.user != oldCreds.user) {
				preferences[__userKey] = newCreds.user
			}
			if (newCreds.passwd != oldCreds.passwd) {
				preferences[__passwdKey] = newCreds.passwd
			}
		}
	}

	suspend fun updateTmpCred(func: (TmpCredentials) -> TmpCredentials) {
		__dataStore.edit { preferences ->
			val oldCreds = extractTmpCred(preferences)
			val newCreds = func(oldCreds)
			Log.d("Locodile", "updateTmpCred: oldCreds=$oldCreds, newCreds=$newCreds")
			if (newCreds.id != oldCreds.id) {
				preferences[__idKey] = newCreds.id.toString()
			}
			if (newCreds.token != oldCreds.token) {
				preferences[__tokenKey] = newCreds.token
			}
		}
	}

	suspend fun updateKeys(func: (SigningKeys) -> SigningKeys) {
		__dataStore.edit { preferences ->
			val oldKeys = extractKeys(preferences)
			val newKeys = func(oldKeys)
			if (newKeys.keyPair != null && newKeys.keyOwner != "") {
				if (newKeys.keyPair != oldKeys.keyPair) {
					preferences[__publicKeyKey] = newKeys.keyPair.public.toString()
					preferences[__privateKeyKey] = newKeys.keyPair.private.toString()
				}
				if (newKeys.keyOwner != oldKeys.keyOwner) {
					preferences[__keyOwnerKey] = newKeys.keyOwner
				}
			} else {
				preferences[__publicKeyKey] = ""
				preferences[__privateKeyKey] = ""
				preferences[__keyOwnerKey] = ""
			}
		}
	}
}
