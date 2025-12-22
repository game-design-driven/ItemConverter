package settingdust.item_converter.client

import settingdust.item_converter.ClientConfig
import settingdust.item_converter.compat.ae2.AE2Compat

object ItemConverterClient {
    init {
        SlotInteractManager
        ClientConfig.Companion.reload()
        AE2Compat.init()
    }
}