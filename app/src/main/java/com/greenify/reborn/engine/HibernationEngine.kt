package com.greenify.reborn.engine

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import com.greenify.reborn.BuildConfig
import com.greenify.reborn.IShellService
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.model.HibernationBatchResult
import com.greenify.reborn.model.HibernationEvent
import com.greenify.reborn.model.ResolvedBackend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class HibernationEngine(
    private val context: Context,
    private val preferences: AppPreferences,
    private val repository: DeviceAppRepository,
) {

    suspend fun hibernate(packages: List<String>): HibernationBatchResult =
        withContext(Dispatchers.IO) {
            val sanitized = ShellCommandBuilder.sanitizePackages(packages)
            if (sanitized.isEmpty()) {
                return@withContext HibernationBatchResult(
                    requested = 0,
                    succeeded = emptyList(),
                    failed = emptyList(),
                    backend = ResolvedBackend.NON_ROOT,
                    message = "No eligible apps ready for a nap.",
                )
            }

            val settings = preferences.state.value
            val env = repository.inspectEnvironment(settings.workingMode)
            val userId = Process.myUserHandle().hashCode()
            val commands = ShellCommandBuilder.buildHibernationCommands(
                packages = sanitized,
                shallowMode = settings.shallowHibernation,
                userId = userId,
            )

            val result = when (env.resolvedBackend) {
                ResolvedBackend.SHIZUKU -> executeViaShizuku(sanitized, commands)
                ResolvedBackend.ROOT -> executeViaRoot(sanitized, commands)
                ResolvedBackend.NON_ROOT -> HibernationBatchResult(
                    requested = sanitized.size,
                    succeeded = emptyList(),
                    failed = sanitized,
                    backend = ResolvedBackend.NON_ROOT,
                    message = "Shizuku or Root is needed to put apps down for a nap automatically.",
                )
            }

            if (result.succeeded.isNotEmpty()) {
                preferences.recordHibernation(
                    packages = result.succeeded,
                    event = HibernationEvent(
                        timestampMillis = System.currentTimeMillis(),
                        packageCount = result.requested,
                        succeededCount = result.succeeded.size,
                        backendUsed = result.backend.displayName,
                        shallowMode = settings.shallowHibernation,
                    ),
                )
            }
            result
        }

    private suspend fun executeViaShizuku(
        packages: List<String>,
        commands: List<String>,
    ): HibernationBatchResult {
        val args = Shizuku.UserServiceArgs(
            ComponentName(context, ShizukuUserService::class.java),
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val binderDeferred = CompletableDeferred<IShellService?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binderDeferred.complete(service?.let { IShellService.Stub.asInterface(it) })
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!binderDeferred.isCompleted) {
                    binderDeferred.complete(null)
                }
            }
        }

        return try {
            withContext(Dispatchers.Main) {
                Shizuku.bindUserService(args, connection)
            }
            val service = withTimeoutOrNull(4_000L) { binderDeferred.await() }
            if (service == null) {
                HibernationBatchResult(
                    requested = packages.size,
                    succeeded = emptyList(),
                    failed = packages,
                    backend = ResolvedBackend.SHIZUKU,
                    message = "Timed out connecting to Shizuku service.",
                )
            } else {
                val failures = service.runCommands(commands.toTypedArray())
                if (failures == 0) {
                    HibernationBatchResult(
                        requested = packages.size,
                        succeeded = packages,
                        failed = emptyList(),
                        backend = ResolvedBackend.SHIZUKU,
                        message = "Put ${packages.size} app(s) down for a nap via Shizuku.",
                    )
                } else {
                    HibernationBatchResult(
                        requested = packages.size,
                        succeeded = emptyList(),
                        failed = packages,
                        backend = ResolvedBackend.SHIZUKU,
                        message = "Shizuku finished with $failures command error(s).",
                    )
                }
            }
        } catch (t: Throwable) {
            HibernationBatchResult(
                requested = packages.size,
                succeeded = emptyList(),
                failed = packages,
                backend = ResolvedBackend.SHIZUKU,
                message = "Shizuku execution failed: ${t.message ?: t::class.simpleName}",
            )
        } finally {
            runCatching {
                withContext(Dispatchers.Main) {
                    Shizuku.unbindUserService(args, connection, true)
                }
            }
        }
    }

    private fun executeViaRoot(
        packages: List<String>,
        commands: List<String>,
    ): HibernationBatchResult {
        val exitCode = runCatching {
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
                for (cmd in commands) {
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }
            process.waitFor()
        }.getOrDefault(-1)

        return if (exitCode == 0) {
            HibernationBatchResult(
                requested = packages.size,
                succeeded = packages,
                failed = emptyList(),
                backend = ResolvedBackend.ROOT,
                message = "Put ${packages.size} app(s) down for a nap via Root.",
            )
        } else {
            HibernationBatchResult(
                requested = packages.size,
                succeeded = emptyList(),
                failed = packages,
                backend = ResolvedBackend.ROOT,
                message = "Root command failed (exit $exitCode). Check Magisk/KSU/APatch su grant.",
            )
        }
    }
}
