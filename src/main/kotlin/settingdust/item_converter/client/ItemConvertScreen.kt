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
import settingdust.item_converter.ConversionBehavior
import settingdust.item_converter.DrawableNineSliceTexture
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.RecipeHelper
import settingdust.item_converter.toConversionRequest
import settingdust.item_converter.networking.BULK_COUNT
import settingdust.item_converter.networking.C2SConvertItemPacket
import settingdust.item_converter.networking.ConvertAction
import settingdust.item_converter.networking.Networking
import settingdust.item_converter.networking.resolveBulkInputCountForSingleOutputStack

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

    // Number key selection
    private var selectedIndex: Int = -1
    private var selectionTime: Long = 0

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
                height = SLOT_SIZE,
                isSpecial = conversion.isSpecial
            )
            itemButtons.add(button)
            addRenderableWidget(button)
        }

        // Reset selection state
        selectedIndex = -1
        selectionTime = 0
    }

    private fun getHoveredButton(mouseX: Double, mouseY: Double): ItemButton? {
        return itemButtons.firstOrNull { it.isMouseOver(mouseX, mouseY) }
    }

    private fun performBehavior(target: ItemStack, behavior: ConversionBehavior): Boolean {
        val request = behavior.toConversionRequest() ?: return false
        performConversion(target, request.count, request.action)
        return true
    }

    private fun performConversion(target: ItemStack, count: Int, action: ConvertAction) {
        if (parent is CreativeModeInventoryScreen) {
            // Creative mode - client-side only, give items directly
            val player = minecraft!!.player!!
            val outputPerInput = target.count.coerceAtLeast(1)
            val inputCount = if (count == BULK_COUNT) {
                resolveBulkInputCountForSingleOutputStack(input.count, outputPerInput, target.maxStackSize)
            } else {
                minOf(count, input.count)
            }
            if (inputCount <= 0) return

            val result = target.copy()
            result.count = inputCount * outputPerInput
            if (result.count <= 0) return

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
        val popupConfig = ClientConfig.config.popup

        // Check if number key selection delay has passed
        if (selectedIndex >= 0 && selectedIndex < itemButtons.size) {
            if (System.currentTimeMillis() - selectionTime >= popupConfig.numberKeyApplyDelayMsClamped()) {
                val button = itemButtons[selectedIndex]
                performBehavior(button.item, popupConfig.numberKeyBehavior)
                onClose()
                return
            }
        }

        // Key released - convert all to hovered item (REPLACE action)
        if (!SlotInteractManager.converting) {
            getHoveredButton(mouseX.toDouble(), mouseY.toDouble())?.let { button ->
                performBehavior(button.item, popupConfig.keyReleaseBehavior)
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

        // Render number labels above window (outside)
        val font = minecraft!!.font
        val maxLabels = minOf(slotInRow, 9)
        for (i in 0 until maxLabels) {
            val label = (i + 1).toString()
            val labelX = x + BORDER + SLOT_SIZE * i + (SLOT_SIZE - font.width(label)) / 2
            val labelY = y - font.lineHeight - 2
            guiGraphics.drawString(font, label, labelX, labelY, 0xFFFFFF, true)
        }

        // Render selection highlight
        if (selectedIndex >= 0 && selectedIndex < itemButtons.size) {
            val button = itemButtons[selectedIndex]
            guiGraphics.fill(button.x + 1, button.y + 1, button.x + button.width - 1, button.y + button.height - 1, 0x80FFFF00.toInt())
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (ClientConfig.config.popup.numberKeyBehavior == ConversionBehavior.DISABLED) {
            return super.keyPressed(keyCode, scanCode, modifiers)
        }

        // Check for number keys 1-9
        val numberIndex = when (keyCode) {
            InputConstants.KEY_1 -> 0
            InputConstants.KEY_2 -> 1
            InputConstants.KEY_3 -> 2
            InputConstants.KEY_4 -> 3
            InputConstants.KEY_5 -> 4
            InputConstants.KEY_6 -> 5
            InputConstants.KEY_7 -> 6
            InputConstants.KEY_8 -> 7
            InputConstants.KEY_9 -> 8
            else -> -1
        }

        if (numberIndex >= 0 && numberIndex < itemButtons.size) {
            selectedIndex = numberIndex
            selectionTime = System.currentTimeMillis()
            return true
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val hoveredButton = getHoveredButton(mouseX, mouseY) ?: return super.mouseClicked(mouseX, mouseY, button)
        val target = hoveredButton.item
        val isShift = hasShiftDown()
        val popupConfig = ClientConfig.config.popup

        val behavior = when (button) {
            0 -> if (isShift) popupConfig.leftClickShiftBehavior else popupConfig.leftClickBehavior
            1 -> if (isShift) popupConfig.rightClickShiftBehavior else popupConfig.rightClickBehavior
            else -> return super.mouseClicked(mouseX, mouseY, button)
        }

        performBehavior(target, behavior)
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
        if (!ClientConfig.config.allowScroll || !ClientConfig.config.popup.allowScrollHotbarCycle) return false

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
    height: Int,
    val isSpecial: Boolean = false
) : Button(x, y, width, height, Component.empty(), { }, DEFAULT_NARRATION) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Draw special tag border
        if (isSpecial) {
            val borderColor = ClientConfig.config.specialTagBorderColor
            // Top
            guiGraphics.fill(x, y, x + width, y + 1, borderColor)
            // Bottom
            guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor)
            // Left
            guiGraphics.fill(x, y, x + 1, y + height, borderColor)
            // Right
            guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor)
        }

        guiGraphics.renderItem(item, x + 1, y + 1)
        if (item.count > 1) {
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, item, x + 1, y + 1)
        }
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
