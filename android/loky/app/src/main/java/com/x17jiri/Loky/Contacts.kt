package com.x17jiri.Loky

import android.content.Context
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(
	tableName = "contacts",
	primaryKeys = ["id"],
)
data class Contact(
	val id: Long,
	val name: String,
	val send: Boolean,
	val recv: Boolean,
	val publicKey: String,
	val keyHash: String,
)

@Dao
interface ContactDao {
	@Query("UPDATE contacts SET send = :send WHERE id = :id")
	fun setSend(id: Long, send: Boolean)

	@Query("UPDATE contacts SET recv = :recv WHERE id = :id")
	fun setRecv(id: Long, recv: Boolean)

	@Query("UPDATE contacts SET publicKey = :newPublicKey, keyHash = :newKeyHash WHERE id = :contactId")
	fun updateKey(contactId: Long, newPublicKey: String, newKeyHash: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insertAll(vararg contacts: Contact)

	@Query("DELETE FROM contacts WHERE id = :id")
	fun delete(id: Long)

	@Query("SELECT * FROM contacts")
	fun flow(): Flow<List<Contact>>
}

class ContactsManager(
	val database: AppDatabase,
	val coroutineScope: CoroutineScope,
) {
	companion object {
		val __mutex = Mutex()
	}

	val __contactDao = database.contactDao()

	fun flow(): Flow<List<Contact>> {
		return __contactDao.flow()
	}

    fun launchEdit(block: suspend (ContactDao) -> Unit) {
		coroutineScope.launch {
			__mutex.withLock {
				block(__contactDao)
			}
		}
    }
}
