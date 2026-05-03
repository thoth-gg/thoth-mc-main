package gg.thoth.thothMcMain.lastlocation

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LastLocationStore(
    private val plugin: JavaPlugin
) {
    private val file = File(plugin.dataFolder, "last-locations.yml")
    private val locations = ConcurrentHashMap<UUID, MutableMap<String, Location>>()

    fun load() {
        if (!file.exists()) {
            return
        }

        val yaml = YamlConfiguration.loadConfiguration(file)
        for (playerId in yaml.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(playerId) }.getOrNull() ?: continue
            val worldLocations = mutableMapOf<String, Location>()
            val section = yaml.getConfigurationSection(playerId) ?: continue
            for (worldName in section.getKeys(false)) {
                val location = section.getLocation(worldName) ?: continue
                worldLocations[worldName] = location
            }
            if (worldLocations.isNotEmpty()) {
                locations[uuid] = worldLocations
            }
        }
    }

    fun save() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        val yaml = YamlConfiguration()
        for ((playerId, worldLocations) in locations) {
            for ((worldName, location) in worldLocations) {
                yaml.set("$playerId.$worldName", location)
            }
        }

        try {
            yaml.save(file)
        } catch (e: IOException) {
            plugin.logger.warning("Failed to save ${file.name}: ${e.message}")
        }
    }

    fun recordCurrentLocation(player: Player) {
        record(player.uniqueId, player.location)
    }

    fun record(locationOwner: UUID, location: Location) {
        val world = location.world ?: return
        val normalized = location.clone()
        normalized.world = Bukkit.getWorld(world.uid)

        locations.computeIfAbsent(locationOwner) { ConcurrentHashMap() }[world.name] = normalized
    }

    fun get(player: Player, worldName: String): Location? {
        val world = Bukkit.getWorld(worldName) ?: return null
        val location = locations[player.uniqueId]?.get(world.name) ?: return null
        return location.clone().apply { this.world = world }
    }
}
