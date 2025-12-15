package settingdust.item_converter.networking

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraftforge.network.NetworkEvent
import org.apache.commons.lang3.math.Fraction
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import settingdust.item_converter.ConvertRules
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.SimpleItemPredicate
import java.util.function.Supplier
import kotlin.math.min

object C2SConvertTargetPacket {
    fun handle(context: Supplier<NetworkEvent.Context>) = runCatching {
        val player = context.get().sender
        if (player == null) {
            ItemConverter.LOGGER.warn("Received C2SConvertItemPacket from null player.")
            return@runCatching
        }

        val level = player.level()
        val hitResult = runBlocking(ItemConverter.serverCoroutineDispatcher!!) {
            val from = player.eyePosition
            val to = from.add(player.getViewVector(1f).scale(player.blockReach))
            level.clip(ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        }
        if (hitResult.type != HitResult.Type.BLOCK) return@runCatching
        val blockState = level.getBlockState(hitResult.blockPos)
        if (blockState.isAir) return@runCatching
        val target = blockState.getCloneItemStack(hitResult, level, hitResult.blockPos, player)
        if (target.isEmpty) return@runCatching
        val targetPredicate = SimpleItemPredicate(target)

        val to = ConvertRules.graph.vertexSet().firstOrNull { it == targetPredicate }
        if (to == null) {
            ItemConverter.LOGGER.error("${player.displayName.string} trying to convert to target not in graph ${target.displayName.string}")
            return@runCatching
        }

        ItemConverter.serverCoroutineScope!!.launch(CoroutineExceptionHandler { context, throwable ->
            ItemConverter.LOGGER.error("Error while converting", throwable)
        }) {

            val selected = player.inventory.getItem(player.inventory.selected)
            val paths = player.inventory.items.asSequence()
                .mapIndexed { slot, it -> slot to it }
                .filter { (_, it) -> !ItemStack.isSameItemSameTags(it, target) }
                .map { (slot, it) -> slot to SimpleItemPredicate(it) }
                .filter { (_, it) -> it in ConvertRules.graph.vertexSet() }
                .mapNotNull { (slot, it) ->
                    DijkstraShortestPath.findPathBetween(
                        ConvertRules.graph,
                        it,
                        targetPredicate
                    )?.let { path -> slot to path }
                }
                .map { (slot, path) ->
                    val ratio = path.edgeList.fold(Fraction.ONE) { acc, edge -> edge.fraction.multiplyBy(acc) }
                    Triple(slot, path, ratio)
                }
                .filter { (_, path, ratio) -> path.startVertex.predicate.count >= ratio.denominator }
                .sortedBy { (slot, _, _) -> if (slot <= 8) slot + 36 else slot }

            val shift = player.isShiftKeyDown

            val (itemToInsert, removeMaterials) = if (shift) {
                var amount =
                    if (ItemStack.isSameItemSameTags(target, selected)) {
                        val count = selected.maxStackSize - selected.count
                        if (count == 0) target.maxStackSize else count
                    } else target.maxStackSize

                val itemToInsert = target.copy().apply {
                    count = 0
                }

                itemToInsert to {
                    paths
                        .any { (slot, path, ratio) ->
                            val count = path.startVertex.predicate.count
                            val times = min(count / ratio.denominator, amount / ratio.numerator)
                            player.inventory.removeItem(slot, times * ratio.denominator)
                            val delta = times * ratio.numerator
                            itemToInsert.count += delta
                            amount -= delta
                            val enough = amount <= 0
                            if (enough) {
                                val lastEdge = path.edgeList.last()
                                player.playNotifySound(
                                    lastEdge.sound,
                                    SoundSource.BLOCKS,
                                    (player.random.nextFloat() * 0.7F + 1.0F) * 2.0f * lastEdge.volume,
                                    lastEdge.pitch
                                )
                            }
                            !enough
                        }
                    Unit
                }
            } else {
                val context = paths.firstOrNull()
                if (context == null) {
                    ItemStack.EMPTY to {}
                } else {
                    val (slot, path, ratio) = context
                    val itemToInsert = to.predicate.copy().also {
                        it.count = ratio.numerator
                    }
                    itemToInsert to {
                        player.inventory.removeItem(slot, ratio.denominator)

                        val lastEdge = path.edgeList.last()
                        player.playNotifySound(
                            lastEdge.sound,
                            SoundSource.BLOCKS,
                            (player.random.nextFloat() * 0.7F + 1.0F) * 2.0f * lastEdge.volume,
                            lastEdge.pitch
                        )
                    }
                }
            }

            insertResult(target, itemToInsert, selected, removeMaterials, player)
        }
    }.onFailure {
        ItemConverter.LOGGER.error("Error handling C2SConvertTargetPacket", it)
    }

    fun insertResult(
        target: ItemStack,
        itemToInsert: ItemStack,
        selected: ItemStack,
        removeMaterials: () -> Unit,
        player: ServerPlayer
    ) {
        val isInHand = ItemStack.isSameItemSameTags(target, selected)

        ItemConverter.serverCoroutineScope!!.launch {
            runCatching {
                if (isInHand) {
                    removeMaterials()
                    if (!player.inventory.add(player.inventory.selected, itemToInsert)) {
                        player.drop(itemToInsert, true)
                    }
                } else {
                    val existIndex = player.inventory.findSlotMatchingItem(target)
                    if (existIndex in 0..8) {
                        player.inventory.selected = existIndex
                        player.connection.send(ClientboundSetCarriedItemPacket(player.inventory.selected));
                    } else if (existIndex != -1) {
                        player.inventory.setItem(player.inventory.selected, player.inventory.getItem(existIndex))
                        player.inventory.setItem(existIndex, selected)
                    } else {
                        removeMaterials()
                        var slotToInsert = player.inventory.getSlotWithRemainingSpace(itemToInsert)
                        if (slotToInsert == -1) slotToInsert = player.inventory.freeSlot
                        if (slotToInsert in 0..8) {
                            player.inventory.selected = slotToInsert
                            player.connection.send(ClientboundSetCarriedItemPacket(player.inventory.selected));
                        }
                        if (!player.inventory.add(itemToInsert)) {
                            player.drop(itemToInsert, true)
                        }
                    }
                }
            }.onFailure { exception ->
                ItemConverter.LOGGER.error("Error inserting result", exception)
            }
        }
    }
}