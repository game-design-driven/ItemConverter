package settingdust.item_converter

import com.google.gson.GsonBuilder
import com.mojang.serialization.JsonOps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceKeyArgument
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import settingdust.item_converter.compat.kubejs.KubeJSCompat
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths
import org.apache.commons.lang3.math.Fraction
import org.apache.logging.log4j.LogManager
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import settingdust.item_converter.client.ItemConverterClient
import settingdust.item_converter.networking.Networking
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asSequence

@Mod(ItemConverter.ID)
object ItemConverter {
    const val ID = "item_converter"
    val LOGGER = LogManager.getLogger()
    val exportPath = FMLPaths.GAMEDIR.get() / ".item_converter_generated"
    val gson = GsonBuilder().setPrettyPrinting().create()
    var serverCoroutineDispatcher: CoroutineDispatcher? = null
    var serverCoroutineScope: CoroutineScope? = null

    init {
        MOD_BUS.register(ModEventHandler)
        FORGE_BUS.register(this)
        Networking
        if (FMLEnvironment.dist == Dist.CLIENT) ItemConverterClient
    }

    fun id(path: String) = ResourceLocation(ID, path)

    @SubscribeEvent
    internal fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal(ID).apply {
            then(Commands.literal("generate").apply {
                then(Commands.argument("generator", ResourceKeyArgument.key(RuleGenerators.KEY)).apply {
                    executes { context ->
                        val key =
                            context.getArgument("generator", ResourceKey::class.java) as ResourceKey<RuleGenerator>
                        val registry = context.source.registryAccess().registry(RuleGenerators.KEY).getOrNull()
                            ?: error("No registry ${RuleGenerators.KEY.location()}")
                        val generator = registry.get(key) ?: error("No generator ${key.location()}")
                        val result = generator.generate(context.source.level)
                        for (entry in result) {
                            val path =
                                exportPath / entry.key.location().namespace / entry.key.registry().namespace / entry.key.registry().path / "${entry.key.location().path}.json"
                            val result = ConvertRule.CODEC.encodeStart(JsonOps.INSTANCE, entry.value)
                            path.parent.createDirectories()
                            path.writeText(
                                gson.toJson(
                                    result.result().getOrNull() ?: error(
                                        result.error().get().message()
                                    )
                                )
                            )
                        }
                        context.source.sendSuccess({
                            Component.translatable(
                                "command.item_converter.generate.success",
                                result.size,
                                exportPath
                            )
                        }, true)
                        context.source.sendSuccess({
                            Component.translatable("command.item_converter.generate.restart_required")
                                .withStyle(ChatFormatting.YELLOW)
                        }, false)

                        // Offer KubeJS options if present
                        if (KubeJSCompat.isLoaded) {
                            val symlinkButton = Component.translatable("command.item_converter.kubejs.button.symlink")
                                .withStyle { style ->
                                    style
                                        .withColor(ChatFormatting.GREEN)
                                        .withUnderlined(true)
                                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/$ID symlink-kubejs"))
                                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable("command.item_converter.kubejs.button.symlink.hover")))
                                }
                            val copyButton = Component.translatable("command.item_converter.kubejs.button.copy")
                                .withStyle { style ->
                                    style
                                        .withColor(ChatFormatting.YELLOW)
                                        .withUnderlined(true)
                                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/$ID copy-kubejs"))
                                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable("command.item_converter.kubejs.button.copy.hover")))
                                }
                            val unlinkButton = Component.translatable("command.item_converter.kubejs.button.unlink")
                                .withStyle { style ->
                                    style
                                        .withColor(ChatFormatting.RED)
                                        .withUnderlined(true)
                                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/$ID unlink-kubejs"))
                                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable("command.item_converter.kubejs.button.unlink.hover")))
                                }
                            context.source.sendSuccess({
                                Component.translatable("command.item_converter.generate.kubejs_offer")
                                    .append(" ")
                                    .append(symlinkButton)
                                    .append(" ")
                                    .append(copyButton)
                                    .append(" ")
                                    .append(unlinkButton)
                            }, false)
                        }

