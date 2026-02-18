package settingdust.item_converter

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.fml.loading.FMLPaths
import settingdust.item_converter.networking.BULK_COUNT
import settingdust.item_converter.networking.ConvertAction
import java.nio.file.Path
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

private const val JSON_SCHEMA_DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema"

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaDescription(val value: String)

private fun List<Annotation>.schemaDescription(): String? {
    return filterIsInstance<SchemaDescription>().firstOrNull()?.value
}

private fun JsonObject.withDescription(description: String?): JsonObject {
    if (description.isNullOrBlank()) return this

    return buildJsonObject {
        for ((key, value) in this@withDescription) {
            put(key, value)
        }
        put("description", JsonPrimitive(description))
    }
}

private fun addRootSchemaPointerProperty(schema: JsonObject): JsonObject {
    val properties = schema["properties"] as? JsonObject ?: return schema

    return buildJsonObject {
        for ((key, value) in schema) {
            if (key != "properties") {
                put(key, value)
            }
        }

        put("properties", buildJsonObject {
            for ((key, value) in properties) {
                put(key, value)
            }

            put("\$schema", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Path to this file's JSON schema."))
            })
        })
    }
}

private fun descriptorToJsonSchema(descriptor: SerialDescriptor): JsonObject {
    val schema = when (descriptor.kind) {
        PrimitiveKind.BOOLEAN -> buildJsonObject {
            put("type", JsonPrimitive("boolean"))
        }

        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG -> buildJsonObject {
            put("type", JsonPrimitive("integer"))
        }

        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> buildJsonObject {
            put("type", JsonPrimitive("number"))
        }

        PrimitiveKind.CHAR,
        PrimitiveKind.STRING -> buildJsonObject {
            put("type", JsonPrimitive("string"))
        }

        StructureKind.LIST -> buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("items", descriptorToJsonSchema(descriptor.getElementDescriptor(0)))
        }

        StructureKind.MAP -> buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", descriptorToJsonSchema(descriptor.getElementDescriptor(1)))
        }

        StructureKind.CLASS,
        StructureKind.OBJECT -> buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("properties", buildJsonObject {
                for (index in 0 until descriptor.elementsCount) {
                    val elementDescriptor = descriptor.getElementDescriptor(index)
                    val elementSchema = descriptorToJsonSchema(elementDescriptor)
                        .withDescription(descriptor.getElementAnnotations(index).schemaDescription())

                    put(
                        descriptor.getElementName(index),
                        elementSchema
                    )
                }
            })

            val required = buildJsonArray {
                for (index in 0 until descriptor.elementsCount) {
                    if (!descriptor.isElementOptional(index)) {
                        add(JsonPrimitive(descriptor.getElementName(index)))
                    }
                }
            }

            if (required.isNotEmpty()) {
                put("required", required)
            }
        }

        SerialKind.ENUM -> buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("enum", buildJsonArray {
                for (index in 0 until descriptor.elementsCount) {
                    add(JsonPrimitive(descriptor.getElementName(index)))
                }
            })
        }

        else -> buildJsonObject { }
    }

    return schema.withDescription(descriptor.annotations.schemaDescription())
}

