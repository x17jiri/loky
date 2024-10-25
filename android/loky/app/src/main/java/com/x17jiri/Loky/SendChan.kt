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

interface SendChanStateStore {
	suspend fun load(contactID: Long): SendChanState?
	fun launchSave(contactID: Long, state: SendChanState)
}

class SendChanStateStoreMock(
	val states: MutableMap<Long, SendChanState> = mutableMapOf<Long, SendChanState>()
): SendChanStateStore {

	override suspend fun load(contactID: Long): SendChanState? {
		return states[contactID]
	}

	override fun launchSave(contactID: Long, state: SendChanState) {
		states[contactID] = state
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

	override suspend fun load(contactID: Long): SendChanState? {
		val dbEntity = dao.load(contactID) ?: return null

		val myPublicKey = PublicDHKey.fromString(dbEntity.myPublicKey).getOrNull()
		val myPrivateKey = PrivateDHKey.fromString(dbEntity.myPrivateKey).getOrNull()
		val myKeys = if (myPublicKey != null && myPrivateKey != null) {
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
		val myPublicKey = state.myKeys?.public?.toString() ?: ""
		val myPrivateKey = state.myKeys?.private?.toString() ?: ""
		val theirPublicKey = state.theirPublicKey?.toString() ?: ""
		val sharedSecret = state.sharedSecret?.toString() ?: ""

		val dbEntity = SendChanStateDBEntity(
			contactID,
			myPublicKey,
			myPrivateKey,
			theirPublicKey,
			sharedSecret,
			state.sharedSecretTimestamp,
		)

		coroutineScope.launch {
			dao.save(dbEntity)
		}
	}
}

class SendChan(
	val contact_id: Long,
	val theirSigningKey: PublicSigningKey,
	val state: StateFlow<SendChanState>,
	val mySigningKeys: SigningKeyPair,
) {
	fun needNewSecret(now: Long): Boolean {
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

	suspend fun updateKey(
		now: Long,
		theirNewKeyWithSignature: Pair<PublicDHKey, Signature>?,
		onSendKeys: suspend (
			myNewPublicKey: PublicDHKey,
			signiture: Signature,
			theirNewPublicKey: PublicDHKey
		) -> Unit,
	) {
		val haveNewKey =
			if (theirNewKeyWithSignature != null) {
				val theirNewKey = theirNewKeyWithSignature.first
				val signature = theirNewKeyWithSignature.second
				val ok = Crypto.verifySignature(theirNewKey.encoded, signature, theirSigningKey)
				// TODO - invalid signature - report to user?
				ok
			} else {
				false
			}
		if (haveNewKey) {
			// we managed to fetch a new key
			val theirPublicKey = theirNewKeyWithSignature!!.first
			val myKeys = DHKeyPair.generate()
			val mySignature = Crypto.sign(myKeys.public.encoded, mySigningKeys.private)

			onSendKeys(myKeys.public, mySignature, theirPublicKey)

			val sharedSecret = SecretKey.deriveFromSecret(
				Crypto.diffieHellman(myKeys.private, theirPublicKey)
			)
			val timestamp = now

			val newState = SendChanState(myKeys, theirPublicKey, sharedSecret, timestamp)
			this.state = newState
			this.stateStore.launchSave(contact.id, newState)
		} else {
			// we couldn't fetch a new key, but will still send a message
			// so the other side knows what key we are using
			// TODO - should we report this to user?
			val theirPublicKey = state.theirPublicKey
			val myKeys = state.myKeys
			if (theirPublicKey != null && myKeys != null) {
				val mySignature = Crypto.sign(myKeys.public.encoded, mySigningKeys.private)

				onSendKeys(myKeys.public, mySignature, theirPublicKey)
			} else {
				// we couldn't reuse the old key because we don't have one
			}
		}
	}

	suspend fun sendMessage(
		msg: String,
		onFetchKey: suspend () -> Pair<PublicDHKey, Signature>?,
		onSendKeys: suspend (
			myNewPublicKey: PublicDHKey,
			mySigniture: Signature,
			theirNewPublicKey: PublicDHKey
		) -> Unit,
		onSendMsg: suspend (ByteArray) -> Unit,
	) {
		val now = monotonicSeconds()
		if (needNewSecret(now)) {
			val theirNewKey = onFetchKey()
			updateKey(now, theirNewKey, onSendKeys)
		}
		val encryptedMsg = encrypt(msg)
		encryptedMsg.onSuccess {
			onSendMsg(it)
		}
	}
}

