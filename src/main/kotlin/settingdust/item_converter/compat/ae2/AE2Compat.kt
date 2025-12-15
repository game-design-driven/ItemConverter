package settingdust.item_converter.compat.ae2

import net.minecraft.world.inventory.Slot
import net.minecraftforge.fml.ModList
import settingdust.item_converter.ItemConverter

object AE2Compat {
    val isLoaded: Boolean by lazy {
        ModList.get().isLoaded("ae2").also {
            if (it) ItemConverter.LOGGER.info("AE2 detected, enabling ME network integration")
        }
    }

    fun isRepoSlot(slot: Slot?): Boolean {
        if (!isLoaded || slot == null) return false
        return AE2SlotHelper.isRepoSlot(slot)
    }

    fun getSlotItem(slot: Slot): net.minecraft.world.item.ItemStack {
        if (!isLoaded) return slot.item
        return AE2SlotHelper.getSlotItem(slot)
    }
}