private fun buildSchemaDocument(title: String, serializer: KSerializer<*>): JsonObject {
    val rootSchema = addRootSchemaPointerProperty(descriptorToJsonSchema(serializer.descriptor))
    return buildJsonObject {
        put("\$schema", JsonPrimitive(JSON_SCHEMA_DRAFT_2020_12))
        put("title", JsonPrimitive(title))
        for ((key, value) in rootSchema) {
            put(key, value)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun <T> loadConfigWithSchema(
    configPath: Path,
    schemaPath: Path,
    schemaFileName: String,
    schemaTitle: String,
    serializer: KSerializer<T>
): T {
    runCatching {
        configPath.createFile()
        configPath.writeText("{}")
    }

    val schema = buildSchemaDocument(schemaTitle, serializer)
    json.encodeToStream(JsonObject.serializer(), schema, schemaPath.outputStream())

    val config = json.decodeFromStream(serializer, configPath.inputStream())
    val encoded = json.encodeToJsonElement(serializer, config).jsonObject

    val configWithSchema = buildJsonObject {
        put("\$schema", JsonPrimitive("./$schemaFileName"))
        for ((key, value) in encoded) {
            put(key, value)
        }
    }

    json.encodeToStream(JsonObject.serializer(), configWithSchema, configPath.outputStream())
    return config
}

@SchemaDescription("Defines conversion action and quantity presets used by popup interactions.")
@Serializable
enum class ConversionBehavior {
    DISABLED,
    REPLACE_ONE,
    REPLACE_BULK,
    TO_INVENTORY_ONE,
    TO_INVENTORY_BULK,
    DROP_ONE,
    DROP_BULK
}

@OnlyIn(Dist.CLIENT)
@SchemaDescription("Client-side popup interaction policy for item conversion UI.")
@Serializable
data class PopupConfig(
    @SchemaDescription("Behavior applied when releasing the hold key while popup is open.")
    val keyReleaseBehavior: ConversionBehavior = ConversionBehavior.REPLACE_BULK,
    @SchemaDescription("Behavior for left click on a conversion target.")
    val leftClickBehavior: ConversionBehavior = ConversionBehavior.TO_INVENTORY_ONE,
    @SchemaDescription("Behavior for shift + left click on a conversion target.")
    val leftClickShiftBehavior: ConversionBehavior = ConversionBehavior.TO_INVENTORY_BULK,
    @SchemaDescription("Behavior for right click on a conversion target.")
    val rightClickBehavior: ConversionBehavior = ConversionBehavior.DROP_ONE,
    @SchemaDescription("Behavior for shift + right click on a conversion target.")
    val rightClickShiftBehavior: ConversionBehavior = ConversionBehavior.DROP_BULK,
    @SchemaDescription("Behavior for number-key quick selection (1..9).")
    val numberKeyBehavior: ConversionBehavior = ConversionBehavior.REPLACE_BULK,
    @SchemaDescription("Delay in milliseconds before number-key selection applies conversion.")
    val numberKeyApplyDelayMs: Int = 100,
    @SchemaDescription("Allow mouse wheel to cycle hotbar slot while popup is open.")
    val allowScrollHotbarCycle: Boolean = true
) {
    fun numberKeyApplyDelayMsClamped(): Long = numberKeyApplyDelayMs.coerceIn(0, 2000).toLong()
}

data class ConversionRequest(val action: ConvertAction, val count: Int)

fun ConversionBehavior.toConversionRequest(): ConversionRequest? {
    return when (this) {
        ConversionBehavior.DISABLED -> null
        ConversionBehavior.REPLACE_ONE -> ConversionRequest(ConvertAction.REPLACE, 1)
        ConversionBehavior.REPLACE_BULK -> ConversionRequest(ConvertAction.REPLACE, BULK_COUNT)
        ConversionBehavior.TO_INVENTORY_ONE -> ConversionRequest(ConvertAction.TO_INVENTORY, 1)
        ConversionBehavior.TO_INVENTORY_BULK -> ConversionRequest(ConvertAction.TO_INVENTORY, BULK_COUNT)
        ConversionBehavior.DROP_ONE -> ConversionRequest(ConvertAction.DROP, 1)
        ConversionBehavior.DROP_BULK -> ConversionRequest(ConvertAction.DROP, BULK_COUNT)
    }
}

@OnlyIn(Dist.CLIENT)
@SchemaDescription("Client-side configuration for Item Converter.")
@Serializable
data class ClientConfig(
    @SchemaDescription("Ticks to hold the interaction key before the popup opens.")
    val pressTicks: Int = 0,
    @SchemaDescription("Highlight color for hovered slot in ARGB integer format.")
    val highlightColor: Int = 0x80FFFFFF.toInt(),
    @SchemaDescription("Show item tooltips when hovering conversion targets.")
    val showTooltips: Boolean = true,
    @SchemaDescription("Legacy master toggle for scroll-based hotbar cycling in popup.")
    val allowScroll: Boolean = true,
    @SchemaDescription("Enable middle-click conversion toward looked-at block target.")
    val middleClickConvert: Boolean = true,
    @SchemaDescription("Tags that are prioritized and highlighted in conversion results.")
    val specialTags: List<String> = listOf(),
    @SchemaDescription("Border color for special-tag items in ARGB integer format.")
    val specialTagBorderColor: Int = 0xFFFFD700.toInt(),
    @SchemaDescription("Popup interaction behavior configuration.")
    val popup: PopupConfig = PopupConfig()
) {
    companion object {
        private const val schemaFileName = "${ItemConverter.ID}.client.schema.json"

        private val path = FMLPaths.CONFIGDIR.get() / "${ItemConverter.ID}.client.json"
        private val schemaPath = FMLPaths.CONFIGDIR.get() / schemaFileName

        var config = ClientConfig()

        /** Runtime toggle for middle-click conversion (can be changed via command) */
        var middleClickEnabled = true

        fun reload() {
            config = loadConfigWithSchema(
                configPath = path,
                schemaPath = schemaPath,
                schemaFileName = schemaFileName,
                schemaTitle = "Item Converter Client Config",
                serializer = serializer()
            )
            // Sync runtime toggle with config on reload
            middleClickEnabled = config.middleClickConvert
        }
    }
}

@SchemaDescription("Shared gameplay configuration for Item Converter.")
@Serializable
data class CommonConfig(
    @SchemaDescription("Recipe types used as conversion sources, e.g. 'minecraft:stonecutting'.")
    val recipeTypes: List<String> = listOf("minecraft:stonecutting")
) {
    companion object {
        private const val schemaFileName = "${ItemConverter.ID}.common.schema.json"

        private val path = FMLPaths.CONFIGDIR.get() / "${ItemConverter.ID}.common.json"
        private val schemaPath = FMLPaths.CONFIGDIR.get() / schemaFileName

        var config = CommonConfig()

        fun reload() {
            config = loadConfigWithSchema(
                configPath = path,
                schemaPath = schemaPath,
                schemaFileName = schemaFileName,
                schemaTitle = "Item Converter Common Config",
                serializer = serializer()
            )
        }
    }
}
