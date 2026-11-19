package com.greenify.reborn.engine

import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
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
            val (exitCode, _) = execSingle(command)
            if (exitCode != 0) failures++
        }
        return failures
    }

    override fun runCommandsWithOutput(commands: Array<out String>?): Array<String> {
        if (commands.isNullOrEmpty()) return emptyArray()
        return Array(commands.size) { index ->
            val (exitCode, output) = execSingle(commands[index])
            "$exitCode|$output"
        }
    }

    private fun execSingle(command: String): Pair<Int, String> {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return 0 to ""
        val args = trimmed.split(WHITESPACE_REGEX)
        return runCatching {
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val outputBuilder = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                var lineCount = 0
                while (line != null) {
                    if (lineCount < MAX_OUTPUT_LINES && outputBuilder.length < MAX_OUTPUT_CHARS) {
                        if (outputBuilder.isNotEmpty()) outputBuilder.append('\n')
                        outputBuilder.append(line)
                    }
                    lineCount++
                    line = reader.readLine()
                }
            }
            val finished = process.waitFor(8_000L, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                -1 to "Command timed out"
            } else {
                process.exitValue() to outputBuilder.toString()
            }
        }.getOrElse { err ->
            -1 to (err.message ?: err::class.simpleName.orEmpty())
        }
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private const val MAX_OUTPUT_LINES = 800
        private const val MAX_OUTPUT_CHARS = 65_536
    }
}
