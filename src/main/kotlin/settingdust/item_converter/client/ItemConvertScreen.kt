package settingdust.item_converter.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import settingdust.item_converter.ClientConfig
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
import settingdust.item_converter.ConvertRules
import settingdust.item_converter.DrawableNineSliceTexture
import settingdust.item_converter.FractionUnweightedEdge
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.SimpleItemPredicate
import settingdust.item_converter.compat.ae2.AE2Compat
import settingdust.item_converter.networking.C2SConvertItemPacket
import settingdust.item_converter.networking.C2SConvertItemPacket.Mode
import settingdust.item_converter.networking.C2SConvertMEItemPacket
import settingdust.item_converter.networking.Networking
import java.util.concurrent.ConcurrentHashMap

@OnlyIn(Dist.CLIENT)
data class ItemConvertScreen(
    val parent: Screen?,
    val slot: Slot,
    val slotScreenX: Int? = null,
    val slotScreenY: Int? = null
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

        // Cache: source item -> list of (target, edge, ratio) for direct conversions
        private val conversionCache = ConcurrentHashMap<SimpleItemPredicate, List<Triple<SimpleItemPredicate, FractionUnweightedEdge, Fraction>>>()

        fun clearCache() {
            conversionCache.clear()
        }

        /** Get direct (1-hop) conversions from source - O(outgoing edges) */
        private fun computeDirectConversions(from: SimpleItemPredicate): List<Triple<SimpleItemPredicate, FractionUnweightedEdge, Fraction>> {
            if (from !in ConvertRules.graph.vertexSet()) return emptyList()

            return ConvertRules.graph.outgoingEdgesOf(from)
                .asSequence()
                .map { edge ->
                    val target = ConvertRules.graph.getEdgeTarget(edge)
                    Triple(target, edge, edge.fraction)
                }
                .sortedWith(
                    compareBy<Triple<SimpleItemPredicate, *, *>> { (to) ->
                        val item = to.predicate.item
                        if (item is BlockItem) item.block.javaClass.name else item.javaClass.name
                    }.thenBy { (to) ->
                        to.predicate.item.builtInRegistryHolder().key().location().toString().reversed()
                    }
                )
                .toList()
        }

        fun getDirectConversions(from: SimpleItemPredicate): List<Triple<SimpleItemPredicate, FractionUnweightedEdge, Fraction>> {
            return conversionCache.getOrPut(from) { computeDirectConversions(from) }
        }
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

        // Use cached direct conversions, filter by current stack count
        val allConversions = getDirectConversions(from)
        val targets = allConversions.filter { (_, _, ratio) -> ratio.denominator <= input.count }

        if (targets.isEmpty()) {
            onClose()
            return
        }

        slotInRow = if (targets.size > 30) 11 else if (targets.size > 5) 5 else targets.size
        slotInColumn = targets.size / slotInRow + if (targets.size % slotInRow != 0) 1 else 0
        width = SLOT_SIZE * slotInRow + BORDER * 2
        height = SLOT_SIZE * slotInColumn + BORDER * 2

        // Position window relative to slot if coordinates available
        if (slotScreenX != null && slotScreenY != null) {
            // Center horizontally on slot
            x = slotScreenX + (SLOT_SIZE / 2) - (width / 2)
            // Clamp to screen bounds horizontally
            x = x.coerceIn(0, super.width - width)

            val spaceAbove = slotScreenY
            val spaceBelow = super.height - (slotScreenY + SLOT_SIZE)
            val margin = 4

            y = when {
                // Prefer above if enough space
                spaceAbove >= height + margin -> slotScreenY - height - margin
                // Otherwise below if enough space
                spaceBelow >= height + margin -> slotScreenY + SLOT_SIZE + margin
                // Fallback to center
                else -> (super.height - height) / 2
            }
            // Clamp to screen bounds vertically
            y = y.coerceIn(0, super.height - height)
        } else {
            // Fallback to center
            x = (super.width - width) / 2
            y = (super.height - height) / 2
        }

        for ((index, pair) in targets.withIndex()) {
            val (to, edge, ratio) = pair
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
                edge = edge,
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

                        for ((i, slot) in player.inventoryMenu.slots.withIndex()) {
                            if (!ItemStack.isSameItemSameTags(slot.item, button.item)) continue
                            player.level().playSound(
                                player,
                                player.blockPosition(),
                                edge.sound,
                                SoundSource.PLAYERS,
                                (player.random.nextFloat() * 0.7F + 1.0F) * 2.0f * edge.volume,
                                edge.pitch
                            )
                            minecraft!!.gameMode!!.handleCreativeModeItemAdd(slot.item, i)
                        }
                    } else if (AE2Compat.isRepoSlot(slot)) {
                        Networking.channel.sendToServer(
                            C2SConvertMEItemPacket(
                                input,
                                target,
                                mode
                            )
                        )
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
                .firstOrNull { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        // When opened from hotbar (no parent), allow scrolling to change hotbar selection
        if (parent == null) {
            val player = minecraft?.player ?: return false
            val inventory = player.inventory
            inventory.selected = (inventory.selected - delta.toInt()).mod(9)

            // If key still held and new item has conversions, open screen for it
            val newItem = inventory.getItem(inventory.selected)
            val keyDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                minecraft!!.window.window,
                SlotInteractManager.SLOT_INTERACT_KEY.key.value
            )
            if (keyDown && SlotInteractManager.hasConversions(newItem)) {
                val slotIndex = 36 + inventory.selected
                val screenWidth = minecraft!!.window.guiScaledWidth
                val screenHeight = minecraft!!.window.guiScaledHeight
                val slotScreenX = screenWidth / 2 - 91 + inventory.selected * 20 + 3
                val slotScreenY = screenHeight - 22 + 3
                minecraft!!.setScreen(
                    ItemConvertScreen(
                        null,
                        player.inventoryMenu.getSlot(slotIndex),
                        slotScreenX,
                        slotScreenY
                    )
                )
                // Restore converting flag after setScreen (onClose resets it)
                SlotInteractManager.converting = true
            } else if (keyDown) {
                // Key still held but item has no conversions - close without resetting state
                // so screen reopens immediately when scrolling to convertible item
                minecraft!!.setScreen(null)
                SlotInteractManager.converting = false
                SlotInteractManager.pressedTicks = ClientConfig.config.pressTicks + 1
            } else {
                onClose()
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, delta)
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
    private val edge: FractionUnweightedEdge,
    onPress: OnPress
) : Button(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.renderItem(item, x + 1, y + 1)
        if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x80FFFFFF.toInt())
            renderTooltip(guiGraphics, mouseX, mouseY)
        }
    }

    private fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltipLines = buildList {
            if (ratio != Fraction.ONE)
                add(Component.literal("${ratio.denominator}:${ratio.numerator}"))
            addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), item))
        }
        guiGraphics.renderTooltip(Minecraft.getInstance().font, tooltipLines, item.tooltipImage, mouseX, mouseY)
    }
}