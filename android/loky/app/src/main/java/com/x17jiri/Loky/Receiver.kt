package com.x17jiri.Loky

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

data class Message(
	val from: Long,
	val lat: Double,
	val lon: Double,
	val timestamp: Instant,
)

data class DataPoint(val lat: Double, val lon: Double, val timestamp: Instant)

class Receiver(model: MainViewModel) {
	val model = model
	val server = model.server
	var __data = MutableStateFlow(HashMap<Long, MutableList<DataPoint>>())
	val data: StateFlow<HashMap<Long, MutableList<DataPoint>>> = __data
	private var job: Job? = null

	suspend fun receive() {
		__data.update { oldData ->
			var newData: HashMap<Long, MutableList<DataPoint>> = HashMap()

			// filter out overything older than 2 hours
			val now = Instant.now()
			val cutoff = now.minusSeconds(7200)

			for ((k, v) in oldData) {
				val filtered = v.filter { it.timestamp.isAfter(cutoff) }
				newData[k] = filtered.toMutableList()
			}

			val cred = model.credMan.credentials.value
			server.recv(cred) { msg ->
				Log.d("Locodile", "Receiver.receive: msg=$msg")
				if (msg.timestamp.isAfter(cutoff)) {
					val dataPoint = DataPoint(msg.lat, msg.lon, msg.timestamp)
					val list = newData.getOrPut(msg.from) { mutableListOf() }
					list.add(dataPoint)
				}
			}

			Log.d("Locodile", "Receiver.receive: newData=$newData")

			newData
		}
	}

	fun startReceiving() {
		if (job == null) {
			job = model.viewModelScope.launch {
				while (isActive) {
					receive()
					delay(7_000)
				}
			}
		}
	}

	fun stopReceiving() {
		job?.cancel()
		job = null
	}
}
