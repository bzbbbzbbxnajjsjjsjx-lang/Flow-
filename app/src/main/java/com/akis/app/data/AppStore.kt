package com.akis.app.data

import android.content.Context
import android.util.AtomicFile
import com.akis.app.domain.AppData
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * All app data lives in memory and in one file. Reads happen once at start; writes go through a
 * conflated channel, so a burst of changes costs one atomic write of the latest state.
 */
@OptIn(ExperimentalSerializationApi::class)
class AppStore(context: Context, scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "akis.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow<AppData?>(null)
    private val writes = Channel<AppData>(Channel.CONFLATED)

    /** Null while the file is still being read. */
    val data: StateFlow<AppData?> = state

    init {
        scope.launch(Dispatchers.IO) {
            state.value = read()
            for (next in writes) write(next)
        }
    }

    /** Applies [transform]; the file is only rewritten when it actually changed something. */
    fun update(transform: (AppData) -> AppData) {
        var changed = false
        state.update { current ->
            val next = current?.let(transform)
            if (next != current) changed = true
            next
        }
        if (changed) state.value?.let { writes.trySend(it) }
    }

    private fun read(): AppData {
        // Streamed, so the whole file is never held as bytes and as a string next to the objects.
        return runCatching { file.openRead().use { json.decodeFromStream(AppData.serializer(), it) } }.getOrNull() ?: AppData()
    }

    private fun write(data: AppData) {
        val out = file.startWrite()
        try {
            json.encodeToStream(AppData.serializer(), data, out)
            file.finishWrite(out)
        } catch (_: IOException) {
            file.failWrite(out)
        }
    }
}
