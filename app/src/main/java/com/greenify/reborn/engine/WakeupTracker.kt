package com.greenify.reborn.engine

import com.greenify.reborn.model.AppWakeupSummary
import com.greenify.reborn.model.WakeupEvent
import kotlinx.serialization.Serializable

@Serializable
data class WakeupRecord(
    val lastCountedNapMillis: Long = 0L,
    val totalWakeups: Int = 0,
    val events: List<WakeupEvent> = emptyList(),
)

data class WakeupReconcileInput(
    val packageName: String,
    val isStopped: Boolean,
    val lastNappedAt: Long?,
    val usageWakeupTimestamp: Long? = null,
    val usageWakeupCause: String? = null,
)

object WakeupTracker {

    private const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L
    private const val MAX_EVENTS_PER_APP = 20

    /**
     * Reconciles persisted wake-up records against the latest app process & usage state.
     *
     * A wake-up is recorded at most once per nap cycle (`lastNappedAt > lastCountedNapMillis`)
     * when the app is observed awake (`!isStopped`) or has a UsageStats foreground/service wake event
     * strictly after `lastNappedAt`.
     */
    fun reconcile(
        existing: Map<String, WakeupRecord>,
        observations: List<WakeupReconcileInput>,
        nowMillis: Long,
    ): Map<String, WakeupRecord> {
        if (observations.isEmpty()) return existing
        val updated = existing.toMutableMap()

        for (obs in observations) {
            val napTs = obs.lastNappedAt ?: continue
            val current = updated[obs.packageName] ?: WakeupRecord()
            if (napTs <= current.lastCountedNapMillis) {
                continue
            }

            val usageWakeTs = obs.usageWakeupTimestamp?.takeIf { it > napTs }
            val wokeUp = !obs.isStopped || usageWakeTs != null
            if (!wokeUp) {
                continue
            }

            val wakeupTs = (usageWakeTs ?: nowMillis).coerceAtLeast(napTs)
            val cause = obs.usageWakeupCause?.takeIf { it.isNotBlank() } ?: "Woke from nap"
            val newEvents = (listOf(WakeupEvent(wakeupTs, cause)) + current.events)
                .take(MAX_EVENTS_PER_APP)

            updated[obs.packageName] = WakeupRecord(
                lastCountedNapMillis = napTs,
                totalWakeups = current.totalWakeups + 1,
                events = newEvents,
            )
        }
        return updated
    }

    fun summarize(record: WakeupRecord?, nowMillis: Long): AppWakeupSummary {
        if (record == null || record.totalWakeups == 0) {
            return AppWakeupSummary()
        }
        val cutoff = nowMillis - TWENTY_FOUR_HOURS_MS
        val recentCount = record.events.count { it.timestampMillis >= cutoff }
        return AppWakeupSummary(
            totalCount = record.totalWakeups,
            last24hCount = recentCount,
            lastWakeupAt = record.events.firstOrNull()?.timestampMillis,
            recentEvents = record.events,
        )
    }
}
