package settingdust.item_converter.networking

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.NetworkEvent
import org.apache.commons.lang3.math.Fraction
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import settingdust.item_converter.ConvertRules
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.MoreCodecs
import settingdust.item_converter.SimpleItemPredicate
import settingdust.item_converter.compat.ae2.AE2Compat
import settingdust.item_converter.compat.ae2.AE2ConversionHandler
import java.util.function.Supplier

data class C2SConvertMEItemPacket(
    val source: ItemStack,
    val target: ItemStack,
    val mode: C2SConvertItemPacket.Mode
) {
    companion object {
        val CODEC: Codec<C2SConvertMEItemPacket> = RecordCodecBuilder.create { instance ->
            instance.group(
                MoreCodecs.ITEM_STACK.fieldOf("source").forGetter { it.source },
                MoreCodecs.ITEM_STACK.fieldOf("target").forGetter { it.target },
                StringRepresentable.fromEnum { C2SConvertItemPacket.Mode.entries.toTypedArray() }
                    .fieldOf("mode").forGetter { it.mode }
            ).apply(instance, ::C2SConvertMEItemPacket)
        }

        fun handle(packet: C2SConvertMEItemPacket, context: Supplier<NetworkEvent.Context>) = runCatching {
            val player = context.get().sender
            if (player == null) {
                ItemConverter.LOGGER.warn("Received C2SConvertMEItemPacket from null player.")
                return@runCatching
            }

            if (!AE2Compat.isLoaded) {
                ItemConverter.LOGGER.warn("Received C2SConvertMEItemPacket but AE2 is not loaded.")
                return@runCatching
            }

            val sourceItem = packet.source
            val targetItem = packet.target

            val from = ConvertRules.graph.vertexSet().firstOrNull { it.test(sourceItem) }
            if (from == null) {
                player.sendSystemMessage(
                    Component.translatable(
                        "messages.${ItemConverter.ID}.no_rule",
                        sourceItem.displayName
                    )
                )
                return@runCatching
            }

            val targetPredicate = SimpleItemPredicate(targetItem)
            val to = ConvertRules.graph.vertexSet().firstOrNull { it == targetPredicate }
            if (to == null) {
                ItemConverter.LOGGER.error(
                    "${player.displayName.string} trying to convert ${sourceItem.displayName.string} " +
                            "to target not in graph ${targetItem.displayName.string}"
                )
                return@runCatching
            }

            val path = DijkstraShortestPath.findPathBetween(ConvertRules.graph, from, to)
            if (path == null || path.vertexList.size < 2) {
                ItemConverter.LOGGER.error(
                    "${player.displayName.string} trying to convert ${sourceItem.displayName.string} " +
                            "to unreachable target ${targetItem.displayName.string}"
                )
                return@runCatching
            }

            val ratio = path.edgeList.fold(Fraction.ONE) { acc, edge -> edge.fraction.multiplyBy(acc) }
            val shiftClick = packet.mode == C2SConvertItemPacket.Mode.SHIFT_CLICK

            val result = AE2ConversionHandler.convertFromMENetwork(
                player = player,
                sourceItem = sourceItem,
                targetItem = to.predicate.copyWithCount(1),
                path = path,
                ratio = ratio,
                shiftClick = shiftClick
            )

            if (!result.success && result.message != null) {
                player.sendSystemMessage(Component.literal(result.message))
            }
        }.onFailure {
            ItemConverter.LOGGER.error("Error handling C2SConvertMEItemPacket", it)
        }
    }
}
