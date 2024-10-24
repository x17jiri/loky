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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

@Entity(
	tableName = "inbox",
	primaryKeys = ["from", "timestamp"],
)
data class Message(
	val from: Long,
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
			val validFrom = now - 7200
			val validTo = now + 60
			__mutex.withLock {
				__messageDao.cleanUp(validFrom, validTo)
			}
		}
	}
}

class Receiver(
	val server: ServerInterface,
	val inboxMan: InboxManager,
	val scope: CoroutineScope
) {
	val data =
		inboxMan.flow().map { messages ->
			val now = monotonicSeconds()
			val cutoff = now - 7200
			val newData: HashMap<Long, MutableList<Message>> = HashMap()

			for (msg in messages) {
				if (msg.timestamp > cutoff && msg.timestamp <= now) {
					val list = newData.getOrPut(msg.from) { mutableListOf() }
					list.add(msg)
				}
			}

			newData
		}.stateIn(scope, SharingStarted.Eagerly, HashMap())

	private var job: Job? = null

	fun start() {
		if (job == null) {
			job = scope.launch {
				var time = 0
				while (isActive) {
					// clean up every 10 minutes
					if (time < 10*60) {
						server.recv().fold(
							onSuccess = { list ->
								if (list.isNotEmpty()) {
									inboxMan.launchEdit { dao ->
										dao.insertAll(list)
									}
								}
							},
							onFailure = {
								Log.d("Locodile", "Receiver: e=$it")
							}
						)
					} else {
						time = 0
						inboxMan.launchCleanUp()
					}
					delay(7_000)
					time += 7
				}
			}
		}
	}

	fun stop() {
		job?.cancel()
		job = null
	}
}
