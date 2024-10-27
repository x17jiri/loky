package com.x17jiri.Loky

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecvChanState(
	val myPublicKey: PublicDHKey?,
	val theirPublicKey: PublicDHKey?,
	val sharedSecret: SecretKey?,
)

abstract class RevChanStateStore(
	private val contacts: ContactsStore,
	private val keyStore: PreKeyStore,
	private val states: MutableMap<Long, PersistentValue<RecvChanState>> = mutableMapOf(),
) {
	abstract suspend fun load(contactID: Long): RecvChanState

	abstract fun launchSave(contactID: Long, newState: RecvChanState)

	private val mutex = Mutex()

	fun flow(): Flow<List<RecvChan>> {
		return contacts.flow().map { newContactList ->
			mutex.withLock {
				newContactList
					.filter { contact -> contact.recv }
					.map { contact ->
						val contactID = contact.id
						val state = states.getOrPut(contactID) {
							val initState = load(contactID)
							PersistentValue(initState, { newState -> launchSave(contactID, newState) })
						}
						RecvChan(keyStore, contact.publicSigningKey, state)
					}
			}
		}
	}
}

class RecvChanStateStoreMock(
	contacts: ContactsStore,
	keyStore: PreKeyStore,
	states: MutableMap<Long, PersistentValue<RecvChanState>> = mutableMapOf(),
): RevChanStateStore(contacts, keyStore, states) {
	override suspend fun load(contactID: Long): RecvChanState {
		return RecvChanState(null, null, null)
	}

	override fun launchSave(contactID: Long, newState: RecvChanState) {
		// Do nothing
	}
}

@Entity(
    tableName = "RecvChanState",
    primaryKeys = ["contactID"],
)
data class RecvChanStateDBEntity(
	val contactID: Long,
	val myPublicKey: String,
	val theirPublicKey: String,
	val sharedSecret: String,
)

@Dao
interface RecvChanStateDao {
	@Query("SELECT * FROM RecvChanState WHERE contactID = :contactID")
	suspend fun load(contactID: Long): RecvChanStateDBEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun save(state: RecvChanStateDBEntity)
}

class RecvChanStateDBStore(
	val dao: RecvChanStateDao,
	val coroutineScope: CoroutineScope,
	contacts: ContactsStore,
	keyStore: PreKeyStore,
): RevChanStateStore(contacts, keyStore) {
	override suspend fun load(contactID: Long): RecvChanState {
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

	override fun launchSave(contactID: Long, newState: RecvChanState) {
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
		theirNewKey: SignedPublicDHKey,
		myNewPublicKey: PublicDHKey,
	) {
		if (!theirNewKey.verifySignature(theirSigningKey)) {
			// TODO - invalid signature - report to user?
			return
		}

		val myKeys = myKeyStore.takeKeyPair(myNewPublicKey)
		if (myKeys == null) {
			// This could happen if the sender couldn't fetch our key
			// so they are reusing an old one and it's already been taken from the key store
			val state = this.state.value
			if (myNewPublicKey != state.myPublicKey || theirNewKey.key != state.theirPublicKey) {
				// The RechChan got out of sync somehow with the SendChan of the contact
				// We don't have a shared secret anymore
				// TODO - report to user?
				this.state.value = RecvChanState(null, null, null)
			}
			return
		}

		val newSharedSecret = Crypto.deriveSharedKey(myKeys.private, theirNewKey.key)

		this.state.value = RecvChanState(myKeys.public, theirNewKey.key, newSharedSecret)
	}
}

