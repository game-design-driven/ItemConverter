package settingdust.item_converter

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.Hash.Strategy
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenCustomHashMap
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.Container
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.IForgeRegistry
import java.util.function.Supplier

typealias RuleGeneratorType = Codec<out RuleGenerator>

object RuleGeneratorTypes {
    val KEY = ResourceKey.createRegistryKey<RuleGeneratorType>(ItemConverter.id("rule_generator_type"))

    val RECIPE = RecipeRuleGenerator.CODEC

    lateinit var REGISTRY: Supplier<IForgeRegistry<RuleGeneratorType>>
        internal set

    fun key(name: ResourceLocation) = ResourceKey.create(KEY, name)
}

object RuleGenerators {
    val KEY = ResourceKey.createRegistryKey<RuleGenerator>(ItemConverter.id("rule_generator"))

    fun key(name: ResourceLocation) = ResourceKey.create(KEY, name)
}

interface RuleGenerator {
    companion object {
        val CODEC = ExtraCodecs.lazyInitializedCodec {
            RuleGeneratorTypes.REGISTRY.get().codec.dispatch({ it.codec }, { it })
        }
    }

    val codec: Codec<out RuleGenerator>

    fun generate(level: Level): Map<ResourceKey<ConvertRule>, ConvertRule>
}

data class RecipeRuleGenerator(
    val recipeType: ResourceKey<RecipeType<*>>,
    val sound: SoundEvent,
    val pitch: Float,
    val volume: Float
) : RuleGenerator {
    companion object {
        val CODEC = RecordCodecBuilder.create<RecipeRuleGenerator> { instance ->
            instance.group(
                ResourceKey.codec(Registries.RECIPE_TYPE).fieldOf("recipe_type").forGetter { it.recipeType },
                ForgeRegistries.SOUND_EVENTS.codec.fieldOf("sound").forGetter { it.sound },
                Codec.FLOAT.fieldOf("pitch").forGetter { it.pitch },
                Codec.FLOAT.fieldOf("volume").forGetter { it.volume }
            ).apply(instance, ::RecipeRuleGenerator)
        }
    }

    val type = ForgeRegistries.RECIPE_TYPES.getDelegateOrThrow(recipeType).get()

    override val codec = CODEC

    override fun generate(level: Level): Map<ResourceKey<ConvertRule>, ConvertRule> {
        val recipeManager = level.recipeManager
        val recipes = recipeManager.getAllRecipesFor(type as RecipeType<Recipe<Container>>)
        var itemCounter = mutableMapOf<Item, Int>().withDefault { 0 }
        val inputToOutput = recipes.flatMap { recipe ->
            var ingredientCounter = Object2IntOpenCustomHashMap<Ingredient>(
                recipe.ingredients.size,
                object : Strategy<Ingredient> {
                    override fun hashCode(o: Ingredient?): Int {
                        return o?.toJson()?.hashCode() ?: 0
                    }

                    override fun equals(
                        a: Ingredient?,
                        b: Ingredient?
                    ): Boolean {
                        return a?.toJson() == b?.toJson()
                    }
                }).withDefault { 0 }
            for (ingredient in recipe.ingredients) {
                ingredientCounter[ingredient] = ingredientCounter.getValue(ingredient) + 1
                if (ingredientCounter.size != 1) return@flatMap emptySet()
            }
            val ingredient = recipe.ingredients.single()
            return@flatMap ingredient.items.map {
                it.apply { it.count = ingredientCounter.getValue(ingredient) * it.count } to recipe.getResultItem(level.registryAccess())
            }
        }
        val predicatesToOutputs =
            Object2ReferenceOpenCustomHashMap<Pair<ItemStack, ResourceKey<ConvertRule>>, MutableList<ItemStack>>(
                inputToOutput.size,
                object : Strategy<Pair<ItemStack, ResourceKey<ConvertRule>>> {
                    override fun hashCode(o: Pair<ItemStack, ResourceKey<ConvertRule>>?): Int {
                        return o?.first?.serializeNBT()?.hashCode() ?: 0
                    }

                    override fun equals(
                        a: Pair<ItemStack, ResourceKey<ConvertRule>>?,
                        b: Pair<ItemStack, ResourceKey<ConvertRule>>?
                    ): Boolean {
                        return a?.first?.serializeNBT()?.equals(b?.first?.serializeNBT()) == true
                    }
                })
        for ((input, output) in inputToOutput) {
            val itemKey = ForgeRegistries.ITEMS.getKey(input.item)!!
            predicatesToOutputs.getOrPut(
                input to ConvertRules.key(
                    ItemConverter.id(
                        "${itemKey.namespace}/${itemKey.path}_${
                            itemCounter.getValue(
                                input.item
                            )
                        }"
                    )
                )
            ) {
                itemCounter[input.item] = itemCounter.getOrDefault(input.item, -1) + 1
                mutableListOf()
            } += output
        }
        return predicatesToOutputs
            .map { entry -> entry.key.second to ConvertRule(entry.key.first, entry.value, sound, pitch, volume) }.toMap()
    }
}