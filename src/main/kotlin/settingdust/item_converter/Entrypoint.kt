package settingdust.item_converter

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import org.apache.logging.log4j.LogManager
import settingdust.item_converter.client.ItemConverterClient
import settingdust.item_converter.networking.Networking
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

@Mod(ItemConverter.ID)
object ItemConverter {
    const val ID = "item_converter"
    val LOGGER = LogManager.getLogger()

    init {
        FORGE_BUS.register(this)
        CommonConfig.reload()
        Networking
        if (FMLEnvironment.dist == Dist.CLIENT) ItemConverterClient
    }

    fun id(path: String) = ResourceLocation(ID, path)
}
