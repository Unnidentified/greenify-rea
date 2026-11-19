package com.greenify.reborn.engine

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.greenify.reborn.data.AppPreferences
import com.greenify.reborn.data.DeviceAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HibernateTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        val settings = AppPreferences(applicationContext).state.value
        val count = settings.managedPackages.size
        qsTile?.apply {
            state = if (count > 0 || settings.autoHibernateAllUserApps) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = when {
                settings.autoHibernateAllUserApps -> "All user apps"
                count > 0 -> "$count scheduled"
                else -> "Tap to nap"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = AppPreferences(applicationContext)
        val settings = prefs.state.value
        val repository = DeviceAppRepository(applicationContext)
        val packages = settings.managedPackages.toList()
        if (packages.isEmpty() && !settings.autoHibernateAllUserApps) {
            AutoHibernationScheduler.enqueueImmediate(applicationContext)
            return
        }
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "Starting nap..."
            updateTile()
        }
        scope.launch {
            val engine = HibernationEngine(
                context = applicationContext,
                preferences = prefs,
                repository = repository,
            )
            val targetPackages = if (packages.isNotEmpty()) {
                packages
            } else {
                repository.loadInstalledApps(
                    perAppPolicies = settings.perAppPolicies,
                    lastNappedTimestamps = settings.lastNappedTimestamps,
                    wakeupRecords = settings.wakeupRecords,
                    includeSystemApps = false,
                ).apps.filter { !it.isSystemApp }.map { it.packageName }
            }
            val result = engine.hibernate(targetPackages)
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                subtitle = if (result.succeeded.isNotEmpty()) {
                    "Napping: ${result.succeeded.size}"
                } else {
                    result.backend.displayName
                }
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
