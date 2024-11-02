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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SharingFrequency(val _seconds: Double = 15.0) {
	val seconds = max(5.0, min(180.0, _seconds))
	val secondsMin: Double get() = 0.9 * seconds
	val secondsMax: Double get() = 1.1 * seconds

	val ms: Int get() = (1000.0 * seconds).toInt()
	val msMin: Int get() = (0.9 * 1000.0 * seconds).toInt()
	val msMax: Int get() = (1.1 * 1000.0 * seconds).toInt()
}

interface SettingsStore {
	val shareFreq: StateFlow<SharingFrequency>
	val lastKeyResend: StateFlow<Long>

	fun setShareFreq(freq: SharingFrequency)
	suspend fun setLastKeyResend(time: Long)

	fun launchEdit(block: suspend (SettingsStore) -> Unit)
}

class SettingsDataStoreStore(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
): SettingsStore {
	companion object {
		val __shareFreqKey: Preferences.Key<String> = stringPreferencesKey("settings.shareFreq")

		val __lastKeyResendKey: Preferences.Key<String> = stringPreferencesKey("key.lastSwitch")
	}

	//-- shareFreq: SharingFrequency

	fun extractShareFreq(p: Preferences): SharingFrequency {
		val ms = p[__shareFreqKey]?.toIntOrNull()
		return if (ms != null) { SharingFrequency(ms.toDouble() / 1000.0) } else { SharingFrequency() }
	}

	fun shareFreqFlow(): Flow<SharingFrequency> {
		return __dataStore.data.map { data -> extractShareFreq(data) }
	}

	override val shareFreq = shareFreqFlow().stateIn(coroutineScope, SharingStarted.Eagerly, SharingFrequency())

	override fun setShareFreq(freq: SharingFrequency) {
		coroutineScope.launch {
			__dataStore.edit {
				val oldFreq = extractShareFreq(it)
				if (freq.ms != oldFreq.ms) {
					it[__shareFreqKey] = freq.ms.toString()
				}
			}
		}
	}

	//-- lastKeyResend: Long

	fun extractLastKeyResend(p: Preferences): Long {
		return p[__lastKeyResendKey]?.toLongOrNull() ?: 0
	}

	fun lastKeyResendFlow(): Flow<Long> = __dataStore.data.map { data -> extractLastKeyResend(data) }

	override val lastKeyResend by lazy {
		lastKeyResendFlow().stateIn(coroutineScope, SharingStarted.Eagerly, 0)
	}

	override suspend fun setLastKeyResend(time: Long) {
		__dataStore.edit { preferences ->
			val oldTime = extractLastKeyResend(preferences)
			if (time != oldTime) {
				preferences[__lastKeyResendKey] = time.toString()
			}
		}
	}

	//--

	override fun launchEdit(block: suspend (SettingsStore) -> Unit) {
		coroutineScope.launch {
			block(this@SettingsDataStoreStore)
		}
	}
}

