package gg.thoth.thothMcMain.lastlocation

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent

class PlayerLocationListener(
    private val lastLocationStore: LastLocationStore
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) {
        lastLocationStore.recordCurrentLocation(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to
        if (!hasMeaningfulChange(event.from, to)) {
            return
        }
        lastLocationStore.record(event.player.uniqueId, to)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val to = event.to
        lastLocationStore.record(event.player.uniqueId, to)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) {
        lastLocationStore.record(event.player.uniqueId, event.respawnLocation)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        flushPlayer(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onKick(event: PlayerKickEvent) {
        flushPlayer(event.player)
    }

    private fun flushPlayer(player: Player) {
        lastLocationStore.recordCurrentLocation(player)
        lastLocationStore.save()
    }

    private fun hasMeaningfulChange(from: Location, to: Location): Boolean {
        return from.world?.uid != to.world?.uid ||
            from.blockX != to.blockX ||
            from.blockY != to.blockY ||
            from.blockZ != to.blockZ ||
            from.yaw != to.yaw ||
            from.pitch != to.pitch
    }
}
