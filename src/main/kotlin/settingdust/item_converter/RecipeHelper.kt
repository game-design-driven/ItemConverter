package settingdust.item_converter

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.StonecutterRecipe

/**
 * Helper to query vanilla recipe registry for conversions.
 * Supports 1:N recipes (e.g., 1 stone -> 2 stone slabs).
 * Uses stonecutting recipes by default.
 */
object RecipeHelper {

    /**
     * Get the most up-to-date RecipeManager.
     * In singleplayer, uses the integrated server's RecipeManager (updated on /reload).
     * In multiplayer, uses the client's synced RecipeManager.
     */
    fun getRecipeManager(): RecipeManager? {
        val minecraft = Minecraft.getInstance()
        // Prefer integrated server's RecipeManager (always fresh after /reload)
        minecraft.singleplayerServer?.recipeManager?.let { return it }
        // Fallback to client's RecipeManager (multiplayer)
        return minecraft.level?.recipeManager
    }

    data class ConversionTarget(
        val output: ItemStack
    )

    fun getConversions(recipeManager: RecipeManager, input: ItemStack): List<ConversionTarget> {
        if (input.isEmpty) return emptyList()

        val results = mutableListOf<ConversionTarget>()
        val seenOutputs = mutableSetOf<ResourceLocation>()

        for (recipeTypeId in CommonConfig.config.recipeTypes) {
            collectConversions(recipeManager, recipeTypeId, input, results, seenOutputs)
        }

        // Sort by output item registry name for consistent ordering
        return results.sortedBy {
            it.output.item.builtInRegistryHolder().key().location().toString()
        }
    }

    private fun collectConversions(
        recipeManager: RecipeManager,
        recipeTypeId: String,
        input: ItemStack,
        results: MutableList<ConversionTarget>,
        seenOutputs: MutableSet<ResourceLocation>
    ) {
        if (recipeTypeId == "minecraft:stonecutting") {
            collectStonecutterConversions(recipeManager, input, results, seenOutputs)
        }
        // Other recipe types can be added here with explicit type handling
    }

    private fun collectStonecutterConversions(
        recipeManager: RecipeManager,
        input: ItemStack,
        results: MutableList<ConversionTarget>,
        seenOutputs: MutableSet<ResourceLocation>
    ) {
        val recipes: List<StonecutterRecipe> = recipeManager.getAllRecipesFor(RecipeType.STONECUTTING)
        for (recipe in recipes) {
            val ingredients = recipe.ingredients
            if (ingredients.size != 1) continue

            val ingredient = ingredients[0]
            val output = recipe.getResultItem(null)

            // 1:N conversion: 1 input -> N output (N = output.count)
            if (ingredient.test(input) && !output.isEmpty) {
                val outputId = output.item.builtInRegistryHolder().key().location()
                if (outputId !in seenOutputs) {
                    seenOutputs.add(outputId)
                    results.add(ConversionTarget(output.copy()))
                }
            }
        }
    }

    fun hasConversions(recipeManager: RecipeManager, input: ItemStack): Boolean {
        if (input.isEmpty) return false

        for (recipeTypeId in CommonConfig.config.recipeTypes) {
            if (recipeTypeId == "minecraft:stonecutting") {
                if (hasStonecutterConversions(recipeManager, input)) return true
            }
        }

        return false
    }

    private fun hasStonecutterConversions(recipeManager: RecipeManager, input: ItemStack): Boolean {
        val recipes: List<StonecutterRecipe> = recipeManager.getAllRecipesFor(RecipeType.STONECUTTING)
        for (recipe in recipes) {
            val ingredients = recipe.ingredients
            if (ingredients.size != 1) continue

            val ingredient = ingredients[0]
            val output = recipe.getResultItem(null)

            if (ingredient.test(input) && !output.isEmpty) {
                return true
            }
        }

        return false
    }

    fun isValidConversion(recipeManager: RecipeManager, input: ItemStack, target: ItemStack): Boolean {
        return getConversions(recipeManager, input).any {
            ItemStack.isSameItemSameTags(it.output, target)
        }
    }
}