                        result.size
                    }
                })
            })

            // Symlink to KubeJS command
            then(Commands.literal("symlink-kubejs").apply {
                executes { context ->
                    if (!KubeJSCompat.isLoaded) {
                        context.source.sendFailure(Component.translatable("command.item_converter.kubejs.not_loaded"))
                        return@executes 0
                    }

                    val (linked, errors) = KubeJSCompat.symlinkGeneratedToKubeJS()

                    for (error in errors) {
                        context.source.sendFailure(Component.literal(error))
                    }

                    if (linked > 0) {
                        context.source.sendSuccess({
                            Component.translatable("command.item_converter.symlink_kubejs.success", linked)
                        }, true)
                        context.source.sendSuccess({
                            Component.translatable("command.item_converter.kubejs.reload_hint")
                                .withStyle(ChatFormatting.YELLOW)
                        }, false)
                    } else if (errors.isEmpty()) {
                        context.source.sendFailure(Component.translatable("command.item_converter.symlink_kubejs.nothing"))
                    }

                    linked
                }
            })

            // Copy to KubeJS command
            then(Commands.literal("copy-kubejs").apply {
                executes { context ->
                    if (!KubeJSCompat.isLoaded) {
                        context.source.sendFailure(Component.translatable("command.item_converter.kubejs.not_loaded"))
                        return@executes 0
                    }

                    val (copied, errors) = KubeJSCompat.copyGeneratedToKubeJS()

                    for (error in errors) {
                        context.source.sendFailure(Component.literal(error))
                    }

                    if (copied > 0) {
                        context.source.sendSuccess({
                            Component.translatable("command.item_converter.copy_kubejs.success", copied)
                        }, true)
                        context.source.sendSuccess({
                            Component.translatable("command.item_converter.kubejs.reload_hint")
                                .withStyle(ChatFormatting.YELLOW)
                        }, false)
                    } else if (errors.isEmpty()) {
                        context.source.sendFailure(Component.translatable("command.item_converter.copy_kubejs.nothing"))
                    }

                    copied
                }
            })

            // Unlink from KubeJS command
            then(Commands.literal("unlink-kubejs").apply {
                executes { context ->
                    val existingSymlinks = KubeJSCompat.findExistingSymlinks()
                    if (existingSymlinks.isEmpty()) {
                        context.source.sendFailure(Component.translatable("command.item_converter.unlink_kubejs.nothing"))
                        return@executes 0
                    }

                    val (unlinked, errors) = KubeJSCompat.unlinkFromKubeJS()

                    for (error in errors) {
                        context.source.sendFailure(Component.literal(error))
                    }

                    if (unlinked > 0) {
                        context.source.sendSuccess({
                            Component.translatable("command.item_converter.unlink_kubejs.success", unlinked)
                        }, true)
                    }

                    unlinked
                }
            })

            // Stats command
            then(Commands.literal("stats").apply {
                executes { context ->
                    val vertices = ConvertRules.graph.vertexSet().size
                    val edges = ConvertRules.graph.edgeSet().size
                    val rules = context.source.registryAccess().registryOrThrow(ConvertRules.KEY).size()

                    context.source.sendSuccess({
                        Component.translatable("command.item_converter.stats", rules, vertices, edges)
                    }, false)

                    rules
                }
            })
        })
    }

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        refreshGraph(event.server.registryAccess())
        serverCoroutineDispatcher = event.server.asCoroutineDispatcher()
        serverCoroutineScope = CoroutineScope(serverCoroutineDispatcher!!)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        serverCoroutineDispatcher = null
        serverCoroutineScope = null
    }


    fun refreshGraph(registryAccess: RegistryAccess) {
        ConvertRules.graph = SimpleDirectedWeightedGraph<SimpleItemPredicate, FractionUnweightedEdge>(null) {
            FractionUnweightedEdge(Fraction.ZERO)
        }
        for (holder in registryAccess.registryOrThrow(ConvertRules.KEY).holders().asSequence()) {
            val rule = holder.value()
            val input = rule.input
            val inputPredicate = SimpleItemPredicate(input.copy().also { it.count = 1 })
            ConvertRules.graph.addVertex(inputPredicate)
            for (output in rule.output) {
                val outputPredicate = SimpleItemPredicate(output.copy().also { it.count = 1 })
                // Skip self-loops (same item in input and output)
                if (inputPredicate == outputPredicate) continue
                val fraction = Fraction.getReducedFraction(output.count, input.count)
                ConvertRules.graph.addVertex(outputPredicate)
                try {
                    ConvertRules.graph.addEdge(
                        inputPredicate,
                        outputPredicate,
                        FractionUnweightedEdge(fraction, rule.sound, rule.pitch, rule.volume)
                    )
                    if (CommonConfig.config.bidirectionalConversion || rule.bidirectional)
                        ConvertRules.graph.addEdge(
                            outputPredicate,
                            inputPredicate,
                            FractionUnweightedEdge(fraction.invert(), rule.sound, rule.pitch, rule.volume)
                        )
                } catch (t: Throwable) {
                    LOGGER.error("Failed to add edge for recipe $holder with input $input and output $output", t)
                }
            }
        }
    }
}