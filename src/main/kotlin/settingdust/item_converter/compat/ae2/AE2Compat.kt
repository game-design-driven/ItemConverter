package settingdust.item_converter.compat.ae2

import appeng.client.gui.me.common.MEStorageScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.fml.ModList
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.RecipeHelper
import settingdust.item_converter.client.SlotInteractManager
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

@OnlyIn(Dist.CLIENT)
object AE2Compat {
    private var lastOpenTime = 0L
    private const val OPEN_COOLDOWN_MS = 100L

    val isLoaded by lazy { ModList.get().isLoaded("ae2") }

    fun init() {
        if (!isLoaded) return

        ItemConverter.LOGGER.info("AE2 detected, enabling ME terminal support")

        FORGE_BUS.addListener { event: ScreenEvent.Render.Post ->
            handleMEScreen(event)
        }
    }

    private fun handleMEScreen(event: ScreenEvent.Render.Post) {
        val screen = event.screen
        if (!isMETerminalScreen(screen)) return

        val meScreen = screen as MEStorageScreen<*>
        val pressedTicks = SlotInteractManager.pressedTicks

        if (pressedTicks > ClientConfig.config.pressTicks && !SlotInteractManager.converting) {
            val now = System.currentTimeMillis()
            if (now - lastOpenTime < OPEN_COOLDOWN_MS) return
            lastOpenTime = now

            val hoveredSlot = meScreen.slotUnderMouse ?: return
            val item = hoveredSlot.item
            if (item.isEmpty) return

            val recipeManager = RecipeHelper.getRecipeManager() ?: return
            if (!RecipeHelper.hasConversions(recipeManager, item)) return

            val slotScreenX = meScreen.guiLeft + hoveredSlot.x
            val slotScreenY = meScreen.guiTop + hoveredSlot.y

            Minecraft.getInstance().pushGuiLayer(
                MEItemConvertScreen(
                    meScreen,
                    hoveredSlot,
                    item.copy(),
                    slotScreenX,
                    slotScreenY
                )
            )
            SlotInteractManager.converting = true
        }
    }

    fun isMETerminalScreen(screen: Screen?): Boolean {
        return screen is MEStorageScreen<*>
    }
}
