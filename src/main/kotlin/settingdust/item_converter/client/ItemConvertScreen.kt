package settingdust.item_converter.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.common.MinecraftForge
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.DrawableNineSliceTexture
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.RecipeHelper
import settingdust.item_converter.networking.C2SConvertItemPacket
import settingdust.item_converter.networking.ConvertAction
import settingdust.item_converter.networking.Networking

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
    }

    private var x = 0
    private var y = 0
    private var windowWidth = 0
    private var windowHeight = 0
    private var slotInRow = 5
    private var slotInColumn = 1
    private val input = getFrom()
    private val itemButtons = mutableListOf<ItemButton>()

    private fun getFrom() = slot.item

    override fun init() {
        val recipeManager = RecipeHelper.getRecipeManager() ?: run {
            onClose()
            return
        }

        if (input.isEmpty) {
            onClose()
            return
        }

        val conversions = RecipeHelper.getConversions(recipeManager, input)

        if (conversions.isEmpty()) {
            onClose()
            return
        }

        slotInRow = if (conversions.size > 30) 11 else if (conversions.size > 5) 5 else conversions.size
        slotInColumn = conversions.size / slotInRow + if (conversions.size % slotInRow != 0) 1 else 0
        windowWidth = SLOT_SIZE * slotInRow + BORDER * 2
        windowHeight = SLOT_SIZE * slotInColumn + BORDER * 2

        // Position window relative to slot if coordinates available
        if (slotScreenX != null && slotScreenY != null) {
            x = slotScreenX + (SLOT_SIZE / 2) - (windowWidth / 2)
            x = x.coerceIn(0, super.width - windowWidth)

            val spaceAbove = slotScreenY
            val spaceBelow = super.height - (slotScreenY + SLOT_SIZE)
            val margin = 4

            y = when {
                spaceAbove >= windowHeight + margin -> slotScreenY - windowHeight - margin
                spaceBelow >= windowHeight + margin -> slotScreenY + SLOT_SIZE + margin
                else -> (super.height - windowHeight) / 2
            }
            y = y.coerceIn(0, super.height - windowHeight)
        } else {
            x = (super.width - windowWidth) / 2
            y = (super.height - windowHeight) / 2
        }

        itemButtons.clear()
        for ((index, conversion) in conversions.withIndex()) {
            val buttonX = x + BORDER + SLOT_SIZE * (index % slotInRow)
            val buttonY = y + BORDER + SLOT_SIZE * (index / slotInRow)
            val button = ItemButton(
                item = conversion.output,
                x = buttonX,
                y = buttonY,
                width = SLOT_SIZE,
                height = SLOT_SIZE
            )
            itemButtons.add(button)
            addRenderableWidget(button)
        }
    }

    private fun getHoveredButton(mouseX: Double, mouseY: Double): ItemButton? {
        return itemButtons.firstOrNull { it.isMouseOver(mouseX, mouseY) }
    }

    private fun performConversion(target: ItemStack, count: Int, action: ConvertAction) {
        if (parent is CreativeModeInventoryScreen) {
            // Creative mode - client-side only, give items directly
            // For simplicity, just give one stack to inventory
            val player = minecraft!!.player!!
            val result = target.copy()
            result.count = if (count == -1) input.count else count
            player.inventory.add(result)
            for ((i, invSlot) in player.inventoryMenu.slots.withIndex()) {
                if (!ItemStack.isSameItemSameTags(invSlot.item, target)) continue
                minecraft!!.gameMode!!.handleCreativeModeItemAdd(invSlot.item, i)
            }
        } else {
            Networking.channel.sendToServer(
                C2SConvertItemPacket(slot.index, target, count, action)
            )
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Key released - convert all to hovered item (REPLACE action)
        if (!SlotInteractManager.converting) {
            getHoveredButton(mouseX.toDouble(), mouseY.toDouble())?.let { button ->
                performConversion(button.item, -1, ConvertAction.REPLACE)
            }
            onClose()
            return
        }
        // Close if slot item changed - key-held logic will reopen for new item
        if (!ItemStack.isSameItemSameTags(slot.item, input)) {
            SlotInteractManager.converting = false
            minecraft?.popGuiLayer()
            return
        }
        renderBackground(guiGraphics)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val hoveredButton = getHoveredButton(mouseX, mouseY) ?: return super.mouseClicked(mouseX, mouseY, button)
        val target = hoveredButton.item
        val isShift = hasShiftDown()

        when (button) {
            0 -> { // Left click - TO_INVENTORY
                val count = if (isShift) -1 else 1
                performConversion(target, count, ConvertAction.TO_INVENTORY)
            }
            1 -> { // Right click - DROP
                val count = if (isShift) -1 else 1
                performConversion(target, count, ConvertAction.DROP)
            }
        }
        return true
    }

    override fun renderBackground(guiGraphics: GuiGraphics) {
        texture.draw(guiGraphics, x, y, windowWidth, windowHeight)
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
        if (!ClientConfig.config.allowScroll) return false

        // When opened from hotbar (no parent), allow scrolling to change hotbar selection
        if (parent == null) {
            val player = minecraft?.player ?: return false
            val inventory = player.inventory
            inventory.selected = (inventory.selected - delta.toInt()).mod(9)

            val newItem = inventory.getItem(inventory.selected)
            val keyDown = InputConstants.isKeyDown(
                minecraft!!.window.window,
                SlotInteractManager.SLOT_INTERACT_KEY.key.value
            )
            val recipeManager = RecipeHelper.getRecipeManager()
            if (keyDown && recipeManager != null && RecipeHelper.hasConversions(recipeManager, newItem)) {
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
                SlotInteractManager.converting = true
            } else if (keyDown) {
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

@OnlyIn(Dist.CLIENT)
class ItemButton(
    val item: ItemStack,
    x: Int,
    y: Int,
    width: Int,
    height: Int
) : Button(x, y, width, height, Component.empty(), { }, DEFAULT_NARRATION) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.renderItem(item, x + 1, y + 1)
        if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, ClientConfig.config.highlightColor)
            if (ClientConfig.config.showTooltips) {
                renderTooltip(guiGraphics, mouseX, mouseY)
            }
        }
    }

    private fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltipLines = Screen.getTooltipFromItem(Minecraft.getInstance(), item)
        guiGraphics.renderTooltip(Minecraft.getInstance().font, tooltipLines, item.tooltipImage, mouseX, mouseY)
    }

    // Prevent default button click handling - screen handles it
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
}
