package gg.thoth.thothMcMain

import gg.thoth.thothMcMain.lastlocation.LastLocationDestination
import gg.thoth.thothMcMain.lastlocation.LastLocationStore
import gg.thoth.thothMcMain.lastlocation.PlayerLocationListener
import org.bukkit.plugin.java.JavaPlugin
import org.mvplugins.multiverse.core.MultiverseCoreApi

class ThothMcMain : JavaPlugin() {

    private lateinit var lastLocationStore: LastLocationStore

    override fun onEnable() {
        lastLocationStore = LastLocationStore(this)
        lastLocationStore.load()

        server.pluginManager.registerEvents(PlayerLocationListener(lastLocationStore), this)

        MultiverseCoreApi.whenLoaded { api ->
            api.destinationsProvider.registerDestination(LastLocationDestination(lastLocationStore))
            logger.info("Registered Multiverse destination ll:<world_name>")
        }
    }

    override fun onDisable() {
        if (::lastLocationStore.isInitialized) {
            server.onlinePlayers.forEach(lastLocationStore::recordCurrentLocation)
            lastLocationStore.save()
        }
    }
}
