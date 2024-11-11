package com.x17jiri.Loky

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharingFrequency(seconds: Double = 5.0) {
	val seconds = max(5.0, min(180.0, seconds))
	val secondsMin: Double get() = 0.9 * seconds
	val secondsMax: Double get() = 1.1 * seconds

	val ms: Int get() = (1000.0 * seconds).toInt()
	val msMin: Int get() = (0.9 * 1000.0 * seconds).toInt()
	val msMax: Int get() = (1.1 * 1000.0 * seconds).toInt()
}

interface SettingsStore {
	val shareFreq: StateFlow<SharingFrequency>
	val lastKeyResend: StateFlow<Long>
	val lastCleanUp: StateFlow<Long>

	suspend fun init()

	fun getDao(): SettingsStoreDao
	fun launchEdit(block: suspend (dao: SettingsStoreDao) -> Unit)
}

interface SettingsStoreDao {
	suspend fun setShareFreq(freq: SharingFrequency)
	suspend fun setLastKeyResend(time: Long)
	suspend fun setLastCleanUp(time: Long)
}

class SettingsStoreMock: SettingsStore, SettingsStoreDao {
	override val shareFreq = MutableStateFlow(SharingFrequency())
	override val lastKeyResend = MutableStateFlow(0L)
	override val lastCleanUp = MutableStateFlow(0L)

	override suspend fun init() {}

	override fun getDao(): SettingsStoreDao {
		return this
	}

	override fun launchEdit(block: suspend (dao: SettingsStoreDao) -> Unit) {
		runBlocking {
			block(this@SettingsStoreMock)
		}
	}

	override suspend fun setShareFreq(freq: SharingFrequency) {
		shareFreq.value = freq
	}

	override suspend fun setLastKeyResend(time: Long) {
		lastKeyResend.value = time
	}

	override suspend fun setLastCleanUp(time: Long) {
		lastCleanUp.value = time
	}
}

class SettingsDataStoreStore(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
): SettingsStore, SettingsStoreDao {
	companion object K {
		val shareFreq: Preferences.Key<String> = stringPreferencesKey("settings.shareFreq")
		val lastKeyResend: Preferences.Key<String> = stringPreferencesKey("key.lastSwitch")
		val lastCleanUp: Preferences.Key<String> = stringPreferencesKey("inbox.lastCleanUp")
	}

	val __shareFreq = MutableStateFlow(SharingFrequency())
	val __lastKeyResend = MutableStateFlow(0L)
	val __lastCleanUp = MutableStateFlow(0L)

	override val shareFreq = __shareFreq
	override val lastKeyResend = __lastKeyResend
	override val lastCleanUp = __lastCleanUp

	val __mutex = Mutex()
	var __initialized = false

	override suspend fun init() {
		__mutex.withLock {
			if (__initialized) {
				return
			}
			__initialized = true

			__dataStore.edit { preferences ->
				// shareFreq
				val ms = preferences[K.shareFreq]?.toIntOrNull()
				__shareFreq.value =
					if (ms != null) {
						SharingFrequency(ms.toDouble() / 1000.0)
					} else {
						SharingFrequency()
					}

				// lastKeyResend, lastCleanUp
				__lastKeyResend.value = preferences[K.lastKeyResend]?.toLongOrNull() ?: 0
				__lastCleanUp.value = preferences[K.lastCleanUp]?.toLongOrNull() ?: 0
			}
		}
	}

	override suspend fun setShareFreq(freq: SharingFrequency) {
		__dataStore.edit { preferences ->
			preferences[K.shareFreq] = freq.ms.toString()
		}
		__shareFreq.value = freq
	}

	override suspend fun setLastKeyResend(time: Long) {
		__dataStore.edit { preferences ->
			preferences[K.lastKeyResend] = time.toString()
		}
		__lastKeyResend.value = time
	}

	override suspend fun setLastCleanUp(time: Long) {
		__dataStore.edit { preferences ->
			preferences[K.lastCleanUp] = time.toString()
		}
		__lastCleanUp.value = time
	}

	override fun getDao(): SettingsStoreDao {
		return this
	}

	override fun launchEdit(block: suspend (dao: SettingsStoreDao) -> Unit) {
		coroutineScope.launch {
			block(this@SettingsDataStoreStore)
		}
	}
}

