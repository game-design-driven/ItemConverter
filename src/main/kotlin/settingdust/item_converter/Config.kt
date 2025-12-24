package settingdust.item_converter

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.fml.loading.FMLPaths
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

internal val json = Json {
    encodeDefaults = true
    prettyPrint = true
    ignoreUnknownKeys = true
}

@OnlyIn(Dist.CLIENT)
@Serializable
data class ClientConfig(
    /** Ticks to hold key before popup opens */
    val pressTicks: Int = 0,
    /** Highlight color for hovered slot (ARGB hex) */
    val highlightColor: Int = 0x80FFFFFF.toInt(),
    /** Show item tooltips on hover */
    val showTooltips: Boolean = true,
    /** Allow scrolling to change hotbar slot while popup is open */
    val allowScroll: Boolean = true,
    /** Enable middle-click to convert items to targeted block */
    val middleClickConvert: Boolean = true,
    /**
     * Tags that receive special treatment: appear first in results,
     * have highlighted border, and are preferred for middle-click conversion
     */
    val specialTags: List<String> = listOf(),
    /** Border color for special tag items (ARGB hex) */
    val specialTagBorderColor: Int = 0xFFFFD700.toInt()
) {
    companion object {
        private val path = FMLPaths.CONFIGDIR.get() / "${ItemConverter.ID}.client.json"

        var config = ClientConfig()

        /** Runtime toggle for middle-click conversion (can be changed via command) */
        var middleClickEnabled = true

        @OptIn(ExperimentalSerializationApi::class)
        fun reload() {
            runCatching {
                path.createFile()
                path.writeText("{}")
            }
            config = json.decodeFromStream(path.inputStream())
            json.encodeToStream(config, path.outputStream())
            // Sync runtime toggle with config on reload
            middleClickEnabled = config.middleClickConvert
        }
    }
}

@Serializable
data class CommonConfig(
    /** Recipe types to use for conversions (e.g. "minecraft:stonecutting") */
    val recipeTypes: List<String> = listOf("minecraft:stonecutting")
) {
    companion object {
        private val path = FMLPaths.CONFIGDIR.get() / "${ItemConverter.ID}.common.json"

        var config = CommonConfig()

        @OptIn(ExperimentalSerializationApi::class)
        fun reload() {
            runCatching {
                path.createFile()
                path.writeText("{}")
            }
            config = json.decodeFromStream(path.inputStream())
            json.encodeToStream(config, path.outputStream())
        }
    }
}