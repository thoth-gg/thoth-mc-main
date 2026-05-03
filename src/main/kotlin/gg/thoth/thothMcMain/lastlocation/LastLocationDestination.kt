package gg.thoth.thothMcMain.lastlocation

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.mvplugins.multiverse.external.acf.locales.MessageKey
import org.mvplugins.multiverse.external.acf.locales.MessageKeyProvider
import org.mvplugins.multiverse.external.vavr.control.Option
import org.mvplugins.multiverse.core.destination.Destination
import org.mvplugins.multiverse.core.destination.DestinationInstance
import org.mvplugins.multiverse.core.destination.DestinationSuggestionPacket
import org.mvplugins.multiverse.core.locale.MVCorei18n
import org.mvplugins.multiverse.core.utils.result.Attempt
import org.mvplugins.multiverse.core.utils.result.FailureReason

class LastLocationDestination(
    private val lastLocationStore: LastLocationStore
) : Destination<LastLocationDestination, LastLocationDestination.Instance, LastLocationDestination.InstanceFailureReason> {

    override fun getIdentifier(): String = "ll"

    override fun getDestinationInstance(
        sender: CommandSender,
        destinationParams: String
    ): Attempt<Instance, InstanceFailureReason> {
        val worldName = destinationParams.trim()
        if (worldName.isEmpty()) {
            return Attempt.failure(InstanceFailureReason.INVALID_FORMAT)
        }

        val world = Bukkit.getWorld(worldName)
            ?: return Attempt.failure(InstanceFailureReason.WORLD_NOT_FOUND)

        return Attempt.success(Instance(this, lastLocationStore, world.name))
    }

    override fun suggestDestinations(
        @NotNull commandSender: CommandSender,
        @Nullable destinationParams: String?
    ): Collection<DestinationSuggestionPacket> {
        return Bukkit.getWorlds().map { world ->
            DestinationSuggestionPacket(this, world.name, world.name)
        }
    }

    enum class InstanceFailureReason(
        private val messageKeyProvider: MessageKeyProvider
    ) : FailureReason {
        INVALID_FORMAT(MVCorei18n.DESTINATION_EXACT_FAILUREREASON_INVALIDFORMAT),
        WORLD_NOT_FOUND(MVCorei18n.DESTINATION_SHARED_FAILUREREASON_WORLDNOTFOUND),
        ;

        override fun getMessageKey(): MessageKey = messageKeyProvider.messageKey
    }

    class Instance(
        destination: LastLocationDestination,
        private val lastLocationStore: LastLocationStore,
        private val worldName: String
    ) : DestinationInstance<Instance, LastLocationDestination>(destination) {

        override fun getLocation(teleportee: Entity): Option<Location> {
            if (teleportee !is Player) {
                return Option.none()
            }

            val world = Bukkit.getWorld(worldName) ?: return Option.none()
            val location = lastLocationStore.get(teleportee, worldName) ?: world.spawnLocation.clone()
            return Option.of(location)
        }

        override fun getVelocity(teleportee: Entity): Option<Vector> = Option.none()

        override fun checkTeleportSafety(): Boolean = false

        override fun getFinerPermissionSuffix(): Option<String> = Option.of(worldName)

        override fun serialise(): String = worldName
    }
}
