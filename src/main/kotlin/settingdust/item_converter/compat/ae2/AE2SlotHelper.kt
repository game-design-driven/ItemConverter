package settingdust.item_converter.compat.ae2

import appeng.client.gui.me.common.RepoSlot
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object AE2SlotHelper {
    fun isRepoSlot(slot: Slot): Boolean = slot is RepoSlot

    fun getSlotItem(slot: Slot): ItemStack {
        return if (slot is RepoSlot) {
            slot.item
        } else {
            slot.item
        }
    }

    fun getStoredAmount(slot: Slot): Long {
        return if (slot is RepoSlot) {
            slot.storedAmount
        } else {
            slot.item.count.toLong()
        }
    }
}
