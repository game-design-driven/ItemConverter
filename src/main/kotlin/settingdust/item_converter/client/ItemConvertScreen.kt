package settingdust.item_converter.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.sounds.SoundSource
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.common.MinecraftForge
import org.apache.commons.lang3.math.Fraction
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.traverse.DepthFirstIterator
import settingdust.item_converter.ConvertRules
import settingdust.item_converter.DrawableNineSliceTexture
import settingdust.item_converter.FractionUnweightedEdge
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.SimpleItemPredicate
import settingdust.item_converter.networking.C2SConvertItemPacket
import settingdust.item_converter.networking.C2SConvertItemPacket.Mode
import settingdust.item_converter.networking.Networking

@OnlyIn(Dist.CLIENT)
data class ItemConvertScreen(
    val parent: Screen?, val slot: Slot
) : Screen(Component.translatable("gui.${ItemConverter.ID}.item_convert")) {
    companion object {
        private val TEXTURE = ItemConverter.id("textures/gui/window.png")
        private const val TEXTURE_WIDTH = 128
        private const val TEXTURE_HEIGHT = 128

        private const val WIDTH = 102
        private const val HEIGHT = 30

        private const val BORDER = 6

        private const val SLOT_SIZE = 18

        val texture = DrawableNineSliceTexture(
            TEXTURE,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT,
            0,
            0,
            WIDTH,
            HEIGHT,
            BORDER,
            BORDER,
            BORDER,
            BORDER
        )
    }

    private var x = 0
    private var y = 0
    private var width = 0
    private var height = 0
    private var slotInRow = 5
    private var slotInColumn = 1
    private val input = getFrom()

    private fun getFrom() = slot.item

    override fun init() {
        val from = SimpleItemPredicate(getFrom())
        if (ConvertRules.graph.vertexSet().isEmpty() || input.isEmpty || from !in ConvertRules.graph.vertexSet()) {
            onClose()
            return
        }
        val targets =
            DepthFirstIterator(ConvertRules.graph, from)
                .asSequence()
                .mapNotNull { to ->
                    val path =
                        DijkstraShortestPath.findPathBetween(ConvertRules.graph, from, to) ?: return@mapNotNull null
                    if (path.vertexList.size == 1) return@mapNotNull null
                    val ratio = path.edgeList.fold(Fraction.ONE) { acc, edge -> edge.fraction.multiplyBy(acc) }
                    if (ratio.denominator > input.count) return@mapNotNull null
                    return@mapNotNull Triple(to, path, ratio)
                }
                .toList()
                .sortedWith(
                    compareBy<Triple<SimpleItemPredicate, *, *>> { (to) ->
                        val item = to.predicate.item
                        if (item is BlockItem) item.block.javaClass.name else item.javaClass.name
                    }.thenBy { (to) ->
                        to.predicate.item.builtInRegistryHolder().key().location().toString().reversed()
                    }
                )

        if (targets.isEmpty()) {
            onClose()
            return
        }

        slotInRow = if (targets.size > 30) 11 else if (targets.size > 5) 5 else targets.size
        slotInColumn = targets.size / slotInRow + if (targets.size % slotInRow != 0) 1 else 0
        width = SLOT_SIZE * slotInRow + BORDER * 2
        height = SLOT_SIZE * slotInColumn + BORDER * 2

        x = (super.width - width) / 2
        y = (super.height - height) / 2

        for ((index, pair) in targets.withIndex()) {
            val (to, path, ratio) = pair
            val x = x + BORDER + SLOT_SIZE * (index % slotInRow)
            val y = y + BORDER + SLOT_SIZE * (index / slotInRow)
            val button = ItemButton(
                screen = this,
                item = to.predicate.copy().apply { count = ratio.numerator },
                x = x,
                y = y,
                width = SLOT_SIZE,
                height = SLOT_SIZE,
                ratio = ratio,
                path = path,
                onPress = { btn ->
                    val mode = if (!hasShiftDown()) Mode.SINGLE_CLICK else Mode.SHIFT_CLICK
                    val button = btn as ItemButton
                    val target = button.item.copy()
                    if (parent is CreativeModeInventoryScreen) {
                        target.popTime = 5
                        val player = minecraft!!.player!!
                        when (mode) {
                            Mode.SINGLE_CLICK -> {
                                target.count = ratio.numerator
                            }

                            Mode.SHIFT_CLICK -> {
                                val times = input.count / ratio.denominator
                                val amount = ratio.denominator * times
                                target.count = amount
                            }
                        }

                        val selected = player.inventory.getItem(player.inventory.selected)
                        val isInHand = ItemStack.isSameItemSameTags(target, selected)

                        if (isInHand) {
                            player.inventory.add(player.inventory.selected, target)
                        } else {
                            val existIndex = player.inventory.findSlotMatchingItem(target)
                            if (existIndex in 0..8) {
                                player.inventory.selected = existIndex
                                player.connection.send(ServerboundSetCarriedItemPacket(player.inventory.selected));
                            } else if (existIndex != -1) {
                                player.inventory.setItem(
                                    player.inventory.selected,
                                    player.inventory.getItem(existIndex)
                                )
                                player.inventory.setItem(existIndex, selected)
                            } else {
                                if (!player.inventory.add(player.inventory.selected, target)) {
                                    player.inventory.add(target)
                                }
                            }
                        }

                        val lastEdge = path.edgeList.last()

                        for ((i, slot) in player.inventoryMenu.slots.withIndex()) {
                            if (!ItemStack.isSameItemSameTags(slot.item, button.item)) continue
                            player.level().playSound(
                                player,
                                player.blockPosition(),
                                lastEdge.sound,
                                SoundSource.PLAYERS,
                                (player.random.nextFloat() * 0.7F + 1.0F) * 2.0f * lastEdge.volume,
                                lastEdge.pitch
                            )
                            minecraft!!.gameMode!!.handleCreativeModeItemAdd(slot.item, i)
                        }
                    } else {
                        Networking.channel.sendToServer(
                            C2SConvertItemPacket(
                                slot.index,
                                target,
                                mode
                            )
                        )
                    }
                }
            )
            addRenderableWidget(button)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!SlotInteractManager.converting) {
            renderables.asSequence()
                .filterIsInstance<ItemButton>()
                .firstOrNull { it.isHoveredOrFocused }
                ?.onPress()
            onClose()
        }
        if (slot.item != input) init()
        renderBackground(guiGraphics)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun renderBackground(guiGraphics: GuiGraphics) {
        texture.draw(guiGraphics, x, y, width, height)
        MinecraftForge.EVENT_BUS.post(ScreenEvent.BackgroundRendered(this, guiGraphics))
    }

    override fun onClose() {
        super.onClose()
        SlotInteractManager.pressedTicks = 0
        SlotInteractManager.converting = false
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}

open class ItemButton(
    private val screen: Screen,
    val item: ItemStack,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val ratio: Fraction,
    private val path: org.jgrapht.GraphPath<SimpleItemPredicate, FractionUnweightedEdge>,
    onPress: OnPress
) : Button(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val minecraft = Minecraft.getInstance()
        guiGraphics.renderItem(item, x + 1, y + 1)
        if (isHoveredOrFocused) {
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x80FFFFFF.toInt())
            renderTooltip(guiGraphics, mouseX, mouseY)
        }
    }

    private fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltipLines = buildList {
            if (ratio != Fraction.ONE)
                add(Component.literal("${ratio.denominator}:${ratio.numerator}"))
            addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), item))
            if (Minecraft.getInstance().options.advancedItemTooltips) {
                add(Component.literal("Path:"))
                if (path.edgeList.isNotEmpty()) {
                    val edge = path.edgeList[0]
                    val fraction = edge.fraction
                    val sourceVertex = ConvertRules.graph.getEdgeSource(edge)
                    add(sourceVertex.predicate.displayName.copy().append(" x${fraction.denominator}"))
                }
                for (edges in path.edgeList.windowed(2, partialWindows = true)) {
                    val firstEdge = edges[0]
                    val firstFraction = firstEdge.fraction
                    val targetVertex = ConvertRules.graph.getEdgeTarget(firstEdge)
                    val secondComponent = Component.literal(">${firstFraction.numerator}x ")
                        .append(targetVertex.predicate.displayName)
                    if (edges.size == 2) {
                        val secondEdge = edges[1]
                        val secondFraction = secondEdge.fraction
                        secondComponent.append(" x${secondFraction.denominator}")
                    }
                    add(secondComponent)
                }
            }
        }
        guiGraphics.renderTooltip(Minecraft.getInstance().font, tooltipLines, item.tooltipImage, mouseX, mouseY)
    }
}