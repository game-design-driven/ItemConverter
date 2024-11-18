package settingdust.item_converter.networking

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.util.function.Supplier

data class C2SConvertItemPacket(val slot: Int, val target: ItemStack, val mode: Mode) {
    companion object {
        val CODEC = RecordCodecBuilder.create<C2SConvertItemPacket> { instance ->
            instance.group(
                Codec.INT.fieldOf("slot").forGetter { it.slot },
                MoreCodecs.ITEM_STACK.fieldOf("target").forGetter { it.target },
                StringRepresentable.fromEnum { Mode.values() }.fieldOf("mode").forGetter { it.mode }
            ).apply(instance, ::C2SConvertItemPacket)
        }

        fun handle(packet: C2SConvertItemPacket, context: Supplier<NetworkEvent.Context>) = runCatching {
            val player = context.get().sender
            if (player == null) {
                ItemConverter.LOGGER.warn("Received C2SConvertItemPacket from null player.")
                return@runCatching
            }
            val container = player.containerMenu
            if (container == null) {
                ItemConverter.LOGGER.warn("Received C2SConvertItemPacket of null container from ${player.displayName}.")
                return@runCatching
            }
            val slot = container.getSlot(packet.slot)
            val fromItem = slot.item
            val from = ConvertRules.graph.vertexSet().firstOrNull { it.test(fromItem) }
            if (from == null) {
                player.sendSystemMessage(
                    Component.translatable(
                        "messages.${ItemConverter.ID}.no_rule",
                        fromItem.displayName
                    )
                )
                return@runCatching
            }
            val targetPredicate = SimpleItemPredicate(packet.target)
            val to = ConvertRules.graph.vertexSet().firstOrNull { it == targetPredicate }
            if (to == null) {
                ItemConverter.LOGGER.error("${player.displayName.string} trying to convert ${fromItem.displayName.string} to target not in graph ${packet.target.displayName.string}")
                return@runCatching
            }
            val path = DijkstraShortestPath.findPathBetween(ConvertRules.graph, from, to)
            if (path == null) {
                ItemConverter.LOGGER.error("${player.displayName.string} trying to convert ${fromItem.displayName.string} to unreachable target ${packet.target.displayName.string}")
                return@runCatching
            }
            val ratio = path.edgeList.fold(Fraction.ONE) { acc, edge -> edge.fraction.multiplyBy(acc) }
            when (packet.mode) {
                Mode.SINGLE_CLICK -> {
                    if (ratio.denominator > fromItem.count) {
                        return@runCatching
                    }
                    runBlocking(ItemConverter.serverCoroutineDispatcher!!) {
                        slot.safeTake(ratio.denominator, ratio.denominator, player)
                    }
                    val itemToInsert = to.predicate.copy().also {
                        it.count = ratio.numerator
                    }
                    ItemConverter.serverCoroutineScope!!.launch {
                        if (!player.inventory.add(itemToInsert)) {
                            player.drop(itemToInsert, true)
                        }
                    }
                }

                Mode.SHIFT_CLICK -> {
                    val times = fromItem.count / ratio.denominator
                    val amount = ratio.denominator * times
                    runBlocking(ItemConverter.serverCoroutineDispatcher!!) { slot.safeTake(amount, amount, player) }
                    val itemToInsert = to.predicate.copy().also {
                        it.count = ratio.numerator * times
                    }
                    ItemConverter.serverCoroutineScope!!.launch {
                        if (!player.inventory.add(itemToInsert)) {
                            player.drop(itemToInsert, true)
                        }
                    }
                }
            }
        }.onFailure {
            ItemConverter.LOGGER.error("Error handling C2SConvertItemPacket", it)
        }
    }

    enum class Mode(private val serialName: String) : StringRepresentable {
        SINGLE_CLICK("single"),
        SHIFT_CLICK("shift");

        override fun getSerializedName() = serialName
    }
}