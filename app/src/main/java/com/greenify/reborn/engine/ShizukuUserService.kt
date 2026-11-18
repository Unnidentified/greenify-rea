package com.greenify.reborn.engine

import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

@Keep
class ShizukuUserService : com.greenify.reborn.IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun runCommands(commands: Array<out String>?): Int {
        if (commands.isNullOrEmpty()) return 0
        var failures = 0
        for (command in commands) {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) continue
            val args = trimmed.split(WHITESPACE_REGEX)
            val exitCode = runCatching {
                val process = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start()
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (reader.readLine() != null) {
                        // Drain output to avoid blocking child process buffers.
                    }
                }
                process.waitFor()
            }.getOrDefault(-1)
            if (exitCode != 0) failures++
        }
        return failures
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
