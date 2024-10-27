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
import kotlinx.coroutines.GlobalScope
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
	fun flow(): Flow<List<Contact>>

	suspend fun setSend(contact: Contact, send: Boolean)
	suspend fun setRecv(contact: Contact, recv: Boolean)
	suspend fun updateSigningKey(contact: Contact, newSigningKey: PublicSigningKey)
	suspend fun insert(contact: Contact)
	suspend fun delete(contact: Contact)

	fun launchEdit(block: suspend (ContactsStore) -> Unit)
}

class ContactsStoreMock(
	val contacts: MutableMap<Long, Contact> = mutableMapOf<Long, Contact>(),
	val coroutineScope: CoroutineScope = GlobalScope,
): ContactsStore {
	val __flow = MutableStateFlow(contacts.values.toList())

	override fun flow(): Flow<List<Contact>> {
		return __flow
	}

	override suspend fun setSend(contact: Contact, send: Boolean) {
		contacts[contact.id] = contact.copy(send = send)
		__flow.update { contacts.values.toList() }
	}

	override suspend fun setRecv(contact: Contact, recv: Boolean) {
		contacts[contact.id] = contact.copy(recv = recv)
		__flow.update { contacts.values.toList() }
	}

	override suspend fun updateSigningKey(contact: Contact, newSigningKey: PublicSigningKey) {
		contacts[contact.id] = contact.copy(publicSigningKey = newSigningKey)
		__flow.update { contacts.values.toList() }
	}

	override suspend fun insert(contact: Contact) {
		contacts[contact.id] = contact
		__flow.update { contacts.values.toList() }
	}

	override suspend fun delete(contact: Contact) {
		contacts.remove(contact.id)
		__flow.update { contacts.values.toList() }
	}

	override fun launchEdit(block: suspend (ContactsStore) -> Unit) {
		coroutineScope.launch {
			block(this@ContactsStoreMock)
		}
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
	suspend fun setSend(id: Long, send: Boolean): Int

	@Query("UPDATE contacts SET recv = :recv WHERE id = :id")
	suspend fun setRecv(id: Long, recv: Boolean)

	@Query("UPDATE contacts SET publicSigningKey = :newSigningKey WHERE id = :contactId")
	suspend fun updateSigningKey(contactId: Long, newSigningKey: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAll(vararg contacts: ContactDBEntity)

	@Query("DELETE FROM contacts WHERE id = :id")
	suspend fun delete(id: Long)

	@Query("SELECT * FROM contacts")
	fun flow(): Flow<List<ContactDBEntity>>
}

class ContactsDBStore(
	val dao: ContactDao,
	val coroutineScope: CoroutineScope,
): ContactsStore {
	override fun flow(): Flow<List<Contact>> {
		return dao.flow().map { dbEntities ->
			dbEntities.mapNotNull { dbEntity -> dbEntity.toContact() }
		}
	}

	override suspend fun setSend(contact: Contact, send: Boolean) {
		dao.setSend(contact.id, send)
	}

	override suspend fun setRecv(contact: Contact, recv: Boolean) {
		dao.setRecv(contact.id, recv)
	}

	override suspend fun updateSigningKey(contact: Contact, newSigningKey: PublicSigningKey) {
		dao.updateSigningKey(contact.id, newSigningKey.toString())
	}

	override suspend fun insert(contact: Contact) {
		val dbEntity = ContactDBEntity(
			id = contact.id,
			name = contact.name,
			send = contact.send,
			recv = contact.recv,
			publicSigningKey = contact.publicSigningKey.toString()
		)
		dao.insertAll(dbEntity)
	}

	override suspend fun delete(contact: Contact) {
		dao.delete(contact.id)
	}

	private val mutex = Mutex()

    override fun launchEdit(block: suspend (ContactsStore) -> Unit) {
		coroutineScope.launch {
			mutex.withLock {
				block(this@ContactsDBStore)
			}
		}
    }
}
