package com.greenify.reborn.engine

import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.data.UserSettingsState
import com.greenify.reborn.model.AppEntry
import com.greenify.reborn.model.AppFilterChip
import com.greenify.reborn.model.AppOpRestriction
import com.greenify.reborn.model.AppWakeupSummary
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.RecentsProtection
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.StandbyBucket
import com.greenify.reborn.model.SwipeCloseMode
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WorkingMode
import com.greenify.reborn.ui.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellCommandBuilderTest {

    @Test
    fun `sanitizePackages removes duplicates protected and malformed names`() {
        val input = listOf(
            "com.example.alpha",
            "com.example.alpha",
            "android",
            "com.android.systemui",
            "com.greenify.reborn",
            "bad;rm -rf /",
            "com.example.beta ",
        )
        assertEquals(
            listOf("com.example.alpha", "com.example.beta"),
            ShellCommandBuilder.sanitizePackages(input),
        )
    }

    @Test
    fun `buildHibernationCommands emits linear O(N) commands respecting per-app strategies`() {
        val packages = listOf("com.example.one", "com.example.two", "com.example.three")
        val policies = mapOf(
            "com.example.two" to PerAppPolicy(strategy = HibernationStrategy.SHALLOW_STANDBY),
            "com.example.three" to PerAppPolicy(strategy = HibernationStrategy.EXEMPT),
        )
        val commands = ShellCommandBuilder.buildHibernationCommands(
            packages = packages,
            globalShallowMode = false,
            targetBucket = StandbyBucket.RARE,
            perAppPolicies = policies,
            userId = 0,
        )
        assertEquals(
            listOf(
                "am force-stop --user 0 com.example.one",
                "am set-standby-bucket --user 0 com.example.one rare",
                "am set-standby-bucket --user 0 com.example.two rare",
            ),
            commands,
        )
    }

    @Test
    fun `shallow hibernation emits only standby bucket command`() {
        val commands = ShellCommandBuilder.buildHibernationCommands(
            packages = listOf("com.example.one"),
            shallowMode = true,
            userId = 10,
        )
        assertEquals(
            listOf("am set-standby-bucket --user 10 com.example.one restricted"),
            commands,
        )
    }

    @Test
    fun `parseRecentsPackages extracts standard tasks and ignores launcher and protected packages`() {
        val sample = """
            * Recent #0: Task{3248488 #934 type=standard A=10490:com.greenify.reborn.debug}
            * Recent #1: Task{ae984b5 #2 type=home I=com.android.launcher/.Launcher}
              realActivity={com.android.launcher/com.android.launcher.Launcher}
            * Recent #2: Task{6462eab #926 type=standard A=10382:com.spotify.music}
              realActivity={com.spotify.music/com.spotify.music.MainActivity}
            * Recent #3: Task{e848155 #910 type=standard A=10385:com.example.notes}
              baseIntent=Intent { cmp=com.example.notes/.MainActivity }
        """.trimIndent()

        assertEquals(
            setOf("com.spotify.music", "com.example.notes"),
            ShellCommandBuilder.parseRecentsPackages(sample),
        )
    }

    @Test
    fun `parseActiveMediaPackages detects playing sessions and ignores paused when finer detection enabled`() {
        val mediaDump = """
            Sessions Stack - have 2 sessions:
            package=com.spotify.music
              state=PlaybackState {state=3, position=1200}
            package=com.google.android.youtube
              state=PlaybackState {state=2, position=5000}
        """.trimIndent()

        assertEquals(
            setOf("com.spotify.music"),
            ShellCommandBuilder.parseActiveMediaPackages(mediaDump, "", finerPlaybackDetection = true),
        )
        assertEquals(
            setOf("com.spotify.music", "com.google.android.youtube"),
            ShellCommandBuilder.parseActiveMediaPackages(mediaDump, "", finerPlaybackDetection = false),
        )
    }

    @Test
    fun `filterProtectedPackages shields recents, audio players, foreground and respects overrides`() {
        val requested = listOf(
            "com.spotify.music",
            "com.example.recent",
            "com.example.override",
            "com.example.fg",
            "com.example.idle",
        )
        val policies = mapOf(
            "com.example.override" to PerAppPolicy(recentsProtection = RecentsProtection.ALLOW_HIBERNATION),
        )

        val result = ShellCommandBuilder.filterProtectedPackages(
            requestedPackages = requested,
            recentsPackages = setOf("com.example.recent", "com.example.override"),
            activeAudioPackages = setOf("com.spotify.music"),
            foregroundPackage = "com.example.fg",
            perAppPolicies = policies,
            excludeRecentsApps = true,
            protectActiveAudioApps = true,
            excludeForegroundApp = true,
        )

        assertEquals(listOf("com.example.override", "com.example.idle"), result.eligiblePackages)
        assertTrue(result.skippedReasons.containsKey("com.spotify.music"))
        assertTrue(result.skippedReasons.containsKey("com.example.recent"))
        assertTrue(result.skippedReasons.containsKey("com.example.fg"))
    }

    @Test
    fun `component and appops command builders validate inputs safely`() {
        assertEquals(
            listOf("cmd appops set --user 0 com.example.app WAKE_LOCK ignore"),
            ShellCommandBuilder.buildAppOpCommands(listOf("com.example.app"), AppOpRestriction.WAKE_LOCK, true, 0),
        )
        assertEquals(
            "pm disable-user --user 0 com.example.app/com.example.app.BootReceiver",
            ShellCommandBuilder.buildComponentStateCommand("com.example.app", "com.example.app.BootReceiver", false, 0),
        )
        assertNull(
            ShellCommandBuilder.buildComponentStateCommand("com.example.app", "bad;rm -rf /", false, 0),
        )
    }

    @Test
    fun `composable filterApps layers filters on top of each other`() {
        val apps = listOf(
            AppEntry(
                packageName = "com.user.awake",
                label = "User Awake",
                versionName = "1.0",
                isSystemApp = false,
                isStopped = false,
                inRecents = true,
                standbyBucket = StandbyBucket.ACTIVE,
            ),
            AppEntry(
                packageName = "com.user.nap",
                label = "User Napping",
                versionName = "1.0",
                isSystemApp = false,
                isStopped = true,
                standbyBucket = StandbyBucket.RESTRICTED,
                wakeupSummary = AppWakeupSummary(totalCount = 2),
            ),
            AppEntry(
                packageName = "com.sys.awake",
                label = "System Awake",
                versionName = "1.0",
                isSystemApp = true,
                isStopped = false,
                standbyBucket = StandbyBucket.WORKING_SET,
            ),
        )

        val userAndAwake = MainUiState.filterApps(apps, setOf(AppFilterChip.USER, AppFilterChip.AWAKE), "")
        assertEquals(listOf("com.user.awake"), userAndAwake.map { it.packageName })

        val sysAndAwake = MainUiState.filterApps(apps, setOf(AppFilterChip.SYSTEM, AppFilterChip.AWAKE), "")
        assertEquals(listOf("com.sys.awake"), sysAndAwake.map { it.packageName })

        val wakeupsOnly = MainUiState.filterApps(apps, setOf(AppFilterChip.WAKEUPS), "")
        assertEquals(listOf("com.user.nap"), wakeupsOnly.map { it.packageName })
    }

    @Test
    fun `resolveBackend respects preferred mode and auto fallback order`() {
        assertEquals(
            ResolvedBackend.SHIZUKU,
            DeviceAppRepository.resolveBackend(WorkingMode.AUTO, shizukuReady = true, rootAvailable = true),
        )
        assertEquals(
            ResolvedBackend.ROOT,
            DeviceAppRepository.resolveBackend(WorkingMode.AUTO, shizukuReady = false, rootAvailable = true),
        )
        assertEquals(
            ResolvedBackend.ROOT,
            DeviceAppRepository.resolveBackend(WorkingMode.ROOT, shizukuReady = true, rootAvailable = true),
        )
        assertEquals(
            ResolvedBackend.SHIZUKU,
            DeviceAppRepository.resolveBackend(WorkingMode.SHIZUKU, shizukuReady = true, rootAvailable = true),
        )
        assertEquals(
            ResolvedBackend.NON_ROOT,
            DeviceAppRepository.resolveBackend(WorkingMode.SHIZUKU, shizukuReady = false, rootAvailable = true),
        )
    }

    @Test
    fun `default theme follows system with monet and max refresh rate enabled`() {
        assertEquals(ThemeMode.SYSTEM, UserSettingsState().themeMode)
        assertTrue(UserSettingsState().dynamicColor)
        assertFalse(UserSettingsState().menuBarTint)
        assertTrue(UserSettingsState().forceMaxRefreshRate)
        assertEquals(SwipeCloseMode.INHERIT, PerAppPolicy().autoCloseOnSwipe)
        assertTrue(PerAppPolicy().isDefault)
        assertFalse(PerAppPolicy(autoCloseOnSwipe = SwipeCloseMode.ALWAYS_CLOSE).isDefault)
        assertTrue(UserSettingsState().excludeRecentsApps)
        assertTrue(UserSettingsState().protectActiveAudioApps)
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(systemDark = false))
        assertTrue(ThemeMode.FOREST_DARK.resolveDarkTheme(systemDark = false))
        assertTrue(ThemeMode.MIDNIGHT_DARK.resolveDarkTheme(systemDark = false))
        assertFalse(ThemeMode.SAGE_LIGHT.resolveDarkTheme(systemDark = true))
        assertFalse(ThemeMode.MIST_LIGHT.resolveDarkTheme(systemDark = true))
    }

    @Test
    fun `degraded status clears once preferred backend is granted`() {
        val shizukuGranted = EnvironmentStatus(
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuPermissionGranted = true,
            rootAvailable = false,
            usageAccessGranted = false,
            notificationsGranted = false,
            resolvedBackend = ResolvedBackend.SHIZUKU,
        )
        assertFalse(shizukuGranted.isBackendDegraded(WorkingMode.AUTO))
        assertFalse(shizukuGranted.isBackendDegraded(WorkingMode.SHIZUKU))
        assertTrue(shizukuGranted.isBackendDegraded(WorkingMode.ROOT))
    }

    @Test
    fun `swiped away detector returns removed non-protected non-foreground packages`() {
        val skipped = ShellCommandBuilder.findSwipedAwayPackages(
            previousRecents = setOf("com.app.a", "com.app.b", "com.android.systemui"),
            currentRecents = setOf("com.app.b"),
            foregroundPackage = "com.launcher.app",
            excludedPackages = setOf("com.launcher.app"),
        )
        assertEquals(listOf("com.app.a"), skipped)
    }

    @Test
    fun `per-app swipe close mode overrides global setting and only-marked flag`() {
        val alwaysPolicy = PerAppPolicy(autoCloseOnSwipe = SwipeCloseMode.ALWAYS_CLOSE)
        val neverPolicy = PerAppPolicy(autoCloseOnSwipe = SwipeCloseMode.NEVER_CLOSE)
        val exemptAlwaysPolicy = PerAppPolicy(
            strategy = HibernationStrategy.EXEMPT,
            autoCloseOnSwipe = SwipeCloseMode.ALWAYS_CLOSE,
        )
        val inheritOff = UserSettingsState(
            autoCloseSwipedRecents = false,
            perAppPolicies = mapOf("com.app.always" to alwaysPolicy),
        )
        assertTrue(RecentsSwipeMonitorService.shouldMonitor(inheritOff))
        assertTrue(
            RecentsSwipeMonitorService.isEligibleForSwipeClose(
                policy = alwaysPolicy,
                globalEnabled = false,
                onlyMarked = false,
                includeSystem = false,
            ),
        )
        assertFalse(
            RecentsSwipeMonitorService.isEligibleForSwipeClose(
                policy = PerAppPolicy(),
                globalEnabled = false,
                onlyMarked = false,
                includeSystem = false,
            ),
        )
        assertFalse(
            RecentsSwipeMonitorService.isEligibleForSwipeClose(
                policy = neverPolicy,
                globalEnabled = true,
                onlyMarked = false,
                includeSystem = true,
            ),
        )
        assertFalse(
            RecentsSwipeMonitorService.isEligibleForSwipeClose(
                policy = exemptAlwaysPolicy,
                globalEnabled = true,
                onlyMarked = false,
                includeSystem = true,
            ),
        )
        assertTrue(
            RecentsSwipeMonitorService.isEligibleForSwipeClose(
                policy = PerAppPolicy(),
                globalEnabled = true,
                onlyMarked = false,
                includeSystem = false,
            ),
        )
    }

    @Test
    fun `swiped away detector skips current foreground and launcher packages`() {
        val removed = ShellCommandBuilder.findSwipedAwayPackages(
            previousRecents = setOf("com.app.a", "com.app.b", "com.launcher.home", "com.greenify.reborn"),
            currentRecents = setOf("com.app.b"),
            foregroundPackage = "com.app.a",
            excludedPackages = setOf("com.launcher.home"),
        )
        assertTrue(removed.isEmpty())
    }
}
