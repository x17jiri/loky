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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Contact(
	val id: Long,
	val name: String,
	val send: Boolean,
	val recv: Boolean,
	val publicSigningKey: PublicSigningKey,
)

interface ContactsStore {
	val flow: Flow<List<Contact>>
	fun save(contact: Contact)
}

class ContactsStoreMock(
	val contacts: MutableMap<Long, Contact> = mutableMapOf<Long, Contact>()
): ContactsStore {

	val __flow = MutableStateFlow(contacts.values.toList())

	override fun flow(): Flow<List<Contact>> {
		return __flow
	}

	override fun save(contact: Contact) {
		contacts[contact.id] = contact
		__flow.value = contacts.values.toList()
	}
}

@Entity(
	tableName = "contacts",
	primaryKeys = ["id"],
)
class ContactDBEntity(
	val id: Long,
	val name: String,
	val send: Boolean,
	val recv: Boolean,
	val publicSigningKey: String,
) {
	fun toContact(): Contact? {
		val signingKey = PublicSigningKey.fromString(this.publicSigningKey).getOrNull()
		if (signingKey != null) {
			return Contact(
				id = this.id,
				name = this.name,
				send = this.send,
				recv = this.recv,
				publicSigningKey = signingKey
			)
		} else {
			return null
		}
	}
}

@Dao
interface ContactDao {
	@Query("UPDATE contacts SET send = :send WHERE id = :id")
	fun setSend(id: Long, send: Boolean)

	@Query("UPDATE contacts SET recv = :recv WHERE id = :id")
	fun setRecv(id: Long, recv: Boolean)

	@Query("UPDATE contacts SET publicSigningKey = :newSigningKey WHERE id = :contactId")
	fun updateSigningKey(contactId: Long, newSigningKey: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insertAll(vararg contacts: ContactDBEntity)

	@Query("DELETE FROM contacts WHERE id = :id")
	fun delete(id: Long)

	@Query("SELECT * FROM contacts")
	fun flow(): Flow<List<ContactDBEntity>>
}

class ContactsDBStore(
	val dao: ContactDao,
	val coroutineScope: CoroutineScope,
): ContactsStore {
	val mutex = Mutex()

	override fun flow(): Flow<List<Contact>> {
		return dao.flow().map { dbEntities ->
			dbEntities.mapNotNull { dbEntity -> dbEntity.toContact() }
		}
	}

    fun launchEdit(block: suspend (ContactDao) -> Unit) {
		coroutineScope.launch {
			mutex.withLock {
				block(dao)
			}
		}
    }

	override fun save(contact: Contact) {
		launchEdit { dao ->
			dao.insertAll(ContactDBEntity(
				id = contact.id,
				name = contact.name,
				send = contact.send,
				recv = contact.recv,
				publicSigningKey = contact.publicSigningKey.toString(),
			))
		}
	}
}
