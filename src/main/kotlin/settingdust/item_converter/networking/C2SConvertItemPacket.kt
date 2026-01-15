package settingdust.item_converter.networking

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.NetworkEvent
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.RecipeHelper
import java.util.function.Supplier

enum class ConvertAction {
    /** Replace items in the original slot */
    REPLACE,
    /** Move converted items to player inventory */
    TO_INVENTORY,
    /** Drop converted items into the world */
    DROP
}

data class C2SConvertItemPacket(
    val slot: Int,
    val target: ItemStack,
    val count: Int,  // -1 = all, otherwise specific count
    val action: ConvertAction
) {
    companion object {
        val CODEC: Codec<C2SConvertItemPacket> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("slot").forGetter { it.slot },
                ItemStack.CODEC.fieldOf("target").forGetter { it.target },
                Codec.INT.fieldOf("count").forGetter { it.count },
                Codec.STRING.fieldOf("action").forGetter { it.action.name }
            ).apply(instance) { slot, target, count, action ->
                C2SConvertItemPacket(slot, target, count, ConvertAction.valueOf(action))
            }
        }

        fun handle(packet: C2SConvertItemPacket, context: Supplier<NetworkEvent.Context>) = runCatching {
            context.get().enqueueWork {
                val player = context.get().sender ?: return@enqueueWork
                val container = player.containerMenu ?: return@enqueueWork

                val slot = container.getSlot(packet.slot)
                val fromItem = slot.item

                if (fromItem.isEmpty) return@enqueueWork

                val recipeManager = player.level().recipeManager

                if (!RecipeHelper.isValidConversion(recipeManager, fromItem, packet.target)) {
                    return@enqueueWork
                }

                // Calculate how many to convert
                val convertCount = if (packet.count == -1) fromItem.count else minOf(packet.count, fromItem.count)
                if (convertCount <= 0) return@enqueueWork

                // Remove input items
                slot.safeTake(convertCount, convertCount, player)

                // Create output (1:N recipe support - multiply by recipe output count)
                val outputPerInput = packet.target.count
                val totalOutput = convertCount.toLong() * outputPerInput.toLong()
                if (totalOutput <= 0) return@enqueueWork

                when (packet.action) {
                    ConvertAction.REPLACE -> {
                        var remaining = totalOutput
                        val slotMax = minOf(slot.maxStackSize, packet.target.maxStackSize).coerceAtLeast(1)
                        if (slot.mayPlace(packet.target)) {
                            if (slot.item.isEmpty) {
                                val toSlot = minOf(remaining, slotMax.toLong()).toInt()
                                if (toSlot > 0) {
                                    slot.set(packet.target.copyWithCount(toSlot))
                                    remaining -= toSlot.toLong()
                                }
                            } else if (ItemStack.isSameItemSameTags(slot.item, packet.target)) {
                                val space = slotMax - slot.item.count
                                if (space > 0) {
                                    val toSlot = minOf(remaining, space.toLong()).toInt()
                                    slot.item.grow(toSlot)
                                    remaining -= toSlot.toLong()
                                }
                            }
                        }
                        if (remaining > 0) {
                            remaining = addToContainerSlots(container, slot, packet.target, remaining)
                        }
                        if (remaining > 0) {
                            addToInventoryOrDrop(player, packet.target, remaining)
                        }
                        // Play conversion sound
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                    }
                    ConvertAction.TO_INVENTORY -> {
                        addToInventoryOrDrop(player, packet.target, totalOutput)
                        // Play conversion + pickup sound
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, ((player.random.nextFloat() - player.random.nextFloat()) * 0.7f + 1.0f) * 2.0f)
                    }
                    ConvertAction.DROP -> {
                        dropStacks(player, packet.target, totalOutput)
                        // Play conversion sound
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                    }
                }

                container.broadcastChanges()
            }
            context.get().packetHandled = true
        }.onFailure {
            ItemConverter.LOGGER.error("Error handling C2SConvertItemPacket", it)
        }
    }
}

internal fun addToContainerSlots(
    container: AbstractContainerMenu,
    sourceSlot: Slot,
    template: ItemStack,
    totalCount: Long
): Long {
    var remaining = totalCount
    if (remaining <= 0) return 0

    var slots = container.slots.filter { it.container == sourceSlot.container && it != sourceSlot }
    if (sourceSlot.container is Inventory) {
        val inventory = sourceSlot.container as Inventory
        slots = slots.filter { it.index < inventory.items.size }
    }

    for (slot in slots) {
        if (remaining <= 0) break
        val existing = slot.item
        if (existing.isEmpty) continue
        if (!ItemStack.isSameItemSameTags(existing, template)) continue
        if (!slot.mayPlace(template)) continue

        val slotMax = minOf(slot.maxStackSize, template.maxStackSize).coerceAtLeast(1)
        val space = slotMax - existing.count
        if (space <= 0) continue
        val toAdd = minOf(remaining, space.toLong()).toInt()
        existing.grow(toAdd)
        remaining -= toAdd.toLong()
    }

    for (slot in slots) {
        if (remaining <= 0) break
        if (!slot.item.isEmpty) continue
        if (!slot.mayPlace(template)) continue

        val slotMax = minOf(slot.maxStackSize, template.maxStackSize).coerceAtLeast(1)
        val toAdd = minOf(remaining, slotMax.toLong()).toInt()
        if (toAdd <= 0) continue
        slot.set(template.copyWithCount(toAdd))
        remaining -= toAdd.toLong()
    }

    return remaining
}

internal fun addToInventoryOrDrop(player: ServerPlayer, template: ItemStack, totalCount: Long) {
    var remaining = totalCount
    val maxStack = template.maxStackSize.coerceAtLeast(1)

    while (remaining > 0) {
        val toAdd = minOf(remaining, maxStack.toLong()).toInt()
        val stack = template.copyWithCount(toAdd)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
        remaining -= toAdd.toLong()
    }
}

internal fun dropStacks(player: ServerPlayer, template: ItemStack, totalCount: Long) {
    var remaining = totalCount
    val maxStack = template.maxStackSize.coerceAtLeast(1)

    while (remaining > 0) {
        val toDrop = minOf(remaining, maxStack.toLong()).toInt()
        val stack = template.copyWithCount(toDrop)
        player.drop(stack, false)
        remaining -= toDrop.toLong()
    }
}
