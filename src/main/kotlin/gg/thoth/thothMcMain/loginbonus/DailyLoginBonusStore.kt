package gg.thoth.thothMcMain.loginbonus

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DailyLoginBonusStore(
    private val plugin: JavaPlugin
) {
    private val file = File(plugin.dataFolder, "login-bonuses.yml")
    private val states = ConcurrentHashMap<UUID, LoginBonusState>()

    fun load() {
        if (!file.exists()) {
            return
        }

        val yaml = YamlConfiguration.loadConfiguration(file)
        for (playerId in yaml.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(playerId) }.getOrNull() ?: continue
            val section = yaml.getConfigurationSection(playerId)
            if (section != null) {
                val claimedDate = section.getString("last-claimed-date")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: continue
                val cumulativeLoginDays = section.getInt("cumulative-login-days", 0).coerceAtLeast(0)

                states[uuid] = LoginBonusState(claimedDate, cumulativeLoginDays)
                continue
            }

            val claimedDate = yaml.getString(playerId)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: continue

            states[uuid] = LoginBonusState(claimedDate, 1)
        }
    }

    fun save() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        val yaml = YamlConfiguration()
        for ((playerId, state) in states) {
            yaml.set("$playerId.last-claimed-date", state.lastClaimedDate.toString())
            yaml.set("$playerId.cumulative-login-days", state.cumulativeLoginDays)
        }

        try {
            yaml.save(file)
        } catch (e: IOException) {
            plugin.logger.warning("Failed to save ${file.name}: ${e.message}")
        }
    }

    fun hasClaimed(playerId: UUID, date: LocalDate): Boolean {
        return states[playerId]?.lastClaimedDate == date
    }

    fun markClaimed(playerId: UUID, date: LocalDate): Int {
        val cumulativeLoginDays = (states[playerId]?.cumulativeLoginDays ?: 0) + 1
        states[playerId] = LoginBonusState(date, cumulativeLoginDays)
        return cumulativeLoginDays
    }

    fun getCumulativeLoginDays(playerId: UUID): Int {
        return states[playerId]?.cumulativeLoginDays ?: 0
    }

    fun resetCumulativeLoginDays(playerId: UUID) {
        val state = states[playerId] ?: return
        states[playerId] = state.copy(cumulativeLoginDays = 0)
    }

    private data class LoginBonusState(
        val lastClaimedDate: LocalDate,
        val cumulativeLoginDays: Int,
    )
}
