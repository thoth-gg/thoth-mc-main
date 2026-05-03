package gg.thoth.thothMcMain.loginbonus

import net.milkbowl.vault.economy.Economy
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.time.LocalDate

class DailyLoginBonusListener(
    private val plugin: JavaPlugin,
    private val economy: Economy,
    private val loginBonusStore: DailyLoginBonusStore,
    private val dailyBonusAmount: Double = 100.0,
    private val cumulativeBonusAmount: Double = 500.0,
    private val cumulativeRewardLoginDays: Int = 7,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val today = LocalDate.now()
        if (loginBonusStore.hasClaimed(player.uniqueId, today)) {
            grantCumulativeBonusIfReady(player)
            return
        }

        val response = economy.depositPlayer(player, dailyBonusAmount)
        if (!response.transactionSuccess()) {
            plugin.logger.warning(
                "Failed to grant daily login bonus to ${player.name}: ${response.errorMessage}"
            )
            return
        }

        loginBonusStore.markClaimed(player.uniqueId, today)
        loginBonusStore.save()
        player.sendMessage("ログインボーナスとして${economy.format(dailyBonusAmount)}を受け取りました。")
        grantCumulativeBonusIfReady(player)
    }

    private fun grantCumulativeBonusIfReady(player: Player) {
        if (loginBonusStore.getCumulativeLoginDays(player.uniqueId) < cumulativeRewardLoginDays) {
            return
        }

        val response = economy.depositPlayer(player, cumulativeBonusAmount)
        if (!response.transactionSuccess()) {
            plugin.logger.warning(
                "Failed to grant cumulative login bonus to ${player.name}: ${response.errorMessage}"
            )
            return
        }

        loginBonusStore.resetCumulativeLoginDays(player.uniqueId)
        loginBonusStore.save()
        player.sendMessage("累積${cumulativeRewardLoginDays}日ログインボーナスとして${economy.format(cumulativeBonusAmount)}を受け取りました。")
    }
}
