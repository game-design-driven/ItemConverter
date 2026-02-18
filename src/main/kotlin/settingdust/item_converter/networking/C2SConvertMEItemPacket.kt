package settingdust.item_converter.networking

import appeng.api.stacks.AEItemKey
import appeng.menu.me.common.MEStorageMenu
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.NetworkEvent
import settingdust.item_converter.RecipeHelper
import java.util.function.Supplier

/**
 * Packet to convert items in ME storage.
 */
data class C2SConvertMEItemPacket(
    val input: ItemStack,
    val target: ItemStack,
    val count: Int,  // -1 = bulk (action-dependent), otherwise specific input count
    val action: ConvertAction
) {
    companion object {
        val CODEC: Codec<C2SConvertMEItemPacket> = RecordCodecBuilder.create { instance ->
            instance.group(
                ItemStack.CODEC.fieldOf("input").forGetter { it.input },
                ItemStack.CODEC.fieldOf("target").forGetter { it.target },
                Codec.INT.fieldOf("count").forGetter { it.count },
                Codec.STRING.fieldOf("action").forGetter { it.action.name }
            ).apply(instance) { input, target, count, action ->
                C2SConvertMEItemPacket(input, target, count, ConvertAction.valueOf(action))
            }
        }

        fun handle(packet: C2SConvertMEItemPacket, context: Supplier<NetworkEvent.Context>) = runCatching {
            context.get().enqueueWork {
                val player = context.get().sender ?: return@enqueueWork
                val menu = player.containerMenu

                if (menu !is MEStorageMenu) return@enqueueWork
                if (packet.count != 1 && packet.count != BULK_COUNT) return@enqueueWork

                val recipeManager = player.level().recipeManager
                val conversionTarget = RecipeHelper.findConversionTarget(recipeManager, packet.input, packet.target)
                    ?: return@enqueueWork
                val targetTemplate = conversionTarget.output.copy()

                val storage = menu.host.inventory ?: return@enqueueWork
                val inputKey = AEItemKey.of(packet.input)
                val targetKey = AEItemKey.of(targetTemplate)
                val outputPerInput = targetTemplate.count.coerceAtLeast(1)

                // Determine how many to convert
                val available = storage.extract(inputKey, Long.MAX_VALUE, appeng.api.config.Actionable.SIMULATE, menu.actionSource)
                val convertCount = when {
                    packet.count != BULK_COUNT -> minOf(packet.count.toLong(), available)
                    packet.action == ConvertAction.REPLACE -> available
                    else -> resolveBulkInputCountForSingleOutputStack(
                        available,
                        outputPerInput,
                        targetTemplate.maxStackSize
                    )
                }
                if (convertCount <= 0) return@enqueueWork
                if (convertCount > Long.MAX_VALUE / outputPerInput.toLong()) return@enqueueWork

                // Extract input items
                val extracted = storage.extract(inputKey, convertCount, appeng.api.config.Actionable.MODULATE, menu.actionSource)
                if (extracted < convertCount) {
                    // Rollback partial extraction
                    if (extracted > 0) {
                        storage.insert(inputKey, extracted, appeng.api.config.Actionable.MODULATE, menu.actionSource)
                    }
                    return@enqueueWork
                }

                // 1:N recipe support - multiply by recipe output count
                val totalOutput = convertCount * outputPerInput.toLong()

                when (packet.action) {
                    ConvertAction.REPLACE -> {
                        // Insert converted items back into ME storage
                        val inserted = storage.insert(targetKey, totalOutput, appeng.api.config.Actionable.MODULATE, menu.actionSource)
                        val remainder = totalOutput - inserted
                        if (remainder > 0) {
                            addToInventoryOrDrop(player, targetTemplate, remainder)
                        }
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                    }
                    ConvertAction.TO_INVENTORY -> {
                        // Give to player inventory
                        addToInventoryOrDrop(player, targetTemplate, totalOutput)
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, ((player.random.nextFloat() - player.random.nextFloat()) * 0.7f + 1.0f) * 2.0f)
                    }
                    ConvertAction.DROP -> {
                        // Drop into world
                        dropStacks(player, targetTemplate, totalOutput)
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0f, 1.0f)
                    }
                }
            }
            context.get().packetHandled = true
        }.onFailure {
            settingdust.item_converter.ItemConverter.LOGGER.error("Error handling C2SConvertMEItemPacket", it)
        }
    }
}
