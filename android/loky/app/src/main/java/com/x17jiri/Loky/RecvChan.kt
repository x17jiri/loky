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
	val sharedSecret: SecretKey?,
)

abstract class RevChanStateStore(
	private val states: MutableMap<Long, StoredValue<RecvChanState>> = mutableMapOf()
) {
	abstract suspend fun load(contactID: Long): RecvChanState

	abstract fun save(contactID: Long, newState: RecvChanState)

	private val mutex = Mutex()

	fun flow(
		contacts: ContactsStore,
		keyStore: KeyStore,
	): Flow<List<RecvChan>> {
		return contacts.flow().map { newContactList ->
			mutex.withLock {
				newContactList
					.filter { contact -> contact.recv }
					.map { contact ->
						val contactID = contact.id
						val state = states.getOrPut(contactID) {
							val initState = load(contactID)
							StoredValue(initState, { newState -> save(contactID, newState) })
						}
						RecvChan(contactID, contact.publicSigningKey, state, keyStore)
					}
			}
		}
	}
}

class RecvChanStateStoreMock(
	states: MutableMap<Long, StoredValue<RecvChanState>> = mutableMapOf()
): RevChanStateStore(states) {
	override suspend fun load(contactID: Long): RecvChanState {
		return RecvChanState(null)
	}

	override fun save(contactID: Long, newState: RecvChanState) {
		// Do nothing
	}
}

@Entity(
    tableName = "RecvChanState",
    primaryKeys = ["contactID"],
)
data class RecvChanStateDBEntity(
	val contactID: Long,
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
): RevChanStateStore() {
	override suspend fun load(contactID: Long): RecvChanState {
		val dbEntity = dao.load(contactID)
		val sharedSecret = SecretKey.fromString(dbEntity?.sharedSecret ?: "").getOrNull()
		return RecvChanState(sharedSecret)
	}

	override fun save(contactID: Long, newState: RecvChanState) {
		coroutineScope.launch {
			dao.save(
				RecvChanStateDBEntity(
					contactID,
					newState.sharedSecret.toString()
				)
			)
		}
	}
}

class RecvChan(
	val contact_id: Long,
	val theirSigningKey: PublicSigningKey,
	val state: StoredValue<RecvChanState>,
	val keyStore: KeyStore,
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

	fun recvKeys(
		theirNewPublicKey: PublicDHKey,
		theirSignature: Signature,
		myNewPublicKey: PublicDHKey
	) {
		if (!Crypto.verifySignature(theirNewPublicKey.encoded, theirSignature, theirSigningKey)) {
			// TODO - invalid signature - report to user?
			return
		}

		// The return could happen if the sender couldn't fetch our key
		// so they are reusing an old one and it's already been taken from the keyStore
		val myKeys = keyStore.takeKeyPair(myNewPublicKey) ?: return

		val newSharedSecret =
			SecretKey.deriveFromSecret(
				Crypto.diffieHellman(myKeys.private, theirNewPublicKey)
			)

		this.state.value = RecvChanState(newSharedSecret)
	}
}

