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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharingFrequency(seconds: Double = 15.0) {
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

	suspend fun init()

	fun launchEdit(block: suspend (dao: SettingsStoreDao) -> Unit)
}

interface SettingsStoreDao {
	suspend fun setShareFreq(freq: SharingFrequency)
	suspend fun setLastKeyResend(time: Long)
}

class SettingsDataStoreStore(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
): SettingsStore, SettingsStoreDao {
	companion object {
		val __shareFreqKey: Preferences.Key<String> = stringPreferencesKey("settings.shareFreq")

		val __lastKeyResendKey: Preferences.Key<String> = stringPreferencesKey("key.lastSwitch")
	}

	val __shareFreq = MutableStateFlow(SharingFrequency())
	val __lastKeyResend = MutableStateFlow(0L)

	override val shareFreq = __shareFreq
	override val lastKeyResend = __lastKeyResend

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
				val ms = preferences[__shareFreqKey]?.toIntOrNull()
				__shareFreq.value =
					if (ms != null) {
						SharingFrequency(ms.toDouble() / 1000.0)
					} else {
						SharingFrequency()
					}

				// lastKeyResend
				__lastKeyResend.value = preferences[__lastKeyResendKey]?.toLongOrNull() ?: 0
			}
		}
	}

	override suspend fun setShareFreq(freq: SharingFrequency) {
		__dataStore.edit { preferences ->
			preferences[__shareFreqKey] = freq.ms.toString()
		}
		__shareFreq.value = freq
	}

	override suspend fun setLastKeyResend(time: Long) {
		__dataStore.edit { preferences ->
			preferences[__lastKeyResendKey] = time.toString()
		}
		__lastKeyResend.value = time
	}

	override fun launchEdit(block: suspend (dao: SettingsStoreDao) -> Unit) {
		coroutineScope.launch {
			block(this@SettingsDataStoreStore)
		}
	}
}

