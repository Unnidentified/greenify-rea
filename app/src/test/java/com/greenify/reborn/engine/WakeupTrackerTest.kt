package com.greenify.reborn.engine

import com.greenify.reborn.model.EnvironmentStatus
import com.greenify.reborn.model.ResolvedBackend
import com.greenify.reborn.model.WorkingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeupTrackerTest {

    @Test
    fun `reconcile does not record wakeup while app stays stopped`() {
        val records = WakeupTracker.reconcile(
            existing = emptyMap(),
            observations = listOf(
                WakeupReconcileInput(
                    packageName = "com.example.app",
                    isStopped = true,
                    lastNappedAt = 1_000L,
                ),
            ),
            nowMillis = 5_000L,
        )
        assertTrue(records.isEmpty())
    }

    @Test
    fun `reconcile records one wakeup per nap cycle without double counting`() {
        val first = WakeupTracker.reconcile(
            existing = emptyMap(),
            observations = listOf(
                WakeupReconcileInput(
                    packageName = "com.example.app",
                    isStopped = false,
                    lastNappedAt = 1_000L,
                    usageWakeupTimestamp = 2_500L,
                    usageWakeupCause = "Foreground launch",
                ),
            ),
            nowMillis = 3_000L,
        )
        assertEquals(1, first["com.example.app"]?.totalWakeups)
        assertEquals(2_500L, first["com.example.app"]?.events?.first()?.timestampMillis)
        assertEquals("Foreground launch", first["com.example.app"]?.events?.first()?.cause)

        // Refreshing again while still awake for the same nap cycle must NOT double-count.
        val second = WakeupTracker.reconcile(
            existing = first,
            observations = listOf(
                WakeupReconcileInput(
                    packageName = "com.example.app",
                    isStopped = false,
                    lastNappedAt = 1_000L,
                ),
            ),
            nowMillis = 4_000L,
        )
        assertEquals(1, second["com.example.app"]?.totalWakeups)

        // A new nap cycle followed by waking up increments the count to 2.
        val third = WakeupTracker.reconcile(
            existing = second,
            observations = listOf(
                WakeupReconcileInput(
                    packageName = "com.example.app",
                    isStopped = false,
                    lastNappedAt = 10_000L,
                ),
            ),
            nowMillis = 12_000L,
        )
        assertEquals(2, third["com.example.app"]?.totalWakeups)
        assertEquals(12_000L, third["com.example.app"]?.events?.first()?.timestampMillis)
    }

    @Test
    fun `isBackendDegraded only triggers when preferred mode cannot run`() {
        val nonRootEnv = EnvironmentStatus(
            shizukuInstalled = false,
            shizukuRunning = false,
            shizukuPermissionGranted = false,
            rootAvailable = false,
            usageAccessGranted = false,
            notificationsGranted = true,
            resolvedBackend = ResolvedBackend.NON_ROOT,
        )
        assertTrue(nonRootEnv.isBackendDegraded(WorkingMode.AUTO))
        assertTrue(nonRootEnv.isBackendDegraded(WorkingMode.SHIZUKU))
        assertTrue(nonRootEnv.isBackendDegraded(WorkingMode.ROOT))
        assertFalse(nonRootEnv.isBackendDegraded(WorkingMode.NON_ROOT))

        val shizukuReadyEnv = nonRootEnv.copy(
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuPermissionGranted = true,
            resolvedBackend = ResolvedBackend.SHIZUKU,
        )
        assertFalse(shizukuReadyEnv.isBackendDegraded(WorkingMode.AUTO))
        assertFalse(shizukuReadyEnv.isBackendDegraded(WorkingMode.SHIZUKU))
    }
}
