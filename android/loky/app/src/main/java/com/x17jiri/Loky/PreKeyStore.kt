package com.x17jiri.Loky

import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PreKeyStore {
	suspend fun takeKeyPair(now: Long, publicKey: PublicDHKey): DHKeyPair?

	// The new keys are stored in the store only if sendToServer returns success
	suspend fun generate(
		sendToServer: suspend (List<PublicDHKey>) -> Result<List<String>>,
	)

	suspend fun init()
}

class PreKeyStoreMock(
	val coroutineScope: CoroutineScope = GlobalScope,
): PreKeyStore {
	private val keys = mutableMapOf<String, DHKeyPair>()

	suspend override fun takeKeyPair(now: Long, publicKey: PublicDHKey): DHKeyPair? {
		val strKey = publicKey.toString()
		return keys[strKey] ?: return null
	}

	override suspend fun generate(
		sendToServer: suspend (List<PublicDHKey>) -> Result<List<String>>,
	) {
		val newKeys = (0 until 10).map { DHKeyPair.generate() }
		sendToServer(newKeys.map { it.public }).onSuccess {
			keys.putAll(newKeys.associateBy { it.public.toString() })
		}
	}

	override suspend fun init() {}
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
	suspend fun loadAll(): List<PreKeyDBEntity>

	@Query("DELETE FROM PreKeyStore WHERE usedTime IS NOT NULL AND usedTime < :validFrom")
	suspend fun delExpired(validFrom: Long): Int

	@Query("UPDATE PreKeyStore SET usedTime = :now WHERE publicKey = :publicKey AND usedTime IS NULL")
	suspend fun markUsed(now: Long, publicKey: String): Unit

	@Query("UPDATE PreKeyStore SET usedTime = :now WHERE publicKey IN (:publicKeys) AND usedTime IS NULL")
	suspend fun markUsed(now: Long, publicKeys: List<String>)

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
	private val genMutex = Mutex()
	var __initialized = false

	override suspend fun init() {
		genMutex.withLock {
			keysMutex.withLock {
				Log.d("Locodile !!!!!!!!!!!!!!!!!!!!!!!", "PreKeyDBStore.init: __initialized=$__initialized")
				if (__initialized) {
					return
				}
				__initialized = true

				Log.d("Locodile !!!!!!!!!!!!!!!!!!!!!!!!1", "PreKeyDBStore.init")
				keys.clear()
				dao.loadAll().forEach { entity ->
					Log.d("Locodile !!!!!!!!!!!!!!!!!!!!!!!!1", "PreKeyDBStore.init: entity=$entity")
					val publicKey = PublicDHKey.fromString(entity.publicKey).getOrNull()
					val privateKey = PrivateDHKey.fromString(entity.privateKey).getOrNull()
					if (publicKey != null && privateKey != null) {
						keys[entity.publicKey] = PreKey(
							DHKeyPair(publicKey, privateKey),
							entity.usedTime,
						)
					}
				}

				for ((key, data) in keys) {
					Log.d("Locodile", "PreKeyDBStore.init: key=$key, data=$data")
				}
			}
		}
	}

	override suspend fun takeKeyPair(now: Long, publicKey: PublicDHKey): DHKeyPair? {
		val strKey = publicKey.toString()
		Log.d("Locodile", "PreKeyDBStore.takeKeyPair: strKey=$strKey")
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
		Log.d("Locodile", "PreKeyDBStore.takeKeyPair: data=$data")
		if (data == null) {
			Log.d("Locodile", "PreKeyDBStore.takeKeyPair: data is null")
			return null
		}
		launchEdit {
			dao.markUsed(now, strKey)
			// TODO - should run dao.delExpired()?
		}
		return data.keyPair
	}

	override suspend fun generate(
		sendToServer: suspend (List<PublicDHKey>) -> Result<List<String>>,
	) {
		if (!genMutex.tryLock()) {
			return
		}
		try {
			val newList = (0 until 10).map { PreKey(DHKeyPair.generate()) }

			val newMap = newList.associateBy { it.keyPair.public.toString() }
			keysMutex.withLock {
				keys.putAll(newMap)
			}

			sendToServer(newList.map { it.keyPair.public }).onSuccess { livePrekeys ->
				Log.d("Locodile !!!!!!!!!!!!!!!!!!!!!!!!1", "PreKeyDBStore.generate. onSuccess")

				val liveKeys = livePrekeys.mapNotNull { keySig ->
					val parts = keySig.split(",")
					if (parts.size != 2) {
						return@mapNotNull null
					}
					parts[0]
				}

				val now = monotonicSeconds()
				val validFrom = now - KEY_EXPIRE_SEC

				val deadKeys: Map<String, PreKey>
				keysMutex.withLock {
					deadKeys = keys - liveKeys

					keys.replaceAll { key, data ->
						if (key in deadKeys && data.usedTime == null) {
							data.copy(usedTime = now)
						} else {
							data
						}
					}

					keys.entries.removeIf {
						(_, data) -> data.usedTime != null && data.usedTime < validFrom
					}
				}

				Log.d("Locodile !!!!!!!!!!!!!!!!!!!!!!!!1", "PreKeyDBStore.generate. deadKeys=$deadKeys")

				val newEntities = newList.map { data ->
					PreKeyDBEntity(
						data.keyPair.public.toString(),
						data.keyPair.private.toString(),
						data.usedTime,
					)
				}
				launchEdit {
					dao.insert(newEntities)
					dao.markUsed(now, deadKeys.keys.toList())
					dao.delExpired(validFrom)
				}
			}
		} finally {
			genMutex.unlock()
		}
	}

	fun launchEdit(block: suspend (PreKeyStore) -> Unit) {
		coroutineScope.launch {
			block(this@PreKeyDBStore)
		}
	}
}

