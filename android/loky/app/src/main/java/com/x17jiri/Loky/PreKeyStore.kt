package com.x17jiri.Loky

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PreKeyStore {
	suspend fun takeKeyPair(now: Long, publicKey: PublicDHKey): DHKeyPair?

	// The new keys are stored in the store only if sendToServer returns success
	suspend fun generate(sendToServer: suspend (List<PublicDHKey>) -> Result<Unit>)

	suspend fun markLive(liveKeys: List<PublicDHKey>)
}

class PreKeyStoreMock(
	val coroutineScope: CoroutineScope = GlobalScope,
): PreKeyStore {
	private val keys = mutableMapOf<String, DHKeyPair>()

	suspend override fun takeKeyPair(now: Long, publicKey: PublicDHKey): DHKeyPair? {
		val strKey = publicKey.toString()
		return keys[strKey] ?: return null
	}

	override suspend fun generate(sendToServer: suspend (List<PublicDHKey>) -> Result<Unit>) {
		val newKeys = (0 until 10).map { DHKeyPair.generate() }
		sendToServer(newKeys.map { it.public }).onSuccess {
			keys.putAll(newKeys.associateBy { it.public.toString() })
		}
	}

	override suspend fun markLive(liveKeys: List<PublicDHKey>) {}
}

@Entity(
	tableName = "PreKeyStore",
	primaryKeys = ["publicKey"],
)
data class PreKeyDBEntity(
	val publicKey: String,
	val privateKey: String,
	val usedTime: Long?,
)

@Dao
interface PreKeyDao {
	@Query("SELECT * FROM PreKeyStore")
	fun flow(): Flow<List<PreKeyDBEntity>>

	@Query("DELETE FROM PreKeyStore WHERE usedTime IS NOT NULL AND usedTime < :validFrom")
	suspend fun delExpired(validFrom: Long): Int

	@Query("UPDATE PreKeyStore SET usedTime = :now WHERE publicKey = :publicKey AND usedTime IS NULL")
	suspend fun markUsed(publicKey: String, now: Long): Unit

	@Query("UPDATE PreKeyStore SET usedTime = :now WHERE publicKey NOT IN (:liveKeys) AND usedTime IS NULL")
	suspend fun markLive(liveKeys: List<String>, now: Long): Unit

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(what: List<PreKeyDBEntity>): Unit

	@Query("DELETE FROM PreKeyStore WHERE publicKey = :publicKey")
	suspend fun delete(publicKey: String): Unit

	@Query("DELETE FROM PreKeyStore WHERE publicKey IN (:publicKeys)")
	suspend fun delete(publicKeys: List<String>): Unit
}

data class PreKey(
	val keyPair: DHKeyPair,
	val usedTime: Long? = null,
)

class PreKeyDBStore(
	val dao: PreKeyDao,
	val coroutineScope: CoroutineScope,
): PreKeyStore {
	private val keys = mutableMapOf<String, PreKey>()
	private val keysMutex = Mutex()

	suspend fun init() {
		keysMutex.withLock {
			keys.clear()
			dao.loadAll().forEach { entity ->
				val publicKey = PublicDHKey.fromString(entity.publicKey).getOrNull()
				val privateKey = PrivateDHKey.fromString(entity.privateKey).getOrNull()
				if (publicKey != null && privateKey != null) {
					keys[entity.publicKey] = PreKey(
						DHKeyPair(publicKey, privateKey),
						entity.usedTime,
					)
				}
			}
		}
	}

	override suspend fun takeKeyPair(now: Long, publicKey: PublicDHKey): DHKeyPair? {
		val strKey = publicKey.toString()
		val data =
			keysMutex.withLock {
				keys.compute(strKey) { _, value ->
					if (value != null && value.usedTime == null) {
						value.copy(usedTime = now)
					} else {
						value
					}
				}
			}
		if (data == null) {
			return null
		}
		launchEdit {
			dao.markUsed(strKey, now)
			// TODO - should run dao.delExpired()?
		}
		return data.keyPair
	}

	override suspend fun generate(sendToServer: suspend (List<PublicDHKey>) -> Result<Unit>) {
		val newPairs = (0 until 10).map { PreKey(DHKeyPair.generate()) }
		val newList = newPairs.map { it.keyPair.public }
		val newMap = newPairs.associateBy { it.keyPair.public.toString() }
		sendToServer(newList).onSuccess {
			keysMutex.withLock { keys.putAll(newMap) }

			val dbEntities = newPairs.map { data ->
				PreKeyDBEntity(
					data.keyPair.public.toString(),
					data.keyPair.private.toString(),
					data.usedTime,
				)
			}
			launchEdit {
				dao.insert(dbEntities)
			}
		}
	}

	override suspend fun markLive(liveKeys: List<PublicDHKey>) {
		val strKeys = liveKeys.map { it.toString() }.toSet()
		val now = monotonicSeconds()
		val validFrom = now - KEY_EXPIRE_SEC
		keysMutex.withLock {
			keys.replaceAll { key, data ->
				if (key in strKeys || data.usedTime != null) {
					data
				} else {
					data.copy(usedTime = now)
				}
			}
			keys.entries.removeIf { (_, data) -> data.usedTime != null && data.usedTime < validFrom }
		}
		launchEdit {
			dao.markLive(strKeys.toList(), now)
			dao.delExpired(validFrom)
		}
	}

	fun launchEdit(block: suspend (PreKeyStore) -> Unit) {
		coroutineScope.launch {
			block(this@PreKeyDBStore)
		}
	}
}

