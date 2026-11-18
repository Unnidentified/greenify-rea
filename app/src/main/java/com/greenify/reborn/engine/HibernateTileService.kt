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
        val count = AppPreferences(applicationContext).state.value.greenifiedPackages.size
        qsTile?.apply {
            state = if (count > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (count > 0) "$count app(s)" else "No apps selected"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = AppPreferences(applicationContext)
        val packages = prefs.state.value.greenifiedPackages.toList()
        if (packages.isEmpty()) {
            AutoHibernationScheduler.enqueueImmediate(applicationContext)
            return
        }
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "Hibernating..."
            updateTile()
        }
        scope.launch {
            val engine = HibernationEngine(
                context = applicationContext,
                preferences = prefs,
                repository = DeviceAppRepository(applicationContext),
            )
            val result = engine.hibernate(packages)
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                subtitle = if (result.succeeded.isNotEmpty()) {
                    "Stopped ${result.succeeded.size}"
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
