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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream

data class Credentials(
	val username: String = "",
	val passwd: String = "",
)

class MainKeys(
	val sign: SigningKeyPair? = null,
	val master: DHKeyPair? = null,
	val owner: String = "",
) {
	fun validFor(username: String): Boolean {
		return sign != null && master != null && owner == username
	}
}

interface ProfileStore {
	val cred: StateFlow<Credentials>
	val bearer: StateFlow<String>
	val mainKeys: StateFlow<MainKeys>

	suspend fun init();

	fun launchEdit(block: suspend (dao: ProfileStoreDao) -> Unit)
}

interface ProfileStoreDao {
	suspend fun setCred(cred: Credentials)
	suspend fun setBearer(bearer: String)
	suspend fun setMainKeys(newKeys: MainKeys)

	suspend fun setMainKeys(sign: SigningKeyPair, master: DHKeyPair, owner: String) {
		setMainKeys(MainKeys(sign, master, owner))
	}
}

class ProfileStoreMock: ProfileStore, ProfileStoreDao {
	private val __cred = MutableStateFlow(Credentials())
	private val __bearer = MutableStateFlow("")
	private val __mainKeys = MutableStateFlow(MainKeys())

	override val cred: StateFlow<Credentials> = __cred
	override val bearer: StateFlow<String> = __bearer
	override val mainKeys: StateFlow<MainKeys> = __mainKeys

	override suspend fun init() {
	}

	override suspend fun setCred(cred: Credentials) {
		__cred.value = cred
	}

	override suspend fun setBearer(bearer: String) {
		__bearer.value = bearer
	}

	override suspend fun setMainKeys(newKeys: MainKeys) {
		__mainKeys.value = newKeys
	}

	override fun launchEdit(block: suspend (dao: ProfileStoreDao) -> Unit) {
		val dao = this
		runBlocking { block(dao) }
	}
}

class ProfileDataStoreStore(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
): ProfileStore, ProfileStoreDao {
	companion object K {
		val username: Preferences.Key<String> = stringPreferencesKey("login.username")
		val passwd: Preferences.Key<String> = stringPreferencesKey("login.passwd")

		val bearer: Preferences.Key<String> = stringPreferencesKey("login.bearer")

		val signPublic = stringPreferencesKey("key.sign.public")
		val signPrivate = stringPreferencesKey("key.sign.private")
		val masterPublic = stringPreferencesKey("key.master.public")
		val masterPrivate = stringPreferencesKey("key.master.private")
		val owner = stringPreferencesKey("key.owner")
	}

	val __cred = MutableStateFlow(Credentials())
	val __bearer = MutableStateFlow("")
	val __mainKeys = MutableStateFlow(MainKeys())

	override val cred: StateFlow<Credentials> = __cred
	override val bearer: StateFlow<String> = __bearer
	override val mainKeys: StateFlow<MainKeys> = __mainKeys

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
				username = preferences[K.username] ?: "",
				passwd = preferences[K.passwd] ?: "",
			)

			// bearer
			__bearer.value = preferences[K.bearer] ?: ""

			// mainKeys
			val signPublicStr = preferences[K.signPublic] ?: ""
			val signPrivateStr = preferences[K.signPrivate] ?: ""
			val masterPublicStr = preferences[K.masterPublic] ?: ""
			val masterPrivateStr = preferences[K.masterPrivate] ?: ""
			val owner = preferences[K.owner] ?: ""

			val signPublic = PublicSigningKey.fromString(signPublicStr).getOrNull()
			val signPrivate = PrivateSigningKey.fromString(signPrivateStr).getOrNull()
			val masterPublic = PublicDHKey.fromString(masterPublicStr).getOrNull()
			val masterPrivate = PrivateDHKey.fromString(masterPrivateStr).getOrNull()
			if (
				signPublic != null
				&& signPrivate != null
				&& masterPublic != null
				&& masterPrivate != null
				&& owner.isNotEmpty()
			) {
				__mainKeys.value = MainKeys(
					sign = SigningKeyPair(signPublic, signPrivate),
					master = DHKeyPair(masterPublic, masterPrivate),
					owner = owner,
				)
			}
		}
	}

	//-- cred: Credentials

	override suspend fun setCred(cred: Credentials) {
		__dataStore.edit { preferences ->
			preferences[K.username] = cred.username
			preferences[K.passwd] = cred.passwd
		}
		__cred.value = cred
	}

	//-- bearer: String

	override suspend fun setBearer(bearer: String) {
		__dataStore.edit { preferences ->
			preferences[K.bearer] = bearer
		}
		__bearer.value = bearer
	}

	//-- signingKeys: SigningKeys

	override suspend fun setMainKeys(newKeys: MainKeys) {
		__dataStore.edit { preferences ->
			if (
				newKeys.sign != null
				&& newKeys.master != null
				&& newKeys.owner != ""
			) {
				preferences[K.signPublic] = newKeys.sign.public.toString()
				preferences[K.signPrivate] = newKeys.sign.private.toString()
				preferences[K.masterPublic] = newKeys.master.public.toString()
				preferences[K.masterPrivate] = newKeys.master.private.toString()
				preferences[K.owner] = newKeys.owner
			} else {
				preferences[K.signPublic] = ""
				preferences[K.signPrivate] = ""
				preferences[K.masterPublic] = ""
				preferences[K.masterPrivate] = ""
				preferences[K.owner] = ""
			}
		}
		__mainKeys.value = newKeys
	}

	//--

	override fun launchEdit(block: suspend (dao: ProfileStoreDao) -> Unit) {
		coroutineScope.launch {
			block(this@ProfileDataStoreStore)
		}
	}
}
