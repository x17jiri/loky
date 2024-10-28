package com.x17jiri.Loky

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// These are global variables, but during their initialization,
// they need a reference to the application context, so we cannot use `Lazy`.

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

val Context.__contactsStore by SingletonBase { appContext, scope ->
	ContactsDBStore(appContext.__database.contactDao(), scope)
}

val Context.__preKeyStore by SingletonBase { appContext, scope ->
	PreKeyDBStore(appContext.__database.preKeyDao(), scope)
}

val Context.__recvChanStateStore by SingletonBase { appContext, scope ->
	RecvChanStateDBStore(
		appContext.__contactsStore,
		appContext.__preKeyStore,
		appContext.__database.recvChanStateDao(),
		scope,
	)
}

val Context.__sendChanStateStore by SingletonBase { appContext, scope ->
	SendChanStateDBStore(
		appContext.__profileStore,
		appContext.__contactsStore,
		appContext.__database.sendChanStateDao(),
		scope,
	)
}

val Context.__profileStore by SingletonBase { appContext, scope ->
	ProfileDataStoreStore(appContext.__dataStore, scope)
}

val Context.__server by SingletonBase { appContext, scope ->
	ServerInterface(appContext, scope)
}

val Context.__inboxMan by SingletonBase { appContext, scope ->
	InboxManager(appContext.__database, scope)
}

val Context.__settings by SingletonBase { appContext, scope ->
	SettingsManager(appContext.__dataStore, scope)
}

