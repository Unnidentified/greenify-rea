package com.greenify.reborn.engine

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.greenify.reborn.BuildConfig
import com.greenify.reborn.IShellService
import com.greenify.reborn.data.AppLogStore
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.model.AppOpRestriction
import com.greenify.reborn.model.HibernationBatchResult
import com.greenify.reborn.model.HibernationEvent
import com.greenify.reborn.model.LiveRuntimeSignals
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

data class ShellExecLine(
    val command: String,
    val exitCode: Int,
    val output: String,
)

class HibernationEngine(
    private val context: Context,
    private val preferences: AppPreferences,
    private val repository: DeviceAppRepository,
) {

    private val logStore = AppLogStore.getInstance(context)

    suspend fun queryLiveRuntimeSignals(backendOverride: ResolvedBackend? = null): LiveRuntimeSignals =
        withContext(Dispatchers.IO) {
            val settings = preferences.state.value
            val backend = backendOverride ?: repository.inspectEnvironment(
                preferredMode = settings.workingMode,
                previousRootVerified = settings.rootVerifiedPreviously,
            ).resolvedBackend

            if (backend == ResolvedBackend.NON_ROOT) {
                return@withContext LiveRuntimeSignals()
            }

            val results = executeRaw(
                backend = backend,
                commands = listOf(
                    "dumpsys activity recents",
                    "dumpsys media_session",
                    "dumpsys audio",
                ),
            )
            if (results.size < 3) return@withContext LiveRuntimeSignals()

            val recents = ShellCommandBuilder.parseRecentsPackages(results[0].output)
            val audio = ShellCommandBuilder.parseActiveMediaPackages(
                mediaSessionOutput = results[1].output,
                audioOutput = results[2].output,
                finerPlaybackDetection = settings.finerMediaPlaybackDetection,
            )
            LiveRuntimeSignals(
                recentsPackages = recents,
                activeAudioPackages = audio,
            )
        }

    suspend fun hibernate(
        packages: List<String>,
        ignoreRecentsAndAudioProtection: Boolean = false,
    ): HibernationBatchResult = withContext(Dispatchers.IO) {
        val sanitized = ShellCommandBuilder.sanitizePackages(packages)
        if (sanitized.isEmpty()) {
            return@withContext HibernationBatchResult(
                requested = 0,
                succeeded = emptyList(),
                failed = emptyList(),
                backend = ResolvedBackend.NON_ROOT,
                message = "No eligible apps selected for hibernation.",
            )
        }

        val settings = preferences.state.value
        val env = repository.inspectEnvironment(
            preferredMode = settings.workingMode,
            previousRootVerified = settings.rootVerifiedPreviously,
        )

        val runtimeSignals = if (env.resolvedBackend != ResolvedBackend.NON_ROOT) {
            queryLiveRuntimeSignals(env.resolvedBackend)
        } else {
            LiveRuntimeSignals()
        }
        val audioCategorySet = resolveAudioCategoryPackages(sanitized)

        val filterResult = if (ignoreRecentsAndAudioProtection) {
            ProtectionFilterResult(sanitized, emptyMap())
        } else {
            ShellCommandBuilder.filterProtectedPackages(
                requestedPackages = sanitized,
                recentsPackages = runtimeSignals.recentsPackages,
                activeAudioPackages = runtimeSignals.activeAudioPackages,
                foregroundPackage = runtimeSignals.foregroundPackage,
                perAppPolicies = settings.perAppPolicies,
                excludeRecentsApps = settings.excludeRecentsApps,
                protectActiveAudioApps = settings.protectActiveAudioApps,
                excludeForegroundApp = settings.excludeForegroundApp,
                audioPackageHintSet = audioCategorySet,
            )
        }

        if (filterResult.skippedReasons.isNotEmpty()) {
            val detail = filterResult.skippedReasons.entries.joinToString("\n") { (pkg, reason) ->
                "$pkg -> $reason"
            }
            logStore.info(
                tag = "RECENTS",
                message = "Protected ${filterResult.skippedReasons.size} active/recent app(s) from hibernation.",
                detail = detail,
            )
        }

        val eligible = filterResult.eligiblePackages
        val skippedList = filterResult.skippedReasons.keys.toList()
        if (eligible.isEmpty()) {
            val msg = if (skippedList.isNotEmpty()) {
                "Skipped ${skippedList.size} protected app(s) open in Recents or playing audio."
            } else {
                "No eligible apps to hibernate."
            }
            return@withContext HibernationBatchResult(
                requested = sanitized.size,
                succeeded = emptyList(),
                skippedProtected = skippedList,
                failed = emptyList(),
                backend = env.resolvedBackend,
                message = msg,
            )
        }

        if (env.resolvedBackend == ResolvedBackend.NON_ROOT) {
            logStore.warn(
                tag = "ENGINE",
                message = "Hibernation aborted: backend resolved to Non-Root.",
                detail = "WorkingMode=${settings.workingMode.name}, ShizukuGranted=${env.shizukuPermissionGranted}, RootAvailable=${env.rootAvailable}",
            )
            return@withContext HibernationBatchResult(
                requested = sanitized.size,
                succeeded = emptyList(),
                skippedProtected = skippedList,
                failed = eligible,
                backend = ResolvedBackend.NON_ROOT,
                message = "Shizuku or Root is needed to hibernate apps automatically.",
            )
        }

        val userId = Process.myUserHandle().hashCode()
        val commands = ShellCommandBuilder.buildHibernationCommands(
            packages = eligible,
            globalShallowMode = settings.shallowHibernation,
            targetBucket = settings.defaultTargetBucket,
            perAppPolicies = settings.perAppPolicies,
            userId = userId,
        )

        val execLines = executeRaw(env.resolvedBackend, commands)
        val failures = execLines.count { it.exitCode != 0 }
        val commandTrace = execLines.joinToString("\n") { line ->
            "[exit=${line.exitCode}] ${line.command}" +
                if (line.output.isNotBlank()) " => ${line.output.replace('\n', ' ')}" else ""
        }

        if (failures == 0 && execLines.isNotEmpty()) {
            logStore.exec(
                tag = env.resolvedBackend.displayName,
                message = "Hibernated ${eligible.size} app(s) (skipped ${skippedList.size} protected).",
                detail = commandTrace,
            )
            preferences.recordHibernation(
                packages = eligible,
                event = HibernationEvent(
                    timestampMillis = System.currentTimeMillis(),
                    packageCount = sanitized.size,
                    succeededCount = eligible.size,
                    backendUsed = env.resolvedBackend.displayName,
                    shallowMode = settings.shallowHibernation,
                ),
            )
            val skippedSuffix = if (skippedList.isNotEmpty()) {
                " (${skippedList.size} active/recent skipped)"
            } else {
                ""
            }
            HibernationBatchResult(
                requested = sanitized.size,
                succeeded = eligible,
                skippedProtected = skippedList,
                failed = emptyList(),
                backend = env.resolvedBackend,
                message = "Put ${eligible.size} app(s) to sleep via ${env.resolvedBackend.displayName}$skippedSuffix.",
            )
        } else {
            logStore.error(
                tag = env.resolvedBackend.displayName,
                message = "Hibernation finished with $failures error(s).",
                detail = commandTrace,
            )
            HibernationBatchResult(
                requested = sanitized.size,
                succeeded = emptyList(),
                skippedProtected = skippedList,
                failed = eligible,
                backend = env.resolvedBackend,
                message = "${env.resolvedBackend.displayName} finished with $failures command error(s).",
            )
        }
    }

    suspend fun setStandbyBucket(packages: List<String>, bucket: StandbyBucket): Result<Int> =
        withContext(Dispatchers.IO) {
            val userId = Process.myUserHandle().hashCode()
            val commands = ShellCommandBuilder.buildStandbyBucketCommands(packages, bucket, userId)
            runPrivilegedBatch(commands, "Set standby bucket '${bucket.label}' on ${packages.size} app(s)")
        }

    suspend fun readAppOps(packageName: String): Map<AppOpRestriction, Boolean> =
        withContext(Dispatchers.IO) {
            val settings = preferences.state.value
            val env = repository.inspectEnvironment(
                preferredMode = settings.workingMode,
                previousRootVerified = settings.rootVerifiedPreviously,
            )
            if (env.resolvedBackend == ResolvedBackend.NON_ROOT) return@withContext emptyMap()
            val safe = ShellCommandBuilder.sanitizePackages(listOf(packageName)).firstOrNull()
                ?: return@withContext emptyMap()
            val userId = Process.myUserHandle().hashCode()
            val result = executeRaw(
                env.resolvedBackend,
                listOf("cmd appops get --user $userId $safe"),
            ).firstOrNull() ?: return@withContext emptyMap()
            val modes = ShellCommandBuilder.parseAppOpsModes(result.output)
            AppOpRestriction.entries.associateWith { op ->
                val m = modes[op.opName]
                m == "ignore" || m == "deny"
            }
        }

    suspend fun setAppOp(
        packages: List<String>,
        restriction: AppOpRestriction,
        blocked: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val userId = Process.myUserHandle().hashCode()
        val commands = ShellCommandBuilder.buildAppOpCommands(packages, restriction, blocked, userId)
        val verb = if (blocked) "Blocked" else "Restored"
        runPrivilegedBatch(commands, "$verb ${restriction.opName} on ${packages.size} app(s)")
    }

    suspend fun setComponentEnabled(
        packageName: String,
        className: String,
        enable: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val userId = Process.myUserHandle().hashCode()
        val cmd = ShellCommandBuilder.buildComponentStateCommand(packageName, className, enable, userId)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid or protected component"))
        val verb = if (enable) "Restored" else "Cut off"
        runPrivilegedBatch(listOf(cmd), "$verb component $packageName/$className")
    }

    suspend fun setComponentsBatchEnabled(
        components: List<Pair<String, String>>,
        enable: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val userId = Process.myUserHandle().hashCode()
        val commands = components.mapNotNull { (pkg, cls) ->
            ShellCommandBuilder.buildComponentStateCommand(pkg, cls, enable, userId)
        }
        val verb = if (enable) "Restored" else "Cut off"
        runPrivilegedBatch(commands, "$verb ${commands.size} wake-up component(s)")
    }

    private suspend fun runPrivilegedBatch(commands: List<String>, summary: String): Result<Int> {
        if (commands.isEmpty()) return Result.success(0)
        val settings = preferences.state.value
        val env = repository.inspectEnvironment(
            preferredMode = settings.workingMode,
            previousRootVerified = settings.rootVerifiedPreviously,
        )
        if (env.resolvedBackend == ResolvedBackend.NON_ROOT) {
            logStore.warn("CUTOFF", "Cannot run '$summary': Shizuku or Root required.")
            return Result.failure(IllegalStateException("Shizuku or Root access is required."))
        }
        val lines = executeRaw(env.resolvedBackend, commands)
        val failures = lines.count { it.exitCode != 0 }
        val trace = lines.joinToString("\n") { "[exit=${it.exitCode}] ${it.command} ${it.output}".trim() }
        return if (failures == 0 && lines.isNotEmpty()) {
            logStore.exec(env.resolvedBackend.displayName, summary, trace)
            Result.success(lines.size)
        } else {
            logStore.error(env.resolvedBackend.displayName, "$summary failed ($failures error(s))", trace)
            Result.failure(IllegalStateException("$failures command(s) failed"))
        }
    }

    private fun resolveAudioCategoryPackages(packages: List<String>): Set<String> {
        val pm = context.packageManager
        return packages.filterTo(mutableSetOf()) { pkg ->
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                info.category == ApplicationInfo.CATEGORY_AUDIO
            }.getOrDefault(false)
        }
    }

    private suspend fun executeRaw(
        backend: ResolvedBackend,
        commands: List<String>,
    ): List<ShellExecLine> = when (backend) {
        ResolvedBackend.SHIZUKU -> executeViaShizuku(commands)
        ResolvedBackend.ROOT -> executeViaRoot(commands)
        ResolvedBackend.NON_ROOT -> emptyList()
    }

    private suspend fun executeViaShizuku(commands: List<String>): List<ShellExecLine> =
        shizukuMutex.withLock {
            if (commands.isEmpty()) return@withLock emptyList()
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

            try {
                withContext(Dispatchers.Main) {
                    Shizuku.bindUserService(args, connection)
                }
                val service = withTimeoutOrNull(4_500L) { binderDeferred.await() }
                if (service == null) {
                    logStore.error("SHIZUKU", "Timed out binding ShizukuUserService.")
                    commands.map { ShellExecLine(it, -1, "Timed out connecting to Shizuku service") }
                } else {
                    val rawOutputs = service.runCommandsWithOutput(commands.toTypedArray())
                    commands.mapIndexed { index, cmd ->
                        val packed = rawOutputs.getOrNull(index) ?: "-1|No output"
                        val sep = packed.indexOf('|')
                        if (sep > 0) {
                            val code = packed.substring(0, sep).toIntOrNull() ?: -1
                            val text = packed.substring(sep + 1)
                            ShellExecLine(cmd, code, text)
                        } else {
                            ShellExecLine(cmd, -1, packed)
                        }
                    }
                }
            } catch (t: Throwable) {
                val msg = t.message ?: t::class.simpleName ?: "Unknown Shizuku error"
                logStore.error("SHIZUKU", "Shizuku execution exception: $msg")
                commands.map { ShellExecLine(it, -1, msg) }
            } finally {
                runCatching {
                    withContext(Dispatchers.Main) {
                        Shizuku.unbindUserService(args, connection, true)
                    }
                }
            }
        }

    companion object {
        private val shizukuMutex = Mutex()
    }

    private fun executeViaRoot(commands: List<String>): List<ShellExecLine> {
        if (commands.isEmpty()) return emptyList()
        return runCatching {
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
                commands.forEachIndexed { idx, cmd ->
                    writer.write(cmd)
                    writer.newLine()
                    writer.write("echo \"__GR_END_${idx}:$?\"")
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }

            val buckets = Array(commands.size) { StringBuilder() }
            val exitCodes = IntArray(commands.size) { -1 }
            var currentIdx = 0
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.startsWith("__GR_END_")) {
                        val suffix = line.removePrefix("__GR_END_")
                        val colon = suffix.indexOf(':')
                        val idx = suffix.substring(0, colon.coerceAtLeast(0)).toIntOrNull()
                        val code = if (colon >= 0) suffix.substring(colon + 1).toIntOrNull() ?: -1 else -1
                        if (idx != null && idx in exitCodes.indices) {
                            exitCodes[idx] = code
                            currentIdx = (idx + 1).coerceAtMost(commands.lastIndex)
                        }
                    } else if (currentIdx in buckets.indices && buckets[currentIdx].length < 8_000) {
                        if (buckets[currentIdx].isNotEmpty()) buckets[currentIdx].append('\n')
                        buckets[currentIdx].append(line)
                    }
                    line = reader.readLine()
                }
            }
            val finished = process.waitFor(15_000L, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
            commands.mapIndexed { idx, cmd ->
                ShellExecLine(cmd, exitCodes[idx], buckets[idx].toString().trim())
            }
        }.getOrElse { err ->
            val msg = err.message ?: err::class.simpleName.orEmpty()
            commands.map { ShellExecLine(it, -1, msg) }
        }
    }
}
