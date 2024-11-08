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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

	suspend fun init();

	fun launchEdit(block: suspend (dao: ProfileStoreDao) -> Unit)
}

interface ProfileStoreDao {
	suspend fun setCred(cred: Credentials)
	suspend fun setBearer(bearer: String)
	suspend fun setSigningKeys(newKeys: SigningKeys)

	suspend fun setSigningKeys(keyPair: SigningKeyPair, owner: String) {
		setSigningKeys(SigningKeys(keyPair, owner))
	}
}

class ProfileDataStoreStore(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
): ProfileStore, ProfileStoreDao {
	companion object {
		val __usernameKey: Preferences.Key<String> = stringPreferencesKey("login.username")
		val __passwdKey: Preferences.Key<String> = stringPreferencesKey("login.passwd")

		val __bearerKey: Preferences.Key<String> = stringPreferencesKey("login.bearer")

		val __publicKeyKey = stringPreferencesKey("key.public")
		val __privateKeyKey = stringPreferencesKey("key.private")
		val __keyOwnerKey = stringPreferencesKey("key.owner")
	}

	val __cred = MutableStateFlow(Credentials())
	val __bearer = MutableStateFlow("")
	val __signingKeys = MutableStateFlow(SigningKeys())

	override val cred: StateFlow<Credentials> = __cred
	override val bearer: StateFlow<String> = __bearer
	override val signingKeys: StateFlow<SigningKeys> = __signingKeys

	val __mutex = Mutex()
	var __initialized = false

	override suspend fun init() {
		__mutex.withLock {
			if (__initialized) {
				return
			}
			__initialized = true

			val preferences = __dataStore.data.first()
			// cred
			__cred.value = Credentials(
				username = preferences[__usernameKey] ?: "",
				passwd = preferences[__passwdKey] ?: "",
			)

			// bearer
			__bearer.value = preferences[__bearerKey] ?: ""

			// signingKeys
			val public = preferences[__publicKeyKey] ?: ""
			val private = preferences[__privateKeyKey] ?: ""
			val owner = preferences[__keyOwnerKey] ?: ""

			val publicKey = PublicSigningKey.fromString(public).getOrNull()
			val privateKey = PrivateSigningKey.fromString(private).getOrNull()
			if (publicKey != null && privateKey != null && owner.isNotEmpty()) {
				__signingKeys.value = SigningKeys(
					keyPair = SigningKeyPair(publicKey, privateKey),
					keyOwner = owner,
				)
			}
		}
	}

	//-- cred: Credentials

	override suspend fun setCred(cred: Credentials) {
		__dataStore.edit { preferences ->
			preferences[__usernameKey] = cred.username
			preferences[__passwdKey] = cred.passwd
		}
		__cred.value = cred
	}

	//-- bearer: String

	override suspend fun setBearer(bearer: String) {
		__dataStore.edit { preferences ->
			preferences[__bearerKey] = bearer
		}
		__bearer.value = bearer
	}

	//-- signingKeys: SigningKeys

	override suspend fun setSigningKeys(newKeys: SigningKeys) {
		__dataStore.edit { preferences ->
			if (newKeys.keyPair != null && newKeys.keyOwner != "") {
				preferences[__publicKeyKey] = newKeys.keyPair.public.toString()
				preferences[__privateKeyKey] = newKeys.keyPair.private.toString()
				preferences[__keyOwnerKey] = newKeys.keyOwner
			} else {
				preferences[__publicKeyKey] = ""
				preferences[__privateKeyKey] = ""
				preferences[__keyOwnerKey] = ""
			}
		}
		__signingKeys.value = newKeys
	}

	//--

	override fun launchEdit(block: suspend (dao: ProfileStoreDao) -> Unit) {
		coroutineScope.launch {
			block(this@ProfileDataStoreStore)
		}
	}
}
