package settingdust.item_converter.client

import settingdust.item_converter.ClientConfig

object ItemConverterClient {
    init {
        SlotInteractManager
        ClientConfig.Companion.reload()
    }
}