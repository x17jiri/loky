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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SharingFrequency(seconds: Double = 15.0) {
	val seconds = max(5.0, min(180.0, seconds))
	val secondsMin: Double get() = max(5.0, (0.9*seconds))
	val secondsMax: Double get() = max(1.1*seconds, secondsMin + 3.0)

	val ms: Int = (1_000.0 * seconds).toInt()
	val msMin: Int get() = (1_000.0 * secondsMin).toInt()
	val msMax: Int get() = (1_000.0 * secondsMax).toInt()
}

class SettingsManager(
	val __dataStore: DataStore<Preferences>,
	val coroutineScope: CoroutineScope,
) {
	companion object {
		val __shareFreqKey: Preferences.Key<String> = stringPreferencesKey("settings.shareFreq")
	}

	fun __getUptFreq(p: Preferences): SharingFrequency {
		val sec = p[__shareFreqKey]?.toIntOrNull()
		return if (sec != null) { SharingFrequency(sec.toDouble() / 1000.0) } else { SharingFrequency() }
	}

	fun shareFreqFlow(): Flow<SharingFrequency> {
		return __dataStore.data.map { data -> __getUptFreq(data) }
	}

	val shareFreq = shareFreqFlow().stateIn(coroutineScope, SharingStarted.Eagerly, SharingFrequency())

	fun launchUpdateShareFreq(func: (SharingFrequency) -> SharingFrequency) {
		coroutineScope.launch {
			__dataStore.edit {
				val oldFreq = __getUptFreq(it)
				val newFreq = func(oldFreq)
				if (newFreq.ms != oldFreq.ms) {
					it[__shareFreqKey] = newFreq.ms.toString()
				}
			}
		}
	}
}

