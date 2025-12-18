package settingdust.item_converter.compat.emi

import net.minecraftforge.fml.ModList
import settingdust.item_converter.ItemConverter

object EMICompat {
    val isLoaded: Boolean by lazy {
        ModList.get().isLoaded("emi").also {
            if (it) ItemConverter.LOGGER.info("EMI detected, enabling recipe viewer integration")
        }
    }
}
