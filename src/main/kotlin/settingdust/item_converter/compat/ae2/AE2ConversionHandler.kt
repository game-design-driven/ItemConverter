package settingdust.item_converter.compat.ae2

import appeng.api.networking.IGridNode
import appeng.api.networking.security.IActionHost
import appeng.api.stacks.AEItemKey
import appeng.api.storage.StorageHelper
import appeng.menu.me.common.MEStorageMenu
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import org.apache.commons.lang3.math.Fraction
import org.jgrapht.GraphPath
import settingdust.item_converter.FractionUnweightedEdge
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.SimpleItemPredicate

object AE2ConversionHandler {

    data class ConversionResult(
        val success: Boolean,
        val extracted: Long = 0,
        val inserted: Long = 0,
        val message: String? = null
    )

    fun convertFromMENetwork(
        player: ServerPlayer,
        sourceItem: ItemStack,
        targetItem: ItemStack,
        path: GraphPath<SimpleItemPredicate, FractionUnweightedEdge>,
        ratio: Fraction,
        shiftClick: Boolean
    ): ConversionResult {
        val menu = player.containerMenu
        if (menu !is MEStorageMenu) {
            return ConversionResult(false, message = "Not in ME terminal")
        }

        val host = menu.host
        val gridNode: IGridNode? = when (host) {
            is IActionHost -> host.actionableNode
            else -> null
        }

        if (gridNode == null || !gridNode.isActive) {
            return ConversionResult(false, message = "ME network not active")
        }

        val grid = gridNode.grid ?: return ConversionResult(false, message = "No grid connection")
        val storage = grid.storageService.inventory
        val energySource = grid.energyService
        val actionSource = menu.actionSource

        val sourceKey = AEItemKey.of(sourceItem)
            ?: return ConversionResult(false, message = "Invalid source item")

        val targetKey = AEItemKey.of(targetItem)
            ?: return ConversionResult(false, message = "Invalid target item")

        val amountToExtract = if (shiftClick) {
            val available = storage.getAvailableStacks()[sourceKey] ?: 0L
            val maxConversions = available / ratio.denominator
            val maxOutput = targetItem.maxStackSize.toLong()
            val conversions = minOf(maxConversions, maxOutput / ratio.numerator)
            (conversions * ratio.denominator).coerceAtLeast(0)
        } else {
            ratio.denominator.toLong()
        }

        if (amountToExtract <= 0) {
            return ConversionResult(false, message = "Not enough items")
        }

        val extracted = StorageHelper.poweredExtraction(
            energySource,
            storage,
            sourceKey,
            amountToExtract,
            actionSource
        )

        if (extracted < ratio.denominator) {
            if (extracted > 0) {
                StorageHelper.poweredInsert(energySource, storage, sourceKey, extracted, actionSource)
            }
            return ConversionResult(false, message = "Not enough items or power")
        }

        val conversions = extracted / ratio.denominator
        var outputRemaining = conversions * ratio.numerator
        var totalInserted = 0L

        // Try to insert into player inventory first
        while (outputRemaining > 0) {
            val stackSize = minOf(outputRemaining, targetItem.maxStackSize.toLong()).toInt()
            val outputStack = targetItem.copyWithCount(stackSize)

            if (player.inventory.add(outputStack)) {
                val inserted = stackSize - outputStack.count
                totalInserted += inserted
                outputRemaining -= inserted
                if (outputStack.count > 0) break // Inventory full
            } else {
                break // Inventory full
            }
        }

        // Insert remainder into ME network
        if (outputRemaining > 0) {
            val insertedToME = StorageHelper.poweredInsert(
                energySource,
                storage,
                targetKey,
                outputRemaining,
                actionSource
            )
            totalInserted += insertedToME
            outputRemaining -= insertedToME
        }

        // Return unconverted source items if we couldn't insert all output
        if (outputRemaining > 0) {
            val returnedSource = (outputRemaining / ratio.numerator) * ratio.denominator
            if (returnedSource > 0) {
                StorageHelper.poweredInsert(energySource, storage, sourceKey, returnedSource, actionSource)
            }
        }

        if (totalInserted > 0) {
            val lastEdge = path.edgeList.last()
            player.playNotifySound(
                lastEdge.sound,
                SoundSource.BLOCKS,
                (player.random.nextFloat() * 0.7f + 1.0f) * 2.0f * lastEdge.volume,
                lastEdge.pitch
            )
        }

        return ConversionResult(
            success = totalInserted > 0,
            extracted = extracted,
            inserted = totalInserted
        )
    }
}
