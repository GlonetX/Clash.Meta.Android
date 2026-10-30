package com.github.kr328.clash.data

import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.settings.*
import com.github.kr328.clash.service.remote.ISettingsObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class SettingsRepository {
    fun observe(): Flow<SettingsSnapshot> = callbackFlow {
        val client = Remote.service.remote.get().settings()
        val observer = object : ISettingsObserver {
            override fun onChanged(snapshot: SettingsSnapshot) { trySend(snapshot) }
        }
        client.addObserver(observer)
        trySend(client.getSnapshot())
        awaitClose { runCatching { client.removeObserver(observer) } }
    }.flowOn(Dispatchers.IO).retryWhen { cause, _ ->
        if(cause is CancellationException) false else { delay(1000); true }
    }
    suspend fun current() = withContext(Dispatchers.IO) { Remote.service.remote.get().settings().getSnapshot() }
    suspend fun update(snapshot: SettingsSnapshot, value: SettingsUpdate): SettingsUpdateResult = withContext(Dispatchers.IO) {
        Remote.service.remote.get().settings().update(snapshot.revision, value)
    }
}
