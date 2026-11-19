package com.greenify.reborn.engine

import com.greenify.reborn.data.DeviceAppRepository
import com.greenify.reborn.data.UserSettingsState
import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.ThemeMode
import com.greenify.reborn.model.WorkingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `buildHibernationCommands emits linear O(N) commands without inner quadratic loop`() {
        val packages = listOf("com.example.one", "com.example.two", "com.example.three")
        val commands = ShellCommandBuilder.buildHibernationCommands(
            packages = packages,
            shallowMode = false,
            userId = 0,
        )
        assertEquals(
            listOf(
                "am force-stop --user 0 com.example.one",
                "am set-standby-bucket --user 0 com.example.one restricted",
                "am force-stop --user 0 com.example.two",
                "am set-standby-bucket --user 0 com.example.two restricted",
                "am force-stop --user 0 com.example.three",
                "am set-standby-bucket --user 0 com.example.three restricted",
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
            ResolvedBackend.NON_ROOT,
            DeviceAppRepository.resolveBackend(WorkingMode.AUTO, shizukuReady = false, rootAvailable = false),
        )
        assertEquals(
            ResolvedBackend.NON_ROOT,
            DeviceAppRepository.resolveBackend(WorkingMode.SHIZUKU, shizukuReady = false, rootAvailable = true),
        )
    }

    @Test
    fun `default theme is forest dark and theme polarity resolves properly`() {
        assertEquals(ThemeMode.FOREST_DARK, UserSettingsState().themeMode)
        assertFalse(UserSettingsState().dynamicColor)
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
}
