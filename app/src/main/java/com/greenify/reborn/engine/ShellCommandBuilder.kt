package com.greenify.reborn.engine

import com.greenify.reborn.model.AppOpRestriction
import com.greenify.reborn.model.HibernationStrategy
import com.greenify.reborn.model.PerAppPolicy
import com.greenify.reborn.model.RecentsProtection
import com.greenify.reborn.model.StandbyBucket

data class ProtectionFilterResult(
    val eligiblePackages: List<String>,
    val skippedReasons: Map<String, String>,
)

/**
 * Builds deduplicated, single-pass shell commands for app hibernation,
 * standby buckets, AppOps wake-up cutoffs, component disabling, and parses
 * system dumpsys signals for Recents & Audio protection.
 */
object ShellCommandBuilder {

    private val SAFE_PACKAGE_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    private val SAFE_COMPONENT_REGEX = Regex("^[a-zA-Z0-9_.$]+$")

    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.greenify.reborn",
        "com.greenify.reborn.debug",
        "moe.shizuku.privileged.api",
    )

    private val RECENTS_TASK_AFFINITY_REGEX = Regex("""Task\{[^}]*type=standard[^}]*\sA=\d+:([a-zA-Z0-9_.]+)""")
    private val RECENTS_REAL_ACTIVITY_REGEX = Regex("""realActivity=\{([a-zA-Z0-9_.]+)/""")
    private val RECENTS_BASE_CMP_REGEX = Regex("""cmp=([a-zA-Z0-9_.]+)/""")
    private val MEDIA_PACKAGE_REGEX = Regex("""package=([a-zA-Z0-9_.]+)""")
    private val MEDIA_STATE_REGEX = Regex("""state=PlaybackState\s*\{state=(\d+)""")
    private val AUDIO_PKG_REGEX = Regex("""(?:pkg|package)[=:]\s*([a-zA-Z0-9_.]+)""")
    private val APPOP_LINE_REGEX = Regex("""^\s*([A-Z0-9_]+):\s*(allow|ignore|deny|default|foreground)\b""")

    fun sanitizePackages(packages: Iterable<String>): List<String> =
        packages.asSequence()
            .map { it.trim() }
            .filter { SAFE_PACKAGE_REGEX.matches(it) }
            .filterNot { it in PROTECTED_PACKAGES }
            .distinct()
            .toList()

    fun isSafeComponent(packageName: String, className: String): Boolean {
        val pkg = packageName.trim()
        val cls = className.trim()
        return SAFE_PACKAGE_REGEX.matches(pkg) &&
            pkg !in PROTECTED_PACKAGES &&
            SAFE_COMPONENT_REGEX.matches(cls)
    }

    fun filterProtectedPackages(
        requestedPackages: Iterable<String>,
        recentsPackages: Set<String>,
        activeAudioPackages: Set<String>,
        foregroundPackage: String?,
        perAppPolicies: Map<String, PerAppPolicy>,
        excludeRecentsApps: Boolean,
        protectActiveAudioApps: Boolean,
        excludeForegroundApp: Boolean,
        audioPackageHintSet: Set<String> = emptySet(),
    ): ProtectionFilterResult {
        val eligible = mutableListOf<String>()
        val skipped = linkedMapOf<String, String>()

        for (pkg in sanitizePackages(requestedPackages)) {
            val policy = perAppPolicies[pkg] ?: PerAppPolicy()
            if (policy.strategy == HibernationStrategy.EXEMPT) {
                skipped[pkg] = "Exempt by per-app rule"
                continue
            }
            if (excludeForegroundApp && pkg == foregroundPackage) {
                skipped[pkg] = "Currently in foreground"
                continue
            }
            val isPlayingAudio = pkg in activeAudioPackages ||
                (pkg in recentsPackages && pkg in audioPackageHintSet)
            if (protectActiveAudioApps && isPlayingAudio &&
                policy.recentsProtection != RecentsProtection.ALLOW_HIBERNATION
            ) {
                skipped[pkg] = "Active audio / media player"
                continue
            }
            val shouldProtectRecents = when (policy.recentsProtection) {
                RecentsProtection.ALWAYS_PROTECT -> true
                RecentsProtection.ALLOW_HIBERNATION -> false
                RecentsProtection.INHERIT -> excludeRecentsApps
            }
            if (shouldProtectRecents && pkg in recentsPackages) {
                skipped[pkg] = "Open in Recents"
                continue
            }
            eligible += pkg
        }
        return ProtectionFilterResult(eligiblePackages = eligible, skippedReasons = skipped)
    }

    fun buildHibernationCommands(
        packages: Iterable<String>,
        shallowMode: Boolean,
        userId: Int = 0,
    ): List<String> = buildHibernationCommands(
        packages = packages,
        globalShallowMode = shallowMode,
        targetBucket = StandbyBucket.RESTRICTED,
        perAppPolicies = emptyMap(),
        userId = userId,
    )

    fun buildHibernationCommands(
        packages: Iterable<String>,
        globalShallowMode: Boolean,
        targetBucket: StandbyBucket,
        perAppPolicies: Map<String, PerAppPolicy>,
        userId: Int = 0,
    ): List<String> {
        val sanitized = sanitizePackages(packages)
        if (sanitized.isEmpty()) return emptyList()
        val bucketArg = targetBucket.shellArg ?: "restricted"

        return sanitized.flatMap { pkg ->
            val strategy = perAppPolicies[pkg]?.strategy ?: HibernationStrategy.GLOBAL_DEFAULT
            val useShallow = when (strategy) {
                HibernationStrategy.GLOBAL_DEFAULT -> globalShallowMode
                HibernationStrategy.DEEP_FORCE_STOP -> false
                HibernationStrategy.SHALLOW_STANDBY -> true
                HibernationStrategy.EXEMPT -> return@flatMap emptyList()
            }
            if (useShallow) {
                listOf("am set-standby-bucket --user $userId $pkg $bucketArg")
            } else {
                listOf(
                    "am force-stop --user $userId $pkg",
                    "am set-standby-bucket --user $userId $pkg $bucketArg",
                )
            }
        }
    }

    fun buildStandbyBucketCommands(
        packages: Iterable<String>,
        bucket: StandbyBucket,
        userId: Int = 0,
    ): List<String> {
        val arg = bucket.shellArg ?: return emptyList()
        return sanitizePackages(packages).map { pkg ->
            "am set-standby-bucket --user $userId $pkg $arg"
        }
    }

    fun buildAppOpCommands(
        packages: Iterable<String>,
        restriction: AppOpRestriction,
        blocked: Boolean,
        userId: Int = 0,
    ): List<String> {
        val mode = if (blocked) "ignore" else "allow"
        return sanitizePackages(packages).map { pkg ->
            "cmd appops set --user $userId $pkg ${restriction.opName} $mode"
        }
    }

    fun buildComponentStateCommand(
        packageName: String,
        className: String,
        enable: Boolean,
        userId: Int = 0,
    ): String? {
        if (!isSafeComponent(packageName, className)) return null
        val pkg = packageName.trim()
        val cls = className.trim()
        return if (enable) {
            "pm enable --user $userId $pkg/$cls"
        } else {
            "pm disable-user --user $userId $pkg/$cls"
        }
    }

    fun parseRecentsPackages(dumpsysRecentsOutput: String): Set<String> {
        if (dumpsysRecentsOutput.isBlank()) return emptySet()
        val result = linkedSetOf<String>()
        var insideStandardTask = false
        for (line in dumpsysRecentsOutput.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("* Recent #")) {
                insideStandardTask = trimmed.contains("type=standard") && !trimmed.contains("type=home")
                val match = RECENTS_TASK_AFFINITY_REGEX.find(trimmed)
                val pkg = match?.groupValues?.getOrNull(1)
                if (pkg != null && SAFE_PACKAGE_REGEX.matches(pkg)) {
                    result += pkg
                }
                continue
            }
            if (insideStandardTask) {
                RECENTS_REAL_ACTIVITY_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { pkg ->
                    if (SAFE_PACKAGE_REGEX.matches(pkg)) result += pkg
                }
                RECENTS_BASE_CMP_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { pkg ->
                    if (SAFE_PACKAGE_REGEX.matches(pkg)) result += pkg
                }
            }
        }
        return result - PROTECTED_PACKAGES
    }

    /**
     * Computes packages that were in [previousRecents] and have been removed from
     * [currentRecents] (e.g. swiped away on the Recents screen), excluding protected
     * packages, the active foreground app, and any launcher/IME packages in [excludedPackages].
     */
    fun findSwipedAwayPackages(
        previousRecents: Set<String>,
        currentRecents: Set<String>,
        foregroundPackage: String?,
        excludedPackages: Set<String> = emptySet(),
    ): List<String> {
        if (previousRecents.isEmpty()) return emptyList()
        return sanitizePackages(previousRecents - currentRecents)
            .filter { pkg ->
                pkg != foregroundPackage && pkg !in excludedPackages
            }
    }

    fun parseActiveMediaPackages(
        mediaSessionOutput: String,
        audioOutput: String,
        finerPlaybackDetection: Boolean = true,
    ): Set<String> {
        val active = linkedSetOf<String>()
        var currentMediaPkg: String? = null
        var inSessionsStack = false
        for (line in mediaSessionOutput.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Sessions Stack")) {
                inSessionsStack = true
                currentMediaPkg = null
                continue
            }
            if (!inSessionsStack) continue
            MEDIA_PACKAGE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { pkg ->
                if (SAFE_PACKAGE_REGEX.matches(pkg)) {
                    currentMediaPkg = pkg
                    if (!finerPlaybackDetection) {
                        active += pkg
                    }
                }
            }
            if (finerPlaybackDetection && currentMediaPkg != null) {
                val stateMatch = MEDIA_STATE_REGEX.find(trimmed)
                if (stateMatch != null) {
                    val stateCode = stateMatch.groupValues[1].toIntOrNull()
                    // PlaybackState.STATE_PLAYING = 3, STATE_BUFFERING = 6, STATE_FAST_FORWARDING = 4, STATE_REWINDING = 5
                    if (stateCode == 3 || stateCode == 4 || stateCode == 5 || stateCode == 6) {
                        active += currentMediaPkg
                    }
                }
            }
        }

        // Also parse active AudioPlaybackConfiguration lines from `dumpsys audio`
        var currentAudioStarted = false
        var currentAudioPkg: String? = null
        for (line in audioOutput.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.contains("AudioPlaybackConfiguration")) {
                if (currentAudioStarted && currentAudioPkg != null) {
                    active += currentAudioPkg
                }
                currentAudioStarted = trimmed.contains("state:started", ignoreCase = true) ||
                    trimmed.contains("PLAYER_STATE_STARTED", ignoreCase = true)
                currentAudioPkg = AUDIO_PKG_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                    ?.takeIf { SAFE_PACKAGE_REGEX.matches(it) }
            } else {
                if (trimmed.contains("state:started", ignoreCase = true) ||
                    trimmed.contains("PLAYER_STATE_STARTED", ignoreCase = true)
                ) {
                    currentAudioStarted = true
                }
                if (currentAudioPkg == null) {
                    currentAudioPkg = AUDIO_PKG_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                        ?.takeIf { SAFE_PACKAGE_REGEX.matches(it) }
                }
            }
        }
        if (currentAudioStarted && currentAudioPkg != null) {
            active += currentAudioPkg
        }
        return active - PROTECTED_PACKAGES
    }

    fun parseAppOpsModes(appOpsOutput: String): Map<String, String> {
        if (appOpsOutput.isBlank()) return emptyMap()
        val modes = linkedMapOf<String, String>()
        for (line in appOpsOutput.lineSequence()) {
            val match = APPOP_LINE_REGEX.find(line) ?: continue
            val op = match.groupValues[1]
            val mode = match.groupValues[2]
            modes[op] = mode
        }
        return modes
    }
}
