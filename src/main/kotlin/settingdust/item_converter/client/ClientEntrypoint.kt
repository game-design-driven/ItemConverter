package settingdust.item_converter.client

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RegisterClientCommandsEvent
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

        FORGE_BUS.addListener { event: RegisterClientCommandsEvent ->
            event.dispatcher.register(
                LiteralArgumentBuilder.literal<CommandSourceStack>("itemconverter")
                    .then(
                        LiteralArgumentBuilder.literal<CommandSourceStack>("middleclick")
                            .executes { ctx ->
                                ClientConfig.middleClickEnabled = !ClientConfig.middleClickEnabled
                                val status = if (ClientConfig.middleClickEnabled) "enabled" else "disabled"
                                Minecraft.getInstance().player?.displayClientMessage(
                                    Component.translatable("commands.item_converter.middleclick.$status"),
                                    false
                                )
                                1
                            }
                    )
            )
        }
    }
}