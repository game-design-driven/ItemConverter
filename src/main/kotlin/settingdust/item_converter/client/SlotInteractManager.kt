package settingdust.item_converter.client

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Widget
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
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
import org.apache.commons.lang3.math.Fraction
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import settingdust.item_converter.ClientConfig
import settingdust.item_converter.ConvertRules
import settingdust.item_converter.SimpleItemPredicate
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
                        progress.render(event.poseStack)
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
                    if (minecraft.screen != null) return@addListener
                    val hitResult = minecraft.hitResult
                    if (hitResult?.type != HitResult.Type.BLOCK) return@addListener
                    val blockHitResult = hitResult as BlockHitResult
                    val level = minecraft.level ?: return@addListener
                    val target =
                        level.getBlockState(blockHitResult.blockPos)
                            .getCloneItemStack(blockHitResult, level, blockHitResult.blockPos, player)
                    if (target.isEmpty) return@addListener
                    val targetPredicate = SimpleItemPredicate(target)
                    if (targetPredicate !in ConvertRules.graph.vertexSet()) return@addListener
                    if (!canConvertTargetFromInventory(targetPredicate, target, player.inventory.items)) return@addListener
                    val sneaking = player.isCrouching
                    if (player.abilities.instabuild && !sneaking) return@addListener
                    event.isCanceled = true
                    Networking.channel.sendToServer(C2SConvertTargetPacket)
                }
            }
        }
    }

    private fun canConvertTargetFromInventory(
        targetPredicate: SimpleItemPredicate,
        target: ItemStack,
        inventoryItems: List<ItemStack>
    ): Boolean {
        return inventoryItems.asSequence()
            .filter { !it.isEmpty }
            .filter { !ItemStack.isSameItemSameTags(it, target) }
            .map { SimpleItemPredicate(it) }
            .filter { it in ConvertRules.graph.vertexSet() }
            .mapNotNull { from ->
                DijkstraShortestPath.findPathBetween(ConvertRules.graph, from, targetPredicate)
                    ?.let { path -> from to path }
            }
            .any { (from, path) ->
                val ratio = path.edgeList.fold(Fraction.ONE) { acc, edge -> edge.fraction.multiplyBy(acc) }
                from.predicate.count >= ratio.denominator
            }
    }
}

data class SlotInteractProgress(
    var x: Int = 0,
    var y: Int = 0
) : GuiComponent(), Widget, IGuiOverlay {
    companion object {
        const val WIDTH = 16
        const val HEIGHT = 2
        const val COLOR = 0xFFF0F0F0.toInt()
    }

    fun render(poseStack: PoseStack) {
        if (SlotInteractManager.converting) return
        val progress =
            (SlotInteractManager.pressedTicks / ClientConfig.config.pressTicks.toFloat()).coerceIn(0f, 1f)
        val width = (WIDTH * progress).toInt()
        if (width > 0) {
            fill(poseStack, x, y, x + width, y + HEIGHT, COLOR)
        }
    }

    override fun render(
        poseStack: PoseStack,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) = render(poseStack)

    override fun render(
        gui: ForgeGui,
        poseStack: PoseStack,
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
        render(poseStack)
    }
}
