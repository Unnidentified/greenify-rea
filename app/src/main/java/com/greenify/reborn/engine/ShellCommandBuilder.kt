package com.greenify.reborn.engine

/**
 * Builds deduplicated, single-pass shell commands for app hibernation.
 *
 * Replaces legacy O(N^2) loops and redundant per-PID `kill -9` passes with
 * linear, sanitized `am force-stop` and `am set-standby-bucket` invocations.
 */
object ShellCommandBuilder {

    private val SAFE_PACKAGE_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.greenify.reborn",
        "com.greenify.reborn.debug",
        "moe.shizuku.privileged.api",
    )

    fun sanitizePackages(packages: Iterable<String>): List<String> =
        packages.asSequence()
            .map { it.trim() }
            .filter { SAFE_PACKAGE_REGEX.matches(it) }
            .filterNot { it in PROTECTED_PACKAGES }
            .distinct()
            .toList()

    fun buildHibernationCommands(
        packages: Iterable<String>,
        shallowMode: Boolean,
        userId: Int = 0,
    ): List<String> {
        val sanitized = sanitizePackages(packages)
        if (sanitized.isEmpty()) return emptyList()

        return sanitized.flatMap { pkg ->
            if (shallowMode) {
                listOf("am set-standby-bucket --user $userId $pkg restricted")
            } else {
                listOf(
                    "am force-stop --user $userId $pkg",
                    "am set-standby-bucket --user $userId $pkg restricted",
                )
            }
        }
    }
}
