package bg.dnevnitapet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A small value persisted as a JSON file in the app's private storage, observable as a flow.
 * Writes are atomic (temp file + rename), so a crash never leaves a half-written file.
 */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val mutex = Mutex()
    private val flow = MutableStateFlow(load())

    val state: StateFlow<T> = flow.asStateFlow()
    val value: T get() = flow.value

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val old = flow.value
        val new = transform(old)
        if (new != old) {
            withContext(Dispatchers.IO) { write(new) }
            flow.value = new
        }
        new
    }

    private fun load(): T = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else default()
    } catch (e: Exception) {
        // Keep the unreadable file for inspection and start fresh instead of crashing.
        runCatching { file.copyTo(File(file.path + ".broken"), overwrite = true) }
        default()
    }

    private fun write(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
