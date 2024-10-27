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
	suspend fun takeKeyPair(publicKey: PublicDHKey): DHKeyPair?

	suspend fun generate(sendToServer: suspend (List<PublicDHKey>) -> Result<Unit>)

	suspend fun markUsed(keys: List<PublicDHKey>)

	suspend fun delExpired(expirationDays: Long = 30)

	fun launchEdit(block: suspend (PreKeyStore) -> Unit)
}

class PreKeyStoreMock(
	val coroutineScope: CoroutineScope = GlobalScope,
): PreKeyStore {
	private val keys = mutableMapOf<String, DHKeyPair>()

	suspend override fun takeKeyPair(publicKey: PublicDHKey): DHKeyPair? {
		val strKey = publicKey.toString()
		return keys[strKey] ?: return null
	}

	override suspend fun generate(sendToServer: suspend (List<PublicDHKey>) -> Result<Unit>) {
		val newKeys = (0 until 10).map { DHKeyPair.generate() }
		sendToServer(newKeys.map { it.public }).onSuccess {
			keys.putAll(newKeys.associateBy { it.public.toString() })
		}
	}

	override suspend fun markUsed(keys: List<PublicDHKey>) {}

	override suspend fun delExpired(expirationDays: Long) {}

	override fun launchEdit(block: suspend (PreKeyStore) -> Unit) {
		coroutineScope.launch {
			block(this@PreKeyStoreMock)
		}
	}
}

@Entity(
	tableName = "PreKeyStore",
	primaryKeys = ["publicKey"],
)
data class PreKeyPairDBEntity(
	val publicKey: String,
	val privateKey: String,
	val used: Boolean,
	val usedTime: Long,
)

@Dao
interface PreKeyPairDao {
	@Query("SELECT * FROM PreKeyStore")
	suspend fun loadAll(): List<PreKeyPairDBEntity>

	@Query("DELETE FROM PreKeyStore WHERE used = 1 AND usedTime < :validFrom")
	suspend fun delExpired(validFrom: Long): Int

	@Query("UPDATE PreKeyStore SET used = 1, usedTime = :usedTime WHERE publicKey IN (:publicKeys)")
	suspend fun markUsed(publicKeys: List<String>, usedTime: Long): Unit

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(what: List<PreKeyPairDBEntity>): Unit

	@Query("DELETE FROM PreKeyStore WHERE publicKey = :publicKey")
	suspend fun delete(publicKey: String): Unit

	@Query("DELETE FROM PreKeyStore WHERE publicKey IN (:publicKeys)")
	suspend fun delete(publicKeys: List<String>): Unit
}

data class PreKeyPair(
	val keyPair: DHKeyPair,
	val used: Boolean = false,
	val usedTime: Long = 0,
)

class PreKeyDBStore(
	val dao: PreKeyPairDao,
	val coroutineScope: CoroutineScope,
): PreKeyStore {
	private val keys = mutableMapOf<String, PreKeyPair>()
	private val keysMutex = Mutex()

	suspend fun init() {
		keysMutex.withLock {
			keys.clear()
			dao.loadAll().forEach { entity ->
				val publicKey = PublicDHKey.fromString(entity.publicKey).getOrNull()
				val privateKey = PrivateDHKey.fromString(entity.privateKey).getOrNull()
				if (publicKey != null && privateKey != null) {
					keys[entity.publicKey] = PreKeyPair(
						DHKeyPair(publicKey, privateKey),
						entity.used,
						entity.usedTime,
					)
				}
			}
		}
	}

	override suspend fun takeKeyPair(publicKey: PublicDHKey): DHKeyPair? {
		val strKey = publicKey.toString()
		val data = keysMutex.withLock { keys.remove(strKey) }
		if (data != null) {
			launchEdit {
				dao.delete(strKey)
			}
		}
		return data?.keyPair
	}

	override suspend fun generate(sendToServer: suspend (List<PublicDHKey>) -> Result<Unit>) {
		val newPairs = (0 until 10).map { PreKeyPair(DHKeyPair.generate()) }
		val newList = newPairs.map { it.keyPair.public }
		val newMap = newPairs.associateBy { it.keyPair.public.toString() }
		sendToServer(newList).onSuccess {
			keysMutex.withLock { keys.putAll(newMap) }

			val dbEntities = newPairs.map { data ->
				PreKeyPairDBEntity(
					data.keyPair.public.toString(),
					data.keyPair.private.toString(),
					data.used,
					data.usedTime,
				)
			}
			launchEdit {
				dao.insert(dbEntities)
			}
		}
	}

	override suspend fun markUsed(usedKeys: List<PublicDHKey>) {
		val strKeys = usedKeys.map { it.toString() }.toSet()
		val now = monotonicSeconds()
		keysMutex.withLock {
			keys.replaceAll { key, data ->
				if (key in strKeys) {
					data.copy(used = true, usedTime = now)
				} else {
					data
				}
			}
		}
		launchEdit {
			dao.markUsed(strKeys.toList(), now)
		}
	}

	override suspend fun delExpired(expirationDays: Long) {
		val now = monotonicSeconds()
		val validFrom = now - expirationDays * 86400
		keysMutex.withLock {
			keys.entries.removeIf { (_, data) ->
				data.used && data.usedTime < validFrom
			}
		}
		launchEdit {
			dao.delExpired(validFrom)
		}
	}

	override fun launchEdit(block: suspend (PreKeyStore) -> Unit) {
		coroutineScope.launch {
			block(this@PreKeyDBStore)
		}
	}
}

