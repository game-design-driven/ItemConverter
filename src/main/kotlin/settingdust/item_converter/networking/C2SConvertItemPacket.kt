package settingdust.item_converter.networking

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
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
                val result = packet.target.copy()
                result.count = convertCount * outputPerInput

                when (packet.action) {
                    ConvertAction.REPLACE -> {
                        // Put back in same slot if possible, otherwise to inventory
                        if (slot.mayPlace(result)) {
                            if (slot.item.isEmpty) {
                                slot.set(result)
                            } else if (ItemStack.isSameItemSameTags(slot.item, result)) {
                                slot.item.grow(result.count)
                            } else {
                                // Slot has different item now, fallback to inventory
                                if (!player.inventory.add(result)) {
                                    player.drop(result, false)
                                }
                            }
                        } else {
                            if (!player.inventory.add(result)) {
                                player.drop(result, false)
                            }
                        }
                        // Play conversion sound
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                    }
                    ConvertAction.TO_INVENTORY -> {
                        if (!player.inventory.add(result)) {
                            player.drop(result, false)
                        }
                        // Play conversion + pickup sound
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, ((player.random.nextFloat() - player.random.nextFloat()) * 0.7f + 1.0f) * 2.0f)
                    }
                    ConvertAction.DROP -> {
                        player.drop(result, false)
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
