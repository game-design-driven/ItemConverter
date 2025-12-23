package settingdust.item_converter.client

import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.compat.ae2.AE2Compat
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

object ItemConverterClient {
    init {
        SlotInteractManager
        ClientConfig.reload()
        AE2Compat.init()

        FORGE_BUS.addListener { _: ClientPlayerNetworkEvent.LoggingIn ->
            ClientConfig.reload()
        }
    }
}