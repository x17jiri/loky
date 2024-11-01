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
import java.io.ByteArrayInputStream

data class Credentials(
	val username: String = "",
	val passwd: String = "",
)

data class SigningKeys(
	val keyPair: SigningKeyPair? = null,
	val keyOwner: String = "",
)

interface ProfileStore {
	val cred: StateFlow<Credentials>
	val bearer: StateFlow<String>
	val signingKeys: StateFlow<SigningKeys>

	suspend fun setCred(cred: Credentials)
	suspend fun setBearer(bearer: String)
	suspend fun setSigningKeys(newKeys: SigningKeys)

	suspend fun setSigningKeys(keyPair: SigningKeyPair, owner: String) {
		setSigningKeys(SigningKeys(keyPair, owner))
	}

	fun launchEdit(block: suspend (ProfileStore) -> Unit)
}

class ProfileDataStoreStore(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
): ProfileStore {
	companion object {
		val __usernameKey: Preferences.Key<String> = stringPreferencesKey("login.username")
		val __passwdKey: Preferences.Key<String> = stringPreferencesKey("login.passwd")

		val __bearerKey: Preferences.Key<String> = stringPreferencesKey("login.bearer")

		val __publicKeyKey = stringPreferencesKey("key.public")
		val __privateKeyKey = stringPreferencesKey("key.private")
		val __keyOwnerKey = stringPreferencesKey("key.owner")
	}

	//-- cred: Credentials

	fun extractCred(p: Preferences): Credentials {
		return Credentials(
			username = p[__usernameKey] ?: "",
			passwd = p[__passwdKey] ?: "",
		)
	}

	fun credFlow(): Flow<Credentials> = __dataStore.data.map { data -> extractCred(data) }

	override val cred by lazy {
		credFlow().stateIn(coroutineScope, SharingStarted.Eagerly, Credentials())
	}

	override suspend fun setCred(cred: Credentials) {
		__dataStore.edit { preferences ->
			val oldCreds = extractCred(preferences)
			if (cred.username != oldCreds.username) {
				preferences[__usernameKey] = cred.username
			}
			if (cred.passwd != oldCreds.passwd) {
				preferences[__passwdKey] = cred.passwd
			}
		}
	}

	//-- bearer: String

	fun extractBearer(p: Preferences): String {
		return p[__bearerKey] ?: ""
	}

	fun bearerFlow(): Flow<String> = __dataStore.data.map { data -> extractBearer(data) }

	override val bearer by lazy {
		bearerFlow().stateIn(coroutineScope, SharingStarted.Eagerly, "")
	}

	override suspend fun setBearer(bearer: String) {
		__dataStore.edit { preferences ->
			val oldBearer = extractBearer(preferences)
			Log.d("Locodile", "setBearer: $bearer")
			if (bearer != oldBearer) {
				preferences[__bearerKey] = bearer
			}
		}
	}

	//-- signingKeys: SigningKeys

	fun extractSigningKeys(p: Preferences): SigningKeys {
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

	fun signingKeysFlow(): Flow<SigningKeys> = __dataStore.data.map { data -> extractSigningKeys(data) }

	override val signingKeys by lazy {
		signingKeysFlow().stateIn(coroutineScope, SharingStarted.Eagerly, SigningKeys())
	}

	override suspend fun setSigningKeys(newKeys: SigningKeys) {
		__dataStore.edit { preferences ->
			val oldKeys = extractSigningKeys(preferences)
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

	//--

	fun launchEdit(block: suspend (ProfileStore) -> Unit) {
		coroutineScope.launch {
			block(this@ProfileDataStoreStore)
		}
	}
}
