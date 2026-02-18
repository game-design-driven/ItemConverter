package settingdust.item_converter.compat.ae2

import net.minecraft.world.inventory.Slot
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.fml.ModList
import settingdust.item_converter.ItemConverter

@OnlyIn(Dist.CLIENT)
object AE2Compat {
    private const val IMPLEMENTATION_CLASS = "settingdust.item_converter.compat.ae2.AE2CompatImpl"

    private val implementation: AE2CompatClient? by lazy {
        if (!ModList.get().isLoaded("ae2")) return@lazy null

        runCatching {
            Class.forName(IMPLEMENTATION_CLASS)
                .getField("INSTANCE")
                .get(null) as AE2CompatClient
        }.onFailure {
            ItemConverter.LOGGER.error("Failed to load AE2 compatibility implementation", it)
        }.getOrNull()
    }

    fun init() {
        implementation?.init()
    }

    fun isRepoSlot(slot: Slot?): Boolean {
        return implementation?.isRepoSlot(slot) ?: false
    }
}

interface AE2CompatClient {
    fun init()

    fun isRepoSlot(slot: Slot?): Boolean
}
