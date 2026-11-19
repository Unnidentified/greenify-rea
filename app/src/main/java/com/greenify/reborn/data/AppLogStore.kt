package com.greenify.reborn.data

import android.content.Context
import com.greenify.reborn.model.EngineLogEntry
import com.greenify.reborn.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class AppLogStore private constructor(context: Context) {

    private val logFile = File(context.applicationContext.filesDir, LOG_FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingPersistJob: Job? = null
    private val idCounter = AtomicLong(1L)

    private val _logs = MutableStateFlow<List<EngineLogEntry>>(emptyList())
    val logs: StateFlow<List<EngineLogEntry>> = _logs.asStateFlow()

    init {
        val loaded = runCatching {
            if (logFile.exists()) {
                json.decodeFromString<List<EngineLogEntry>>(logFile.readText()).take(MAX_LOG_ENTRIES)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
        _logs.value = loaded
        val maxId = loaded.maxOfOrNull { it.id } ?: 0L
        idCounter.set(maxId + 1L)
    }

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        detail: String? = null,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val entry = EngineLogEntry(
            id = idCounter.getAndIncrement(),
            timestampMillis = timestampMillis,
            level = level,
            tag = tag.uppercase(Locale.US),
            message = message,
            detail = detail?.takeIf { it.isNotBlank() },
        )
        synchronized(lock) {
            val prev = _logs.value
            val limit = minOf(prev.size, MAX_LOG_ENTRIES - 1)
            val updated = ArrayList<EngineLogEntry>(limit + 1).apply {
                add(entry)
                for (i in 0 until limit) add(prev[i])
            }
            _logs.value = updated
            schedulePersistLocked()
        }
    }

    fun info(tag: String, message: String, detail: String? = null) =
        log(LogLevel.INFO, tag, message, detail)

    fun exec(tag: String, message: String, detail: String? = null) =
        log(LogLevel.EXEC, tag, message, detail)

    fun warn(tag: String, message: String, detail: String? = null) =
        log(LogLevel.WARN, tag, message, detail)

    fun error(tag: String, message: String, detail: String? = null) =
        log(LogLevel.ERROR, tag, message, detail)

    fun clear() {
        synchronized(lock) {
            pendingPersistJob?.cancel()
            pendingPersistJob = null
            _logs.value = emptyList()
            ioScope.launch {
                runCatching {
                    if (logFile.exists()) logFile.delete()
                }
            }
        }
        info("LOGGER", "Engine logs cleared by user.")
    }

    private fun schedulePersistLocked() {
        pendingPersistJob?.cancel()
        pendingPersistJob = ioScope.launch {
            delay(400L)
            val snapshot = synchronized(lock) { _logs.value }
            runCatching {
                logFile.writeText(json.encodeToString(snapshot))
            }
        }
    }

    companion object {
        private const val LOG_FILE_NAME = "engine_logs.json"
        private const val MAX_LOG_ENTRIES = 250

        @Volatile
        private var instance: AppLogStore? = null

        fun getInstance(context: Context): AppLogStore {
            return instance ?: synchronized(this) {
                instance ?: AppLogStore(context.applicationContext).also { instance = it }
            }
        }

        fun formatAllRawLogs(entries: List<EngineLogEntry>): String {
            if (entries.isEmpty()) return "No engine logs recorded yet."
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            return entries.asReversed().joinToString("\n") { entry ->
                val ts = formatter.format(Date(entry.timestampMillis))
                val header = "[$ts] [${entry.level.name}] [${entry.tag}] ${entry.message}"
                if (entry.detail.isNullOrBlank()) {
                    header
                } else {
                    val indented = entry.detail.lines().joinToString("\n") { "    $it" }
                    "$header\n$indented"
                }
            }
        }
    }
}
