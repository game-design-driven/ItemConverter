package settingdust.item_converter.client

import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.ItemConverter.refreshGraph
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

object ItemConverterClient {
    init {
        SlotInteractManager
        ClientConfig.Companion.reload()
        FORGE_BUS.register(this)
    }

    @SubscribeEvent
    fun onClientPlayerNetwork(event: ClientPlayerNetworkEvent.LoggingIn) {
        refreshGraph(event.player.level().registryAccess())
    }
}