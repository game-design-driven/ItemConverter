package settingdust.item_converter.compat.ae2

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
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
import settingdust.item_converter.client.ItemButton
import settingdust.item_converter.client.SlotInteractManager
import settingdust.item_converter.networking.C2SConvertMEItemPacket
import settingdust.item_converter.networking.ConvertAction
import settingdust.item_converter.networking.Networking

@OnlyIn(Dist.CLIENT)
class MEItemConvertScreen(
    val parent: Screen,
    val slot: Slot,
    val inputItem: ItemStack,
    val slotScreenX: Int,
    val slotScreenY: Int
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
    private val itemButtons = mutableListOf<ItemButton>()

    override fun init() {
        val recipeManager = RecipeHelper.getRecipeManager() ?: run {
            onClose()
            return
        }

        if (inputItem.isEmpty) {
            onClose()
            return
        }

        val conversions = RecipeHelper.getConversions(recipeManager, inputItem)

        if (conversions.isEmpty()) {
            onClose()
            return
        }

        slotInRow = if (conversions.size > 30) 11 else if (conversions.size > 5) 5 else conversions.size
        slotInColumn = conversions.size / slotInRow + if (conversions.size % slotInRow != 0) 1 else 0
        windowWidth = SLOT_SIZE * slotInRow + BORDER * 2
        windowHeight = SLOT_SIZE * slotInColumn + BORDER * 2

        // Center horizontally on slot
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

    private fun performBehavior(target: ItemStack, behavior: ConversionBehavior): Boolean {
        val request = behavior.toConversionRequest() ?: return false
        performConversion(target, request.count, request.action)
        return true
    }

    private fun performConversion(target: ItemStack, count: Int, action: ConvertAction) {
        Networking.channel.sendToServer(
            C2SConvertMEItemPacket(inputItem.copyWithCount(1), target.copy(), count, action)
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val popupConfig = ClientConfig.config.popup

        // Key released - convert all to hovered item (REPLACE action)
        if (!SlotInteractManager.converting) {
            getHoveredButton(mouseX.toDouble(), mouseY.toDouble())?.let { button ->
                performBehavior(button.item, popupConfig.keyReleaseBehavior)
            }
            onClose()
            return
        }
        // Close if slot item changed
        if (!ItemStack.isSameItemSameTags(slot.item, inputItem)) {
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

    override fun isPauseScreen(): Boolean = false
}
