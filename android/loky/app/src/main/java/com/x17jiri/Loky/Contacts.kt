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
	val id: String,
	val name: String,
	val send: Boolean,
	val recv: Boolean,
	val signKey: PublicSigningKey,
	val masterKey: PublicDHKey,
)

interface ContactsStore {
	fun flow(): Flow<List<Contact>>

	suspend fun setSend(contact: Contact, send: Boolean)
	suspend fun setRecv(contact: Contact, recv: Boolean)
	suspend fun setKeys(contact: Contact, sign: PublicSigningKey, master: PublicDHKey)
	suspend fun insert(contact: Contact)
	suspend fun delete(contact: Contact)

	fun launchEdit(block: suspend (ContactsStore) -> Unit)
}

class ContactsStoreMock(
	val contacts: MutableMap<String, Contact> = mutableMapOf<String, Contact>(),
	val coroutineScope: CoroutineScope = GlobalScope,
): ContactsStore {
	val __flow = MutableStateFlow(contacts.values.toList())
	val __mutex = Mutex()

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

	override suspend fun setKeys(contact: Contact, sign: PublicSigningKey, master: PublicDHKey) {
		contacts[contact.id] = contact.copy(signKey = sign, masterKey = master)
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
			__mutex.withLock {
				block(this@ContactsStoreMock)
			}
		}
	}
}

@Entity(
	tableName = "contacts",
	primaryKeys = ["id"],
)
class ContactDBEntity(
	val id: String,
	val name: String,
	val send: Boolean,
	val recv: Boolean,
	val signKey: String,
	val masterKey: String,
) {
	fun toContact(): Contact? {
		val signKey = PublicSigningKey.fromString(this.signKey).getOrNull()
		val masterKey = PublicDHKey.fromString(this.masterKey).getOrNull()
		if (signKey != null && masterKey != null) {
			return Contact(
				id = this.id,
				name = this.name,
				send = this.send,
				recv = this.recv,
				signKey = signKey,
				masterKey = masterKey,
			)
		} else {
			return null
		}
	}
}

@Dao
interface ContactDao {
	@Query("UPDATE contacts SET send = :send WHERE id = :id")
	suspend fun setSend(id: String, send: Boolean): Int

	@Query("UPDATE contacts SET recv = :recv WHERE id = :id")
	suspend fun setRecv(id: String, recv: Boolean)

	@Query("UPDATE contacts SET signKey = :sign, masterKey = :master WHERE id = :id")
	suspend fun setKeys(id: String, sign: String, master: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAll(vararg contacts: ContactDBEntity)

	@Query("DELETE FROM contacts WHERE id = :id")
	suspend fun delete(id: String)

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

	override suspend fun setKeys(contact: Contact, sign: PublicSigningKey, master: PublicDHKey) {
		dao.setKeys(contact.id, sign.toString(), master.toString())
	}

	override suspend fun insert(contact: Contact) {
		val dbEntity = ContactDBEntity(
			id = contact.id,
			name = contact.name,
			send = contact.send,
			recv = contact.recv,
			signKey = contact.signKey.toString(),
			masterKey = contact.masterKey.toString(),
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
