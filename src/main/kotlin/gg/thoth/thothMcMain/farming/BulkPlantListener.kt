package gg.thoth.thothMcMain.farming

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import java.util.ArrayDeque

class BulkPlantListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        val hand = event.hand ?: return
        if (event.useInteractedBlock() == Event.Result.DENY || event.useItemInHand() == Event.Result.DENY) {
            return
        }

        val player = event.player
        if (!player.isSneaking || player.gameMode == GameMode.SPECTATOR) {
            return
        }
        if (hand == EquipmentSlot.OFF_HAND && player.inventory.getItem(EquipmentSlot.HAND).type in PLANTABLE_CROPS) {
            return
        }

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != Material.FARMLAND) {
            return
        }

        val item = player.inventory.getItem(hand)
        val cropMaterial = PLANTABLE_CROPS[item.type] ?: return
        if (item.amount <= 0) {
            return
        }

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)

        val consumeItems = player.gameMode != GameMode.CREATIVE
        val itemConsumer = PlantItemConsumer(player.inventory, hand, item.type)
        val farmlandPositions = collectConnectedFarmland(clickedBlock)

        for (position in orderedPositions(farmlandPositions, clickedBlock)) {
            val farmlandBlock = clickedBlock.world.getBlockAt(position.x, clickedBlock.y, position.z)
            if (!canPlantAt(farmlandBlock, cropMaterial)) {
                continue
            }

            if (consumeItems && !itemConsumer.consumeOne()) {
                return
            }
            plantAt(farmlandBlock, cropMaterial)
        }
    }

    private fun collectConnectedFarmland(start: Block): Set<BlockPosition> {
        val farmlandPositions = mutableSetOf<BlockPosition>()
        val queue = ArrayDeque<Block>()
        val y = start.y

        queue.add(start)
        while (queue.isNotEmpty()) {
            val block = queue.removeFirst()
            if (block.y != y || block.type != Material.FARMLAND) {
                continue
            }

            val position = BlockPosition(block.x, block.z)
            if (!farmlandPositions.add(position)) {
                continue
            }

            HORIZONTAL_FACES.forEach { face ->
                queue.add(block.getRelative(face))
            }
        }

        return farmlandPositions
    }

    private fun orderedPositions(farmlandPositions: Set<BlockPosition>, clickedBlock: Block): List<BlockPosition> {
        if (farmlandPositions.isEmpty()) {
            return emptyList()
        }

        val minX = farmlandPositions.minOf { it.x }
        val maxX = farmlandPositions.maxOf { it.x }
        val minZ = farmlandPositions.minOf { it.z }
        val maxZ = farmlandPositions.maxOf { it.z }
        val clickedPosition = BlockPosition(clickedBlock.x, clickedBlock.z)
        val startingCorner = listOf(
            BlockPosition(minX, minZ),
            BlockPosition(minX, maxZ),
            BlockPosition(maxX, minZ),
            BlockPosition(maxX, maxZ),
        ).minWith(
            compareBy<BlockPosition> { distanceSquared(it, clickedPosition) }
                .thenBy { it.x }
                .thenBy { it.z }
        )

        val xLength = maxX - minX + 1
        val zLength = maxZ - minZ + 1
        val xStep = if (startingCorner.x == minX) 1 else -1
        val zStep = if (startingCorner.z == minZ) 1 else -1
        val orderedPositions = ArrayList<BlockPosition>(farmlandPositions.size)

        if (xLength <= zLength) {
            for (zOffset in 0 until zLength) {
                val z = startingCorner.z + (zOffset * zStep)
                for (xOffset in 0 until xLength) {
                    val position = BlockPosition(startingCorner.x + (xOffset * xStep), z)
                    if (position in farmlandPositions) {
                        orderedPositions.add(position)
                    }
                }
            }
        } else {
            for (xOffset in 0 until xLength) {
                val x = startingCorner.x + (xOffset * xStep)
                for (zOffset in 0 until zLength) {
                    val position = BlockPosition(x, startingCorner.z + (zOffset * zStep))
                    if (position in farmlandPositions) {
                        orderedPositions.add(position)
                    }
                }
            }
        }

        return orderedPositions
    }

    private fun canPlantAt(farmlandBlock: Block, cropMaterial: Material): Boolean {
        if (farmlandBlock.type != Material.FARMLAND) {
            return false
        }

        val cropBlock = farmlandBlock.getRelative(BlockFace.UP)
        if (!cropBlock.type.isAir) {
            return false
        }

        return cropBlock.canPlace(createCropData(cropMaterial))
    }

    private fun plantAt(farmlandBlock: Block, cropMaterial: Material) {
        farmlandBlock.getRelative(BlockFace.UP).setBlockData(createCropData(cropMaterial), true)
    }

    private fun createCropData(cropMaterial: Material) =
        Bukkit.createBlockData(cropMaterial).also { blockData ->
            if (blockData is Ageable) {
                blockData.age = 0
            }
        }

    private fun distanceSquared(first: BlockPosition, second: BlockPosition): Int {
        val xDistance = first.x - second.x
        val zDistance = first.z - second.z
        return (xDistance * xDistance) + (zDistance * zDistance)
    }

    private data class BlockPosition(
        val x: Int,
        val z: Int,
    )

    private class PlantItemConsumer(
        private val inventory: PlayerInventory,
        preferredHand: EquipmentSlot,
        private val itemType: Material,
    ) {
        private val slots = buildConsumptionOrder(inventory, preferredHand)

        fun consumeOne(): Boolean {
            for (slot in slots) {
                val item = slot.get(inventory)
                if (item?.type != itemType || item.amount <= 0) {
                    continue
                }

                if (item.amount > 1) {
                    item.amount -= 1
                    slot.set(inventory, item)
                } else {
                    slot.set(inventory, null)
                }
                return true
            }

            return false
        }

        private fun buildConsumptionOrder(inventory: PlayerInventory, preferredHand: EquipmentSlot): List<InventorySlot> {
            val preferredSlot = when (preferredHand) {
                EquipmentSlot.OFF_HAND -> InventorySlot.Hand(EquipmentSlot.OFF_HAND)
                else -> InventorySlot.Index(inventory.heldItemSlot)
            }
            val slots = mutableListOf(preferredSlot)

            inventory.storageContents.indices
                .map { InventorySlot.Index(it) }
                .filterTo(slots) { it != preferredSlot }

            val offHandSlot = InventorySlot.Hand(EquipmentSlot.OFF_HAND)
            if (offHandSlot != preferredSlot) {
                slots.add(offHandSlot)
            }

            return slots
        }
    }

    private sealed class InventorySlot {
        abstract fun get(inventory: PlayerInventory): ItemStack?

        abstract fun set(inventory: PlayerInventory, item: ItemStack?)

        data class Index(
            val index: Int,
        ) : InventorySlot() {
            override fun get(inventory: PlayerInventory): ItemStack? = inventory.getItem(index)

            override fun set(inventory: PlayerInventory, item: ItemStack?) {
                inventory.setItem(index, item)
            }
        }

        data class Hand(
            val hand: EquipmentSlot,
        ) : InventorySlot() {
            override fun get(inventory: PlayerInventory): ItemStack? = inventory.getItem(hand)

            override fun set(inventory: PlayerInventory, item: ItemStack?) {
                inventory.setItem(hand, item)
            }
        }
    }

    private companion object {
        val HORIZONTAL_FACES = listOf(
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST,
        )

        val PLANTABLE_CROPS = mapOf(
            Material.WHEAT_SEEDS to Material.WHEAT,
            Material.CARROT to Material.CARROTS,
            Material.POTATO to Material.POTATOES,
            Material.BEETROOT_SEEDS to Material.BEETROOTS,
            Material.MELON_SEEDS to Material.MELON_STEM,
            Material.PUMPKIN_SEEDS to Material.PUMPKIN_STEM,
            Material.TORCHFLOWER_SEEDS to Material.TORCHFLOWER_CROP,
            Material.PITCHER_POD to Material.PITCHER_CROP,
        )
    }
}
