package com.greenify.reborn.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.greenify.reborn.BuildConfig
import com.greenify.reborn.IShellService
import com.greenify.reborn.MainActivity
import com.greenify.reborn.R
import com.greenify.reborn.data.AppLogStore
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.data.UserSettingsState
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.SwipeCloseMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Lightweight foreground monitor that detects apps swiped closed from the Android Recents
 * screen while the display is interactive, then hibernates eligible packages automatically.
 * Sleeps cleanly whenever the screen turns off so background battery/CPU impact stays ~0%.
 */
class RecentsSwipeMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferences: AppPreferences
    private lateinit var repository: DeviceAppRepository
    private lateinit var engine: HibernationEngine
    private lateinit var logStore: AppLogStore

    private var monitorJob: Job? = null
    private var previousRecents: Set<String> = emptySet()
    private var cachedShellService: IShellService? = null
    private var boundArgs: Shizuku.UserServiceArgs? = null
    private var boundConn: ServiceConnection? = null
    private var cachedLaunchersAndImes: Set<String> = emptySet()
    private var lastAuxResolveMs: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> startMonitorLoop()
                Intent.ACTION_SCREEN_OFF -> {
                    stopMonitorLoop()
                    unbindCachedShizuku()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(applicationContext)
        repository = DeviceAppRepository(applicationContext)
        engine = HibernationEngine(applicationContext, preferences, repository)
        logStore = AppLogStore.getInstance(applicationContext)

        promoteToForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shouldMonitor(preferences.state.value)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isInteractive != false) {
            startMonitorLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        stopMonitorLoop()
        unbindCachedShizuku()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            var lastUsageCheckMs = System.currentTimeMillis() - 3_000L
            var lastFullPollMs = 0L
            while (isActive) {
                val settings = preferences.state.value
                if (!shouldMonitor(settings)) {
                    stopSelf()
                    break
                }

                val now = System.currentTimeMillis()
                val hasUsage = repository.hasUsageStatsPermission()
                val shouldSampleRecents = if (hasUsage) {
                    val activityOccurred = hasRecentActivityTransition(lastUsageCheckMs, now)
                    lastUsageCheckMs = now
                    activityOccurred || (now - lastFullPollMs >= FALLBACK_POLL_INTERVAL_MS)
                } else {
                    now - lastFullPollMs >= NO_USAGE_POLL_INTERVAL_MS
                }

                if (shouldSampleRecents) {
                    lastFullPollMs = now
                    checkRecentsDiffAndHibernate()
                }
                delay(if (hasUsage) USAGE_GATE_INTERVAL_MS else NO_USAGE_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = null
        previousRecents = emptySet()
    }

    private suspend fun checkRecentsDiffAndHibernate() {
        val settings = preferences.state.value
        val env = repository.inspectEnvironment(
            preferredMode = settings.workingMode,
            previousRootVerified = settings.rootVerifiedPreviously,
        )
        if (env.resolvedBackend == ResolvedBackend.NON_ROOT) {
            unbindCachedShizuku()
            previousRecents = emptySet()
            return
        }

        val recentsOutput = queryRecentsRaw(env.resolvedBackend) ?: return
        val currentRecents = ShellCommandBuilder.parseRecentsPackages(recentsOutput)
        if (previousRecents.isEmpty()) {
            previousRecents = currentRecents
            return
        }

        val initialRemoved = ShellCommandBuilder.findSwipedAwayPackages(
            previousRecents = previousRecents,
            currentRecents = currentRecents,
            foregroundPackage = resolveCurrentForegroundPackage(),
            excludedPackages = resolveLauncherAndImePackages(),
        )
        if (initialRemoved.isEmpty()) {
            previousRecents = currentRecents
            return
        }

        // Wait briefly to rule out task restarts or activity trampolines
        delay(TRAMPOLINE_DEBOUNCE_MS)
        val confirmOutput = queryRecentsRaw(env.resolvedBackend) ?: return
        val confirmRecents = ShellCommandBuilder.parseRecentsPackages(confirmOutput)
        val confirmedRemoved = ShellCommandBuilder.findSwipedAwayPackages(
            previousRecents = initialRemoved.toSet(),
            currentRecents = confirmRecents,
            foregroundPackage = resolveCurrentForegroundPackage(),
            excludedPackages = resolveLauncherAndImePackages(),
        )
        previousRecents = confirmRecents

        if (confirmedRemoved.isEmpty()) return

        val latestSettings = preferences.state.value
        val eligible = confirmedRemoved.filter { pkg ->
            isEligibleForSwipeClose(
                packageName = pkg,
                policy = latestSettings.perAppPolicies[pkg] ?: PerAppPolicy(),
                globalEnabled = latestSettings.autoCloseSwipedRecents,
                onlyMarked = latestSettings.autoCloseSwipedOnlyMarked,
                includeSystem = latestSettings.includeSystemApps,
            )
        }
        if (eligible.isEmpty()) return

        val result = engine.hibernate(eligible, ignoreRecentsAndAudioProtection = true)
        if (result.succeeded.isNotEmpty()) {
            logStore.exec(
                tag = "SWIPE_CLOSE",
                message = "Auto-closed ${result.succeeded.size} swiped Recents app(s).",
                detail = result.succeeded.joinToString(", "),
            )
        }
    }

    private fun isEligibleForSwipeClose(
        packageName: String,
        policy: PerAppPolicy,
        globalEnabled: Boolean,
        onlyMarked: Boolean,
        includeSystem: Boolean,
    ): Boolean {
        val checkSystem = policy.strategy != HibernationStrategy.EXEMPT &&
            policy.autoCloseOnSwipe == SwipeCloseMode.INHERIT &&
            globalEnabled &&
            (!onlyMarked || policy.includeInScheduledNaps) &&
            !includeSystem &&
            !policy.includeInScheduledNaps
        val isSystem = if (checkSystem) {
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
                (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }.getOrDefault(false)
        } else {
            false
        }
        return Companion.isEligibleForSwipeClose(
            policy = policy,
            globalEnabled = globalEnabled,
            onlyMarked = onlyMarked,
            includeSystem = includeSystem,
            isSystemApp = isSystem,
        )
    }

    private fun hasRecentActivityTransition(sinceMillis: Long, nowMillis: Long): Boolean {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return true
        return runCatching {
            val events = usm.queryEvents(sinceMillis, nowMillis) ?: return false
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.USER_INTERACTION,
                    -> return true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun resolveCurrentForegroundPackage(): String? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        return runCatching {
            val events = usm.queryEvents(now - 60_000L, now) ?: return null
            val ev = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp >= latestTs) {
                    latestTs = ev.timeStamp
                    latestPkg = ev.packageName
                }
            }
            latestPkg
        }.getOrNull()
    }

    private fun resolveLauncherAndImePackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (cachedLaunchersAndImes.isNotEmpty() && now - lastAuxResolveMs < 300_000L) {
            return cachedLaunchersAndImes
        }
        val excluded = mutableSetOf<String>()
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(homeIntent, 0)
            }
            resolved.forEach { it.activityInfo?.packageName?.let(excluded::add) }
        }
        runCatching {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { excluded += it.packageName }
        }
        cachedLaunchersAndImes = excluded
        lastAuxResolveMs = now
        return excluded
    }

    private suspend fun queryRecentsRaw(backend: ResolvedBackend): String? = when (backend) {
        ResolvedBackend.SHIZUKU -> queryViaCachedShizuku()
        ResolvedBackend.ROOT -> queryViaRoot()
        ResolvedBackend.NON_ROOT -> null
    }

    private suspend fun queryViaCachedShizuku(): String? {
        val service = ensureShizukuService() ?: return null
        return runCatching {
            val res = service.runCommandsWithOutput(arrayOf("dumpsys activity recents"))
            val packed = res.firstOrNull() ?: return null
            val sep = packed.indexOf('|')
            if (sep > 0) packed.substring(sep + 1) else packed
        }.getOrElse {
            unbindCachedShizuku()
            null
        }
    }

    private suspend fun ensureShizukuService(): IShellService? {
        val existing = cachedShellService
        if (existing != null && existing.asBinder().pingBinder()) {
            return existing
        }
        unbindCachedShizuku()

        val args = Shizuku.UserServiceArgs(
            ComponentName(applicationContext, ShizukuUserService::class.java),
        )
            .daemon(false)
            .processNameSuffix("swipe")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val deferred = CompletableDeferred<IShellService?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val iface = service?.let { IShellService.Stub.asInterface(it) }
                cachedShellService = iface
                if (!deferred.isCompleted) deferred.complete(iface)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                cachedShellService = null
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }
        boundArgs = args
        boundConn = conn
        return runCatching {
            withContext(Dispatchers.Main) {
                Shizuku.bindUserService(args, conn)
            }
            withTimeoutOrNull(4_000L) { deferred.await() }
        }.getOrNull()
    }

    private fun unbindCachedShizuku() {
        val args = boundArgs
        val conn = boundConn
        cachedShellService = null
        boundArgs = null
        boundConn = null
        if (args != null && conn != null) {
            serviceScope.launch(Dispatchers.Main) {
                runCatching { Shizuku.unbindUserService(args, conn, true) }
            }
        }
    }

    private fun queryViaRoot(): String? = runCatching {
        val process = ProcessBuilder("su")
            .redirectErrorStream(true)
            .start()
        BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
            writer.write("dumpsys activity recents")
            writer.newLine()
            writer.write("exit")
            writer.newLine()
            writer.flush()
        }
        val out = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            var lines = 0
            while (line != null && lines < 600) {
                out.append(line).append('\n')
                lines++
                line = reader.readLine()
            }
        }
        if (!process.waitFor(5_000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return null
        }
        out.toString()
    }.getOrNull()

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = getString(R.string.channel_swipe_monitor_id)
        if (manager != null) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_swipe_monitor_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.channel_swipe_monitor_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_tile_leaf)
            .setContentTitle("Recents Swipe Auto-Close active")
            .setContentText("Monitoring Recents while screen is awake")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 4102
        private const val USAGE_GATE_INTERVAL_MS = 1_500L
        private const val NO_USAGE_POLL_INTERVAL_MS = 2_500L
        private const val FALLBACK_POLL_INTERVAL_MS = 9_000L
        private const val TRAMPOLINE_DEBOUNCE_MS = 650L

        fun shouldMonitor(settings: UserSettingsState): Boolean =
            settings.autoCloseSwipedRecents ||
                settings.perAppPolicies.values.any { it.autoCloseOnSwipe == SwipeCloseMode.ALWAYS_CLOSE }

        internal fun isEligibleForSwipeClose(
            policy: PerAppPolicy,
            globalEnabled: Boolean,
            onlyMarked: Boolean,
            includeSystem: Boolean,
            isSystemApp: Boolean = false,
        ): Boolean {
            if (policy.strategy == HibernationStrategy.EXEMPT) return false
            return when (policy.autoCloseOnSwipe) {
                SwipeCloseMode.NEVER_CLOSE -> false
                SwipeCloseMode.ALWAYS_CLOSE -> true
                SwipeCloseMode.INHERIT -> {
                    if (!globalEnabled) return false
                    if (onlyMarked && !policy.includeInScheduledNaps) return false
                    if (!includeSystem && !policy.includeInScheduledNaps && isSystemApp) return false
                    true
                }
            }
        }

        fun sync(context: Context, settings: UserSettingsState) {
            sync(context, enabled = shouldMonitor(settings))
        }

        fun sync(context: Context, enabled: Boolean) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, RecentsSwipeMonitorService::class.java)
            runCatching {
                if (enabled) {
                    ContextCompat.startForegroundService(appCtx, intent)
                } else {
                    appCtx.stopService(intent)
                }
            }
        }
    }
}
