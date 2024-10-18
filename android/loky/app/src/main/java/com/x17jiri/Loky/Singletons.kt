package com.x17jiri.Loky

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class SingletonBase<T>(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
	create: (Context, CoroutineScope) -> T
): ReadOnlyProperty<Context, T> {
	@Volatile
	private var __instance: T? = null

	private var __scope: CoroutineScope? = scope
	private var __create: ((Context, CoroutineScope) -> T)? = create

	override fun getValue(thisRef: Context, property: KProperty<*>): T {
		val instance = __instance
		if (instance != null) {
			return instance
		}

		synchronized(this) {
			val instance = __instance
			if (instance != null) {
				return instance
			}

			val create: (Context, CoroutineScope) -> T = __create!!
			val scope: CoroutineScope = __scope!!

			val new_instance = create(thisRef.applicationContext, scope)

			__create = null
			__scope = null

			__instance = new_instance
			return new_instance
		}
	}
}

val Context.__dataStore by preferencesDataStore(name = "settings")

val Context.__database by SingletonBase { appContext, scope ->
	Room.databaseBuilder(
		appContext,
		AppDatabase::class.java,
		"x17loky_database"
	).build()
}

val Context.__credMan by SingletonBase { appContext, scope ->
	CredentialsManager(appContext.__dataStore, scope)
}

val Context.__contactsMan by SingletonBase { appContext, scope ->
	ContactsManager(appContext.__database, scope)
}

val Context.__server by SingletonBase { appContext, scope ->
	ServerInterface(appContext, scope)
}
