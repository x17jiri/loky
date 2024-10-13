package com.x17jiri.Loky

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Entity(tableName = "contacts")
data class Contact(
	@PrimaryKey val id: Long,
	@ColumnInfo(name = "name") val name: String,
	@ColumnInfo(name = "send") var send: Boolean,
	@ColumnInfo(name = "recv") var recv: Boolean,
	@ColumnInfo(name = "public_key") var publicKey: String,
	@ColumnInfo(name = "key_hash") var keyHash: String,
)

@Dao
interface ContactDao {
	@Query("SELECT * FROM contacts")
	fun getAll(): List<Contact>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insertAll(vararg contacts: Contact)

	@Delete
	fun delete(contact: Contact)
}

@Database(entities = [Contact::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
	abstract fun contactDao(): ContactDao

	companion object {
		private var instance: AppDatabase? = null

		fun getInstance(context: android.content.Context): AppDatabase {
			Log.d("Locodile", "AppDatabase.getInstance.1")
			if (instance == null) {
				Log.d("Locodile", "AppDatabase.getInstance.2")
				try {
					instance = Room.databaseBuilder(
						context.applicationContext,
						AppDatabase::class.java,
						"contacts_database"
					).build()
				} catch (e: Exception) {
					Log.d("Locodile", "AppDatabase.getInstance.2.1: e=$e")
					throw e
				}
				Log.d("Locodile", "AppDatabase.getInstance.3")
			}
			Log.d("Locodile", "AppDatabase.getInstance.4: instance=$instance")
			return instance!!
		}
	}
}

class ContactsManager(database: AppDatabase, scope: CoroutineScope) {
	private val database = database
	private val scope = scope;
	private val __contacts = MutableStateFlow<List<Contact>>(emptyList())
	val contacts: StateFlow<List<Contact>> = __contacts

	fun init() {
		try {
			scope.launch(Dispatchers.IO) {
				__contacts.value = database.contactDao().getAll()
			}
		} catch (e: Exception) {
			Log.d("Locodile", "ContactsManager.init: e=$e")
			throw e
		}
	}

	fun setSend(id: Long, send: Boolean) {
		val newList = __contacts.value.map<Contact, Contact> {
			if (it.id == id) {
				var newContact = it.copy(send = send)
				scope.launch(Dispatchers.IO) {
					database.contactDao().insertAll(newContact)
				}
				newContact
			} else {
				it
			}
		}
		__contacts.value = newList
	}

	fun setRecv(id: Long, recv: Boolean) {
		val newList = __contacts.value.map<Contact, Contact> {
			if (it.id == id) {
				var newContact = it.copy(recv = recv)
				scope.launch(Dispatchers.IO) {
					database.contactDao().insertAll(newContact)
				}
				newContact
			} else {
				it
			}
		}
		__contacts.value = newList
	}

	fun add(id: Long, name: String) {
		if (__contacts.value.any { it.id == id }) {
			return
		}

		val newContact = Contact(id, name, false, false, "", "")
		scope.launch(Dispatchers.IO) {
			database.contactDao().insertAll(newContact)
		}
		__contacts.value = __contacts.value + newContact
	}
}
