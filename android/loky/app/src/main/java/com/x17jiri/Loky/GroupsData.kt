package com.x17jiri.Loky

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SecretKey {
	var __key: ByteArray = ByteArray(16)

	@OptIn(ExperimentalEncodingApi::class)
	val text: String get() { return Base64.encode(__key) }

	@OptIn(ExperimentalEncodingApi::class)
	fun setText(value: String): Boolean {
		return Base64.decode(value).let {
			if (it.size == 16) {
				__key = it
				true
			} else {
				false
			}
		}
	}

	companion object {
		@OptIn(ExperimentalEncodingApi::class)
		fun newRandom(): String {
			var key = ByteArray(16)
			SecureRandom().nextBytes(key)
			return Base64.encode(key)
		}
	}
}

class GroupData(i: Int) {
	val id: Int
	val name: String get() { return __name }
	val secretKey: SecretKey get() { return __secretKey }
	val enabled: Boolean get() { return __enabled }

	var __used: Boolean = false
	var __name: String = ""
	var __secretKey: SecretKey = SecretKey()
	var __enabled: Boolean = false
	val __nameKey: Preferences.Key<String>
	val __secretKeyKey: Preferences.Key<String>
	val __enabledKey: Preferences.Key<String>

	init {
		id = i
		__nameKey = stringPreferencesKey("group[${i}].name")
		__secretKeyKey = stringPreferencesKey("group[${i}].secretKey")
		__enabledKey = stringPreferencesKey("group[${i}].enabled")
	}

	fun init(name: String) {
		__name = name
		__secretKey.setText(SecretKey.newRandom())
		__used = true
		__enabled = false
	}

	fun load(preferences: Preferences): Boolean {
		val name = preferences[__nameKey]
		val secretKey = preferences[__secretKeyKey]
		val enabled = preferences[__enabledKey] ?: "false"

		if (name == null || secretKey == null) {
			return false
		}

		__name = name
		if (!__secretKey.setText(secretKey)) {
			return false
		}
		__enabled = enabled == "true"

		this.__used = true
		return true
	}

	fun save(preferences: MutablePreferences) {
		preferences[__nameKey] = __name
		preferences[__secretKeyKey] = __secretKey.text
		preferences[__enabledKey] = if (__enabled) "true" else "false"
	}
}

const val MAX_GROUPS: Int = 10

data class IDsPlusEnabled(val id: Int, val enabled: Boolean)

class GroupsData(dataStore: DataStore<Preferences>) {
	var __dataStore = dataStore
	var __order: MutableList<Int> = mutableListOf()
	var __order_key: Preferences.Key<String> = stringPreferencesKey("groups_order")

	val n: Int
		get() { return __order.count() }

	var __groups: Array<GroupData> = Array<GroupData>(
		MAX_GROUPS,
		init = { GroupData(it) }
	)

	init {
		runBlocking {
			val preferences = dataStore.data.first()
			val order1 = preferences[__order_key]
			if (order1 != null) {
				val order2 = order1.split(",")
				val order3 = order2.mapNotNull { it.trim().toIntOrNull() }
				for (i in order3) {
					if (
						i >= 0
						&& i < MAX_GROUPS
						&& !__groups[i].__used
						&& __groups[i].load(preferences)
					) {
						__order.add(i)
					}
				}
			}
		}
	}

	fun __saveOrder(preferences: MutablePreferences) {
		preferences[__order_key] = __order.joinToString(separator = ",")
	}

	fun add(name: String): Int? {
		val i = __groups.indexOfFirst { !it.__used }
		if (n >= MAX_GROUPS || i < 0) {
			return null
		}
		__groups[i].init(name)
		__order.add(i)
		runBlocking {
			__dataStore.edit {
				__groups[i].save(it)
				__saveOrder(it)
			}
		}
		return i
	}

	fun remove(id: Int) {
		if (id >= MAX_GROUPS || id < 0) {
			return
		}
		__groups[id].__used = false
		__order.removeAll { it == id }
		runBlocking {
			__dataStore.edit {
				__saveOrder(it)
			}
		}
	}

	fun update(id: Int, name: String, enabled: Boolean) {
		if (id < 0 || id >= MAX_GROUPS) {
			return
		}
		__groups[id].__name = name
		__groups[id].__enabled = enabled
		runBlocking {
			__dataStore.edit {
				__groups[id].save(it)
			}
		}
	}

	fun enable(id: Int, enabled: Boolean) {
		if (id < 0 || id >= MAX_GROUPS) {
			return
		}
		__groups[id].__enabled = enabled
		runBlocking {
			__dataStore.edit {
				__groups[id].save(it)
			}
		}
	}

	operator fun get(id: Int): GroupData {
		return __groups[id]
	}

	fun IDs(): List<Int> {
		return __order.toList()
	}

	fun IDsPlusEnabled(): List<IDsPlusEnabled> {
		return __order.map { IDsPlusEnabled(it, __groups[it].__enabled) }
	}
}

