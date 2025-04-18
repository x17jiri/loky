package com.x17jiri.Loky

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

@Entity(
	tableName = "inbox",
	primaryKeys = ["from", "timestamp"],
)
data class Message(
	val from: String,
	val timestamp: Long,
	val lat: Double,
	val lon: Double,
)

@Dao
interface MessageDao {
	@Query("DELETE FROM inbox WHERE timestamp < :validFrom OR timestamp > :validTo")
	fun cleanUp(validFrom: Long, validTo: Long)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insertAll(messages: List<Message>)

	@Query("SELECT * FROM inbox ORDER BY [from], [timestamp]")
	fun flow(): Flow<List<Message>>
}

class InboxManager(
	val database: AppDatabase,
	val coroutineScope: CoroutineScope,
) {
	companion object {
		val __mutex = Mutex()
	}

	val __messageDao = database.messageDao()

	fun flow(): Flow<List<Message>> {
		return __messageDao.flow()
	}

	fun launchEdit(block: suspend (MessageDao) -> Unit) {
		coroutineScope.launch {
			__mutex.withLock {
				block(__messageDao)
			}
		}
	}

	fun launchCleanUp() {
		coroutineScope.launch {
			val now = monotonicSeconds()
			val validFrom = now - DATA_EXPIRE_SEC
			val validTo = now + 60
			__mutex.withLock {
				__messageDao.cleanUp(validFrom, validTo)
			}
		}
	}
}

data class DataWithHeartbeat(
	val time: Long,
	val ok: Boolean,
	val data: HashMap<String, MutableList<Message>>,
)

class Receiver(
	val server: ServerInterface,
	val inbox: InboxManager,
	val settings: SettingsStore,
	stateStore: RecvChanStateStore,
	val scope: CoroutineScope
) {
	val data =
		inbox.flow()
			.map { messages ->
				val now = monotonicSeconds()
				val cutoff = now - DATA_EXPIRE_SEC
				val newData: HashMap<String, MutableList<Message>> = HashMap()

				for (msg in messages) {
					if (msg.timestamp > cutoff && msg.timestamp <= now) {
						val list = newData.getOrPut(msg.from) { mutableListOf() }
						list.add(msg)
					}
				}

				Pair(now, newData)
			}
			.stateIn(scope, SharingStarted.Eagerly, Pair(0L, HashMap()))
	val heartbeat = MutableStateFlow<Pair<Long, Boolean>>(Pair(0, true))
	val dataWithHeartbeat: StateFlow<DataWithHeartbeat> = data
		.combine(heartbeat) { dt, heartbeat ->
			val time = max(heartbeat.first, dt.first)
			val ok = heartbeat.second || time > heartbeat.first + 10
			val data = dt.second
			DataWithHeartbeat(time, ok, data)
		}
		.stateIn(scope, SharingStarted.Eagerly, DataWithHeartbeat(0L, true, HashMap()))

	val contacts = stateStore.flow().stateIn(scope, SharingStarted.Eagerly, emptyMap())

	val lastErr = MutableStateFlow(Pair(0L, mapOf<String, Long>()))
	val decryptOk = MutableStateFlow(true)

	private var job: Job? = null

	fun start() {
		if (job == null) {
			job = scope.launch {
				val mutableLastErr = lastErr.value.second.toMutableMap()
				var time = 0
				while (isActive) {
					val now = monotonicSeconds()
					// clean up every 10 minutes
					if (abs(now - settings.lastCleanUp.value) >= 10*60) {
						settings.launchEdit { dao ->
							dao.setLastCleanUp(now)
						}
						inbox.launchCleanUp()
					} else {
						Log.d("Locodile", "recv BEFORE **")
						server.recv(contacts.value, mutableLastErr).fold(
							onSuccess = { (list, needPrekeys) ->
								Log.d("Locodile", "recv onSuccess")
								if (needPrekeys.value) {
									server.addPreKeys()
								}
								if (list.isNotEmpty()) {
									inbox.launchEdit { dao ->
										dao.insertAll(list)
									}
								} else {
									heartbeat.value = Pair(monotonicSeconds(), true)
								}
							},
							onFailure = { e ->
								heartbeat.value = Pair(monotonicSeconds(), false)
								Log.d("Locodile", "recv onFailure")
								Log.d("Locodile", "Receiver: e=$e")
								Log.d("Locodile", "Receiver: e=${e.stackTraceToString()}")
							}
						)
					}
					val lastErrMap = mutableLastErr.toMap()
					lastErr.value = Pair(now, lastErrMap)
					decryptOk.value = lastErrMap.all { it.value == 0L }
					delay(4_000)
					time += 4
				}
			}
		}
	}

	fun stop() {
		job?.cancel()
		job = null
	}
}
