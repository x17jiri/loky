package com.x17jiri.Loky

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecvChanState(
	val myPublicKey: PublicDHKey?,
	val theirPublicKey: PublicDHKey?,
	val sharedSecret: SecretKey?,
)

abstract class RecvChanStateStore(
	private val contacts: ContactsStore,
	private val keyStore: PreKeyStore,
	private val states: MutableMap<String, PersistentValue<RecvChanState>> = mutableMapOf(),
) {
	abstract suspend fun load(contactID: String): RecvChanState

	abstract fun launchSave(contactID: String, newState: RecvChanState)

	private val mutex = Mutex()

	fun flow(): Flow<Map<String, RecvChan>> {
		return contacts.flow()
			.map { newContactList ->
				newContactList
					.filter { contact -> contact.recv }
					// sort by id so we can easily compare the list to the old one
					.sortedBy { it.id }
			}
			.distinctUntilChanged { old, new ->
				// if all the ids are the same, we don't need to create new state objects
				old.map { it.id } == new.map { it.id }
			}
			.map { newContactList ->
				mutex.withLock {
					newContactList
						.map { contact ->
							val contactID = contact.id
							val state = states.getOrPut(contactID) {
								val initState = load(contactID)
								PersistentValue(
									initState,
									{ newState -> launchSave(contactID, newState) },
								)
							}
							RecvChan(contactID, keyStore, contact.publicSigningKey, state)
						}
						.associateBy { it.id }
				}
			}
	}
}

class RecvChanStateStoreMock(
	contacts: ContactsStore,
	keyStore: PreKeyStore,
	states: MutableMap<String, PersistentValue<RecvChanState>> = mutableMapOf(),
): RecvChanStateStore(contacts, keyStore, states) {
	override suspend fun load(contactID: String): RecvChanState {
		return RecvChanState(null, null, null)
	}

	override fun launchSave(contactID: String, newState: RecvChanState) {
		// Do nothing
	}
}

@Entity(
    tableName = "RecvChanState",
    primaryKeys = ["contactID"],
)
data class RecvChanStateDBEntity(
	val contactID: String,
	val myPublicKey: String,
	val theirPublicKey: String,
	val sharedSecret: String,
)

@Dao
interface RecvChanStateDao {
	@Query("SELECT * FROM RecvChanState WHERE contactID = :contactID")
	suspend fun load(contactID: String): RecvChanStateDBEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun save(state: RecvChanStateDBEntity)
}

class RecvChanStateDBStore(
	contacts: ContactsStore,
	keyStore: PreKeyStore,
	val dao: RecvChanStateDao,
	val coroutineScope: CoroutineScope,
): RecvChanStateStore(contacts, keyStore) {
	override suspend fun load(contactID: String): RecvChanState {
		val dbEntity = dao.load(contactID)
		if (dbEntity == null) {
			return RecvChanState(null, null, null)
		}
		return RecvChanState(
			myPublicKey = PublicDHKey.fromString(dbEntity.myPublicKey).getOrNull(),
			theirPublicKey = PublicDHKey.fromString(dbEntity.theirPublicKey).getOrNull(),
			sharedSecret = SecretKey.fromString(dbEntity.sharedSecret).getOrNull(),
		)
	}

	override fun launchSave(contactID: String, newState: RecvChanState) {
		coroutineScope.launch {
			dao.save(
				RecvChanStateDBEntity(
					contactID,
					myPublicKey = newState.myPublicKey.toString(),
					theirPublicKey = newState.theirPublicKey.toString(),
					sharedSecret = newState.sharedSecret.toString()
				)
			)
		}
	}
}

class RecvChan(
	val id: String,
	val myKeyStore: PreKeyStore,
	val theirSigningKey: PublicSigningKey,
	val state: PersistentValue<RecvChanState>,
) {
	fun decrypt(msg: ByteArray): Result<String> {
		val sharedSecret = state.value.sharedSecret
		if (sharedSecret == null) {
			return Result.failure(Exception("No shared secret"))
		}

		val bytes = Crypto.decrypt(sharedSecret, msg)
		if (bytes == null) {
			return Result.failure(Exception("Decryption failed"))
		}

		val str = Crypto.byteArrayToStr(bytes)
		return Result.success(str)
	}

	suspend fun switchKeys(
		now: Long,
		myNewPublicKey: PublicDHKey,
		theirNewKey: SignedPublicDHKey,
	) {
		if (!theirNewKey.verifySignature(theirSigningKey)) {
			// TODO - invalid signature - report to user?
			return
		}

		val myKeys = myKeyStore.takeKeyPair(now, myNewPublicKey)
		if (myKeys == null) {
			// We don't have a shared secret anymore
			// TODO - report to user?
			this.state.value = RecvChanState(null, null, null)
			return
		}

		val newSharedSecret = Crypto.deriveSharedKey(myKeys.private, theirNewKey.key)

		this.state.value = RecvChanState(myKeys.public, theirNewKey.key, newSharedSecret)
	}
}

