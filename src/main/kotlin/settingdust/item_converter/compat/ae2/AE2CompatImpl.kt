package settingdust.item_converter.compat.ae2

import appeng.client.gui.me.common.MEStorageScreen
import appeng.client.gui.me.common.RepoSlot
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.Slot
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ScreenEvent
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.ItemConverter
import settingdust.item_converter.RecipeHelper
import settingdust.item_converter.client.SlotInteractManager
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

@OnlyIn(Dist.CLIENT)
object AE2CompatImpl : AE2CompatClient {
    private var lastOpenTime = 0L
    private const val OPEN_COOLDOWN_MS = 100L

    override fun init() {
        ItemConverter.LOGGER.info("AE2 detected, enabling ME terminal support")

        FORGE_BUS.addListener { event: ScreenEvent.Render.Post ->
            handleMEScreen(event)
        }
    }

    private fun handleMEScreen(event: ScreenEvent.Render.Post) {
        val meScreen = event.screen as? MEStorageScreen<*> ?: return
        val pressedTicks = SlotInteractManager.pressedTicks

        if (pressedTicks <= ClientConfig.config.pressTicks || SlotInteractManager.converting) return

        val now = System.currentTimeMillis()
        if (now - lastOpenTime < OPEN_COOLDOWN_MS) return
        lastOpenTime = now

        val hoveredSlot = meScreen.slotUnderMouse ?: return
        // Only handle RepoSlots (ME network items), not player inventory slots
        if (hoveredSlot !is RepoSlot) return

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

    override fun isRepoSlot(slot: Slot?): Boolean {
        return slot is RepoSlot
    }
}
