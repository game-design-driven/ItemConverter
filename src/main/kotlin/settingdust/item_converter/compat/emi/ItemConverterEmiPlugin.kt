package settingdust.item_converter.compat.emi

import dev.emi.emi.api.EmiEntrypoint
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.world.item.Items
import settingdust.item_converter.ConvertRules
import settingdust.item_converter.ItemConverter
import java.util.function.Consumer

@EmiEntrypoint
class ItemConverterEmiPlugin : EmiPlugin {
    companion object {
        val CATEGORY_ID = ItemConverter.id("conversion")
        val ICON = EmiStack.of(Items.CRAFTING_TABLE)
        val CATEGORY = EmiRecipeCategory(CATEGORY_ID, ICON)
    }

    override fun register(registry: EmiRegistry) {
        registry.addCategory(CATEGORY)
        registry.addWorkstation(CATEGORY, ICON)

        // Use deferred recipes since the graph is populated on server start
        registry.addDeferredRecipes(this::registerConversionRecipes)
    }

    private fun registerConversionRecipes(consumer: Consumer<EmiRecipe>) {
        var count = 0
        for (edge in ConvertRules.graph.edgeSet()) {
            val source = ConvertRules.graph.getEdgeSource(edge)
            val target = ConvertRules.graph.getEdgeTarget(edge)
            val fraction = edge.fraction

            val sourceItem = source.predicate.item
            val targetItem = target.predicate.item

            val sourceKey = sourceItem.builtInRegistryHolder().key().location()
            val targetKey = targetItem.builtInRegistryHolder().key().location()

            val recipe = ItemConversionEmiRecipe(
                source = source.predicate.copy().apply { this.count = fraction.denominator },
                target = target.predicate.copy().apply { this.count = fraction.numerator },
                id = ItemConverter.id("conversion/${sourceKey.namespace}/${sourceKey.path}_to_${targetKey.path}")
            )
            consumer.accept(recipe)
            count++
        }

        if (count > 0) {
            ItemConverter.LOGGER.info("Registered $count conversion recipes with EMI")
        }
    }
}
