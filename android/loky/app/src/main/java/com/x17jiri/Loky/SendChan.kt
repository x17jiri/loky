package com.x17jiri.Loky

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class SendChanState(
	val myKeys: DHKeyPair?,
	val theirPublicKey: PublicDHKey?,
	val sharedSecret: SecretKey?,
	val sharedSecretTimestamp: Long,
)

abstract class SendChanStateStore(
	private val contacts: ContactsStore,
	private val states: MutableMap<Long, PersistentValue<SendChanState>> = mutableMapOf(),
) {
	abstract suspend fun load(contactID: Long): SendChanState

	abstract fun launchSave(contactID: Long, state: SendChanState)

	private val mutex = Mutex()

	val flow by lazy {
		contacts.flow.map { newContactList ->
			mutex.withLock {
				newContactList
					.filter { contact -> contact.send }
					.map { contact ->
						val contactID = contact.id
						val state = states.getOrPut(contactID) {
							val initState = load(contactID)
							PersistentValue(initState, { newState -> launchSave(contactID, newState) })
						}
						SendChan(MY_SIGNING_KEYS, contact.publicSigningKey, state)
					}
			}
		}
	}
}

class SendChanStateStoreMock(
	contacts: ContactsStore,
	states: MutableMap<Long, PersistentValue<SendChanState>> = mutableMapOf(),
): SendChanStateStore {
	override suspend fun load(contactID: Long): SendChanState {
		return SendChanState(null, null, null, 0)
	}

	override fun launchSave(contactID: Long, state: SendChanState) {
		// Do nothing
	}
}

@Entity(
	tableName = "SendChanState",
	primaryKeys = ["contactID"],
)
data class SendChanStateDBEntity(
	val contactID: Long,
	val myPublicKey: String,
	val myPrivateKey: String,
	val theirPublicKey: String,
	val sharedSecret: String,
	val sharedSecretTimestamp: Long,
)

@Dao
interface SendChanStateDao {
	@Query("SELECT * FROM SendChanState WHERE contactID = :contactID")
	fun load(contactID: Long): SendChanStateDBEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun save(state: SendChanStateDBEntity)
}

class SendChanStateDBStore(
	val dao: SendChanStateDao,
	val coroutineScope: CoroutineScope,
): SendChanStateStore {
	override suspend fun load(contactID: Long): SendChanState {
		val dbEntity = dao.load(contactID)

		val myPublicKey = PublicDHKey.fromString(dbEntity.myPublicKey).getOrNull()
		val myPrivateKey = PrivateDHKey.fromString(dbEntity.myPrivateKey).getOrNull()
		val myKeys =
			if (myPublicKey != null && myPrivateKey != null) {
				DHKeyPair(myPublicKey, myPrivateKey)
			} else {
				null
			}

		val theirPublicKey = PublicDHKey.fromString(dbEntity.theirPublicKey).getOrNull()
		val sharedSecret = SecretKey.fromString(dbEntity.sharedSecret).getOrNull()

		return SendChanState(
			myKeys,
			theirPublicKey,
			sharedSecret,
			dbEntity.sharedSecretTimestamp,
		)
	}

	override fun launchSave(contactID: Long, state: SendChanState) {
		coroutineScope.launch {
			dao.save(
				SendChanStateDBEntity(
					contactID,
					myPublicKey = state.myKeys?.public?.toString() ?: "",
					myPrivateKey = state.myKeys?.private?.toString() ?: "",
					theirPublicKey = state.theirPublicKey?.toString() ?: "",
					sharedSecret = state.sharedSecret?.toString() ?: "",
					state.sharedSecretTimestamp,
				)
			)
		}
	}
}

class SendChan(
	val mySigningKeys: SigningKeyPair,
	val theirSigningKey: PublicSigningKey,
	val state: PersistentValue<SendChanState>,
) {
	fun needNewKeys(now: Long): Boolean {
		val state = this.state.value
		return state.sharedSecret == null || now - state.sharedSecretTimestamp > 3600
	}

	fun encrypt(msg: String): Result<ByteArray> {
		val sharedSecret = state.sharedSecret
		if (sharedSecret == null) {
			return Result.failure(Exception("No shared secret"))
		} else {
			return Result.success(Crypto.encrypt(sharedSecret, Crypto.strToByteArray(msg)))
		}
	}

	suspend fun switchKeys(
		now: Long,
		theirNewKey: SignedPublicDHKey?,
		onSendKeys: suspend (myNewPublicKey: SignedPublicDHKey, theirNewPublicKey: PublicDHKey) -> Unit,
	) {
		// did we fetch a valid new key from the other side?
		if (theirNewKey != null && theirNewKey.verifySignature(theirSigningKey)) {
			// yes, we did
			val myNewKeys = DHKeyPair.generate()
			onSendKeys(myNewKeys.public.sign(mySigningKeys.private), theirNewKey.key)

			val sharedSecret = Crypto.deriveSharedKey(myNewKeys.private, theirNewKey.key)

			this.state.value = SendChanState(myNewKeys, theirNewKey, sharedSecret, now)
		} else {
			// we couldn't fetch a new key, but will still send a message
			// so the other side knows what key we are using
			// TODO - should we report this to user?
			val theirOldKey = state.theirPublicKey
			val myOldKeys = state.myKeys
			if (theirOldKey != null && myOldKeys != null) {
				onSendKeys(myOldKeys.public.sign(mySigningKeys.private), theirOldKey)
			} else {
				// we couldn't reuse the old key because we don't have one
			}
		}
	}

	suspend fun sendMessage(
		msg: String,
		onFetchKey: suspend () -> SignedPublicDHKey?,
		onSendKeys: suspend (myNewPublicKey: SignedPublicDHKey, theirNewPublicKey: PublicDHKey) -> Unit,
		onSendMsg: suspend (encryptedMsg: ByteArray) -> Unit,
	) {
		val now = monotonicSeconds()
		if (needNewKeys(now)) {
			val theirNewKey = onFetchKey()
			switchKeys(now, theirNewKey, onSendKeys)
		}
		encrypt(msg).onSuccess { onSendMsg(it) }
	}
}

