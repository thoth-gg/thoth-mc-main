package gg.thoth.thothMcMain

import gg.thoth.thothMcMain.farming.BulkPlantListener
import gg.thoth.thothMcMain.lastlocation.LastLocationDestination
import gg.thoth.thothMcMain.lastlocation.LastLocationStore
import gg.thoth.thothMcMain.lastlocation.PlayerLocationListener
import gg.thoth.thothMcMain.loginbonus.DailyLoginBonusListener
import gg.thoth.thothMcMain.loginbonus.DailyLoginBonusStore
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin
import org.mvplugins.multiverse.core.MultiverseCoreApi

class ThothMcMain : JavaPlugin() {

    private lateinit var lastLocationStore: LastLocationStore
    private lateinit var loginBonusStore: DailyLoginBonusStore

    override fun onEnable() {
        lastLocationStore = LastLocationStore(this)
        lastLocationStore.load()
        loginBonusStore = DailyLoginBonusStore(this)
        loginBonusStore.load()

        server.pluginManager.registerEvents(PlayerLocationListener(lastLocationStore), this)
        server.pluginManager.registerEvents(BulkPlantListener(), this)
        registerDailyLoginBonus()

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
        if (::loginBonusStore.isInitialized) {
            loginBonusStore.save()
        }
    }

    private fun registerDailyLoginBonus() {
        val economy = setupEconomy()
        if (economy == null) {
            logger.warning("Vault economy provider was not found. Daily login bonus is disabled.")
            return
        }

        server.pluginManager.registerEvents(DailyLoginBonusListener(this, economy, loginBonusStore), this)
        logger.info("Daily login bonus enabled with provider ${economy.name}")
    }

    private fun setupEconomy(): Economy? {
        if (server.pluginManager.getPlugin("Vault") == null) {
            return null
        }

        return server.servicesManager.getRegistration(Economy::class.java)?.provider
    }
}
