package settingdust.item_converter.networking

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraftforge.common.ForgeMod
import net.minecraftforge.network.NetworkEvent
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.RecipeHelper
import java.util.function.Supplier
import kotlin.math.min

/**
 * Packet sent when player middle-clicks to convert items to the targeted block.
 * Server-side raytraces to find target, searches inventory for convertible items.
 */
object C2SConvertTargetPacket {
    fun handle(context: Supplier<NetworkEvent.Context>) = runCatching {
        context.get().enqueueWork {
            val player = context.get().sender ?: return@enqueueWork
            val level = player.level()

            // Raytrace to find target block
            val from = player.eyePosition
            val reachDistance = player.getAttributeValue(ForgeMod.BLOCK_REACH.get())
            val to = from.add(player.lookAngle.scale(reachDistance))
            val hitResult = level.clip(
                ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
            )

            if (hitResult.type != HitResult.Type.BLOCK) return@enqueueWork
            val blockState = level.getBlockState(hitResult.blockPos)
            if (blockState.isAir) return@enqueueWork

            val target = blockState.getCloneItemStack(hitResult, level, hitResult.blockPos, player)
            if (target.isEmpty) return@enqueueWork

            val selected = player.inventory.getItem(player.inventory.selected)

            // If already holding the target, do nothing
            if (ItemStack.isSameItemSameTags(target, selected)) return@enqueueWork

            // Check if target already exists in inventory - vanilla pick block behavior
            val existingSlot = player.inventory.findSlotMatchingItem(target)
            if (existingSlot != -1) {
                // Item exists in inventory - just switch to it, no conversion
                if (existingSlot in 0..8) {
                    // Already in hotbar, just select it
                    player.inventory.selected = existingSlot
                    player.connection.send(
                        net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(player.inventory.selected)
                    )
                } else {
                    // Swap with current hotbar slot
                    val existingItem = player.inventory.getItem(existingSlot)
                    player.inventory.setItem(existingSlot, selected)
                    player.inventory.setItem(player.inventory.selected, existingItem)
                }
                return@enqueueWork
            }

            // Item not in inventory - try conversion
            val recipeManager = level.recipeManager
            val candidates = findConversionCandidates(player, recipeManager, target)
            if (candidates.isEmpty()) return@enqueueWork

            val shift = player.isShiftKeyDown

            if (shift) {
                // Convert as many as possible to fill a stack
                var remaining = target.maxStackSize

                val result = target.copy().apply { count = 0 }

                for ((slot, outputPerInput) in candidates) {
                    if (remaining <= 0) break
                    val sourceItem = player.inventory.getItem(slot)
                    val available = sourceItem.count
                    val canConvert = min(available, remaining / outputPerInput)
                    if (canConvert > 0) {
                        player.inventory.removeItem(slot, canConvert)
                        result.count += canConvert * outputPerInput
                        remaining -= canConvert * outputPerInput
                    }
                }

                if (result.count > 0) {
                    giveResult(result, player)
                }
            } else {
                // Convert single item from first candidate
                val (slot, outputPerInput) = candidates.first()
                player.inventory.removeItem(slot, 1)
                val result = target.copy().apply { count = outputPerInput }
                giveResult(result, player)
            }
        }
        context.get().packetHandled = true
    }.onFailure {
        ItemConverter.LOGGER.error("Error handling C2SConvertTargetPacket", it)
    }

    /**
     * Find inventory slots with items that can be converted to the target.
     * Returns list of (slot, outputPerInput) sorted by priority:
     * 1. Items that produce outputs with special tags (highest priority)
     * 2. Non-hotbar slots (9-35) before hotbar slots (0-8)
     */
    private fun findConversionCandidates(
        player: ServerPlayer,
        recipeManager: net.minecraft.world.item.crafting.RecipeManager,
        target: ItemStack
    ): List<Pair<Int, Int>> {
        // slot, outputPerInput, isSpecial, slotPriority
        val candidates = mutableListOf<Candidate>()

        for (slot in 0 until player.inventory.items.size) {
            val item = player.inventory.getItem(slot)
            if (item.isEmpty) continue
            if (ItemStack.isSameItemSameTags(item, target)) continue

            val conversions = RecipeHelper.getConversions(recipeManager, item)
            val matchingConversion = conversions.find {
                ItemStack.isSameItemSameTags(it.output.copyWithCount(1), target.copyWithCount(1))
            }

            if (matchingConversion != null) {
                // Slot priority: lower is better. Hotbar (0-8) gets higher number = lower priority
                val slotPriority = if (slot < 9) slot + 36 else slot
                candidates.add(Candidate(slot, matchingConversion.output.count, matchingConversion.isSpecial, slotPriority))
            }
        }

        // Sort: special items first, then by slot priority
        return candidates
            .sortedWith(compareBy({ !it.isSpecial }, { it.slotPriority }))
            .map { Pair(it.slot, it.outputPerInput) }
    }

    private data class Candidate(
        val slot: Int,
        val outputPerInput: Int,
        val isSpecial: Boolean,
        val slotPriority: Int
    )

    /** Give converted result to player, preferring hotbar slot */
    private fun giveResult(result: ItemStack, player: ServerPlayer) {
        if (!player.inventory.add(player.inventory.selected, result)) {
            if (!player.inventory.add(result)) {
                player.drop(result, true)
            }
        }

        player.level().playSound(
            null,
            player.blockPosition(),
            SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
            SoundSource.PLAYERS,
            1.0f,
            1.0f
        )
    }
}
