package com.x17jiri.Loky

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class GroupData(public val id: Int) {
	val name: String get() { return __name }
	val secretKey: SecretKey get() { return __secretKey }
	val enabled: Boolean get() { return __enabled }

	var __used: Boolean = false
	var __name: String = ""
	var __secretKey: SecretKey = SecretKey()
	var __enabled: Boolean = false
	val __nameKey = stringPreferencesKey("group[${id}].name")
	val __secretKeyKey = stringPreferencesKey("group[${id}].secretKey")
	val __enabledKey = stringPreferencesKey("group[${id}].enabled")

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

class GroupsManager(dataStore: DataStore<Preferences>) {
	private var __dataStore = dataStore

	private var __order = MutableStateFlow(listOf<Int>())
	val order: StateFlow<List<Int>> = __order.asStateFlow()

	private var __groups = MutableStateFlow(listOf<GroupData>())
	val groups: StateFlow<List<GroupData>> = __groups.asStateFlow()

	private val __order_key: Preferences.Key<String> = stringPreferencesKey("groups_order")

	suspend fun init() {
		__dataStore.data.first().let { preferences ->
			val order1 = preferences[__order_key]
			if (order1 != null) {
				val order2 = order1.split(",")
				val order3 = order2.mapNotNull { it.trim().toIntOrNull() }
				var order = mutableListOf<Int>()
				var groups = MutableList(MAX_GROUPS) { GroupData(it) }
				for (i in order3) {
					if (
						i >= 0
						&& i < MAX_GROUPS
						&& !groups[i].__used
						&& groups[i].load(preferences)
					) {
						order.add(i)
					}
				}
				__order.value = order
				__groups.value = groups
			}
		}
	}

	suspend fun observe_order() {
		__order.collect { newOrder ->
			__dataStore.edit { preferences ->
				preferences[__order_key] = newOrder.joinToString(separator = ",")
			}
		}
	}

	suspend fun objserve_groups() {
		__groups.collect { newGroups ->
			__dataStore.edit { preferences ->
				for (group in newGroups) {
					group.save(preferences)
				}
			}
		}
	}

	fun add(name: String): Int? {
		var groups = __groups.value.toMutableList()
		var order = __order.value.toMutableList()
		// TODO - select one of the free IDs randomly
		val id = groups.indexOfFirst { !it.__used }
		if (id < 0 || order.count() >= MAX_GROUPS) {
			return null
		}

		groups[id].init(name)
		order.add(id)

		__groups.value = groups
		__order.value = order

		return id
	}

	fun remove(id: Int) {
		if (id < 0 || id >= MAX_GROUPS) {
			return
		}
		val groups = __groups.value.toMutableList()
		val order = __order.value.toMutableList()

		groups[id].__used = false
		order.removeAll { it == id }

		__groups.value = groups
		__order.value = order
	}

	fun update(id: Int, name: String, enabled: Boolean) {
		if (id < 0 || id >= MAX_GROUPS) {
			return
		}
		val groups = __groups.value.toMutableList()

		groups[id].__name = name
		groups[id].__enabled = enabled

		__groups.value = groups
	}

	fun enable(id: Int, enabled: Boolean) {
		if (id < 0 || id >= MAX_GROUPS) {
			return
		}
		val groups = __groups.value.toMutableList()

		groups[id].__enabled = enabled

		__groups.value = groups
	}

	fun get(id: Int): GroupData {
		return __groups.value[id]
	}

	fun set(id: Int, group: GroupData) {
		val groups = __groups.value.toMutableList()
		groups[id] = group
		__groups.value = groups
	}
}

