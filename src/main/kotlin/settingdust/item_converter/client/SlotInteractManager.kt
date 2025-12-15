package settingdust.item_converter.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.client.gui.overlay.ForgeGui
import net.minecraftforge.client.gui.overlay.GuiOverlayManager
import net.minecraftforge.client.gui.overlay.IGuiOverlay
import net.minecraftforge.event.TickEvent
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.networking.C2SConvertTargetPacket
import settingdust.item_converter.networking.Networking
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@OnlyIn(Dist.CLIENT)
object SlotInteractManager {
    var pressedTicks = 0
    var converting = false
    val SLOT_INTERACT_KEY =
        KeyMapping("key.item_converter.slot_interact", InputConstants.KEY_CAPSLOCK, "key.categories.inventory")

    init {
        MOD_BUS.addListener { event: RegisterKeyMappingsEvent ->
            event.register(SLOT_INTERACT_KEY)
        }

        FORGE_BUS.addListener { event: InputEvent.Key ->
            if (event.key == SLOT_INTERACT_KEY.key.value) {
                when (event.action) {
                    InputConstants.PRESS -> {
                        SLOT_INTERACT_KEY.isDown = true
                        pressedTicks = 0
                    }

                    InputConstants.RELEASE -> {
                        SLOT_INTERACT_KEY.isDown = false
                        pressedTicks = 0
                        converting = false
                    }
                }
            }
        }

        FORGE_BUS.addListener { event: TickEvent.ClientTickEvent ->
            if (event.phase != TickEvent.Phase.END) return@addListener
            val minecraft = Minecraft.getInstance()
            if (minecraft.player == null) return@addListener
            val screen by lazy { minecraft.screen }
            val inventory by lazy { minecraft.player!!.inventory }
            if (SLOT_INTERACT_KEY.isDown &&
                ((screen is AbstractContainerScreen<*>
                        && (screen as AbstractContainerScreen<*>).slotUnderMouse != null
                        && (screen as AbstractContainerScreen<*>).menu.carried.isEmpty)
                        || (!inventory.getItem(inventory.selected).isEmpty && screen == null))
            ) {
                pressedTicks++
            }
        }

        FORGE_BUS.addListener { event: ScreenEvent.Opening ->
            pressedTicks = 0
        }

        FORGE_BUS.addListener { event: ScreenEvent.Closing ->
            pressedTicks = 0
        }

        MOD_BUS.addListener { event: RegisterGuiOverlaysEvent ->
            event.registerAboveAll("slot_interact_progress", SlotInteractProgress())
        }

        val progress = SlotInteractProgress()

        FORGE_BUS.addListener { event: ScreenEvent.Render.Post ->
            val screen = event.screen
            if (pressedTicks <= ClientConfig.config.pressTicks) {
                if (screen is AbstractContainerScreen<*>) {
                    val hoveredSlot = screen.slotUnderMouse
                    if (hoveredSlot != null && screen.menu.carried.isEmpty && !hoveredSlot.item.isEmpty) {
                        progress.x = screen.guiLeft + hoveredSlot.x
                        progress.y = screen.guiTop + hoveredSlot.y
                        progress.render(event.guiGraphics)
                    } else {
                        pressedTicks = 0
                    }
                }
            }
        }

        FORGE_BUS.addListener { event: RenderGuiEvent.Post ->
            val minecraft = Minecraft.getInstance()
            val screen = minecraft.screen
            if (pressedTicks > ClientConfig.config.pressTicks) {
                if (!converting) {
                    if (screen is AbstractContainerScreen<*>) {
                        minecraft.pushGuiLayer(
                            ItemConvertScreen(
                                screen,
                                screen.slotUnderMouse!!
                            )
                        )
                        converting = true
                    } else if (screen == null) {
                        val slotIndex = 36 + minecraft.player!!.inventory.selected
                        minecraft.setScreen(
                            ItemConvertScreen(
                                screen,
                                Minecraft.getInstance().player!!.inventoryMenu.getSlot(slotIndex)
                            )
                        )
                        converting = true
                        pressedTicks = 0
                    }
                }
            }
        }

        FORGE_BUS.addListener { event: InputEvent.InteractionKeyMappingTriggered ->
            when {
                event.isPickBlock -> {
                    val minecraft = Minecraft.getInstance()
                    val player = minecraft.player ?: return@addListener
                    val sneaking = player.isCrouching
                    if (player.abilities.instabuild && !sneaking) return@addListener
                    event.isCanceled = true
                    Networking.channel.sendToServer(C2SConvertTargetPacket)
                }
            }
        }
    }
}

data class SlotInteractProgress(
    var x: Int = 0,
    var y: Int = 0
) : Renderable, IGuiOverlay {
    companion object {
        const val WIDTH = 16
        const val HEIGHT = 2
        const val COLOR = 0xFFF0F0F0.toInt()
    }

    fun render(guiGraphics: GuiGraphics) {
        if (SlotInteractManager.converting) return
        val progress =
            (SlotInteractManager.pressedTicks / ClientConfig.config.pressTicks.toFloat()).coerceIn(0f, 1f)
        val width = (WIDTH * progress).toInt()
        if (width > 0) {
            guiGraphics.fill(x, y, x + width, y + HEIGHT, COLOR)
        }
    }

    override fun render(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) = render(guiGraphics)

    override fun render(
        gui: ForgeGui,
        guiGraphics: GuiGraphics,
        partialTick: Float,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (gui.minecraft.options.hideGui) return
        if (GuiOverlayManager.findOverlay(ResourceLocation("minecraft:hotbar")) == null) return
        if (gui.minecraft.screen != null) return
        val inventory = gui.minecraft.player!!.inventory
        x = screenWidth / 2 - 91 + inventory.selected * 20 + 3
        y = screenHeight - 22 + 3
        render(guiGraphics)
    }
}