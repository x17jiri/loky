package com.x17jiri.Loky

import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SendChanState(
	val myKeys: DHKeyPair?,
	val theirPublicKey: PublicDHKey?,
	val sharedSecret: SecretKey?,

	// The last time we switched keys
	val keySwitchTime: Long,
)

abstract class SendChanStateStore(
	private val profile: ProfileStore,
	private val contacts: ContactsStore,
	private val states: MutableMap<String, PersistentValue<SendChanState>> = mutableMapOf(),
) {
	abstract suspend fun load(contactID: String): SendChanState

	abstract fun launchSave(contactID: String, state: SendChanState)

	private val mutex = Mutex()

	fun flow(): Flow<List<SendChan>> {
		return contacts.flow()
			.map { newContactList ->
				Log.d("Locodile **********", "map 111: newContactList: $newContactList")
				newContactList
					.filter { contact -> contact.send }
					// sort by id so we can easily compare the list to the old one
					.sortedBy { it.id }
			}
			.distinctUntilChanged { old, new ->
				Log.d("Locodile **********", "old: $old")
				Log.d("Locodile **********", "new: $new")
				Log.d("Locodile **********", "same: ${old.map { it.id } == new.map { it.id }}")
				// if all the ids are the same, we don't need to create new state objects
				old.map { it.id } == new.map { it.id }
			}
			.map { newContactList ->
				Log.d("Locodile **********", "map 222: newContactList: $newContactList")
				val result = mutex.withLock {
					val mySigningKeys = profile.signingKeys.value.keyPair
					if (mySigningKeys == null) {
						return@withLock emptyList()
					}
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
							SendChan(contactID, mySigningKeys, contact.publicSigningKey, state)
						}
				}
				Log.d("Locodile **********", "map 333: result: $result")
				result
			}
	}
}

class SendChanStateStoreMock(
	profile: ProfileStore,
	contacts: ContactsStore,
	states: MutableMap<String, PersistentValue<SendChanState>> = mutableMapOf(),
): SendChanStateStore(profile, contacts, states) {
	override suspend fun load(contactID: String): SendChanState {
		return SendChanState(null, null, null, 0)
	}

	override fun launchSave(contactID: String, state: SendChanState) {
		// Do nothing
	}
}

@Entity(
	tableName = "SendChanState",
	primaryKeys = ["contactID"],
)
data class SendChanStateDBEntity(
	val contactID: String,
	val myPublicKey: String,
	val myPrivateKey: String,
	val theirPublicKey: String,
	val sharedSecret: String,
	val keySwitchTime: Long,
)

@Dao
interface SendChanStateDao {
	@Query("SELECT * FROM SendChanState WHERE contactID = :contactID")
	suspend fun load(contactID: String): SendChanStateDBEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun save(state: SendChanStateDBEntity)
}

class SendChanStateDBStore(
	profile: ProfileStore,
	contacts: ContactsStore,
	val dao: SendChanStateDao,
	val coroutineScope: CoroutineScope,
): SendChanStateStore(profile, contacts) {
	override suspend fun load(contactID: String): SendChanState {
		val dbEntity = dao.load(contactID)
		if (dbEntity == null) {
			return SendChanState(null, null, null, 0)
		}

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
			dbEntity.keySwitchTime,
		)
	}

	override fun launchSave(contactID: String, state: SendChanState) {
		coroutineScope.launch {
			dao.save(
				SendChanStateDBEntity(
					contactID,
					myPublicKey = state.myKeys?.public?.toString() ?: "",
					myPrivateKey = state.myKeys?.private?.toString() ?: "",
					theirPublicKey = state.theirPublicKey?.toString() ?: "",
					sharedSecret = state.sharedSecret?.toString() ?: "",
					keySwitchTime = state.keySwitchTime,
				)
			)
		}
	}
}

data class SendChanUsedKeys(
	val myKey: SignedPublicDHKey,
	val theirKey: PublicDHKey,
)

class SendChan(
	val id: String,
	val mySigningKeys: SigningKeyPair,
	val theirSigningKey: PublicSigningKey,
	val state: PersistentValue<SendChanState>,
) {
	fun encrypt(msg: String): Result<ByteArray> {
		val state = this.state.value
		val sharedSecret = state.sharedSecret
		if (sharedSecret == null) {
			return Result.failure(Exception("No shared secret"))
		} else {
			return Result.success(Crypto.encrypt(sharedSecret, Crypto.strToByteArray(msg)))
		}
	}

	suspend fun usedKeys(): SendChanUsedKeys? {
		val state = this.state.value
		val myKeys = state.myKeys
		val theirPublicKey = state.theirPublicKey
		if (myKeys == null || theirPublicKey == null) {
			return null
		}
		return SendChanUsedKeys(myKeys.public.signBy(mySigningKeys.private), theirPublicKey)
	}

	fun shouldChangeKeys(now: Long): Boolean {
		val state = this.state.value
		Log.d("Locodile **********", "shouldChangeKeys: state: $state")
		// In this range, our key is considered fresh, i.e., we don't try to change it
		val freshFrom = state.keySwitchTime
		val freshTo = freshFrom + KEY_SWITCH_SEC
		return state.sharedSecret == null || now !in freshFrom until freshTo
	}

	suspend fun changeKeys(now: Long, theirNewKey: SignedPublicDHKey?) {
		Log.d("Locodile **********", "changeKeys: theirNewKey: $theirNewKey")
		if (theirNewKey != null && theirNewKey.verifySignature(theirSigningKey)) {
			// We managed to fetch a valid new key from the other side
			val myNewKeys = DHKeyPair.generate()
			val sharedSecret = Crypto.deriveSharedKey(myNewKeys.private, theirNewKey.key)
			Log.d("Locodile **********", "changeKeys: myNewKeys: $myNewKeys")
			Log.d("Locodile **********", "changeKeys: sharedSecret: $sharedSecret")
			this.state.value =
				SendChanState(
					myKeys = myNewKeys,
					theirPublicKey = theirNewKey.key,
					sharedSecret = sharedSecret,
					keySwitchTime = now,
				)
		} else {
			// We couldn't fetch a new key from the other side
			// Use the old one unless it's expired
			val state = this.state.value
			// In this range, our key is considered valid, i.e., we can encrypt messages
			val validFrom = state.keySwitchTime
			// subtract `KEY_SWITCH_SEC` so the key remains valid until next switch
			val validTo = validFrom + KEY_EXPIRE_SEC - KEY_SWITCH_SEC
			if (now !in validFrom until validTo) {
				// our keys are expired
				this.state.value = SendChanState(null, null, null, 0)
			}
		}
	}
}

