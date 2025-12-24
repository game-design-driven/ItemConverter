package settingdust.item_converter

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
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
        val output: ItemStack,
        /** Whether this item has a special tag */
        val isSpecial: Boolean = false
    )

    fun getConversions(recipeManager: RecipeManager, input: ItemStack): List<ConversionTarget> {
        if (input.isEmpty) return emptyList()

        val results = mutableListOf<ConversionTarget>()
        val seenOutputs = mutableSetOf<ResourceLocation>()

        for (recipeTypeId in CommonConfig.config.recipeTypes) {
            collectConversions(recipeManager, recipeTypeId, input, results, seenOutputs)
        }

        return sortConversions(results)
    }

    /**
     * Sort conversions:
     * 1. Special tag items first
     * 2. By tag names (alphabetically)
     * 3. By reversed registry name
     */
    private fun sortConversions(results: List<ConversionTarget>): List<ConversionTarget> {
        return results.sortedWith(
            compareBy<ConversionTarget> { !it.isSpecial } // Special items first (false < true)
                .thenBy { target ->
                    target.output.item.builtInRegistryHolder().tags()
                        .map { it.location.toString() }
                        .toList()
                        .sorted()
                        .joinToString(",")
                }
                .thenBy { target ->
                    target.output.item.builtInRegistryHolder().key().location().toString().reversed()
                }
        )
    }

    /** Check if an item has any of the configured special tags (checks both item and block tags) */
    fun hasSpecialTag(item: ItemStack): Boolean {
        val specialTags = ClientConfig.config.specialTags
        if (specialTags.isEmpty()) return false

        // Check item tags
        val itemTags = item.item.builtInRegistryHolder().tags().map { it.location.toString() }.toList().toSet()
        if (specialTags.any { it in itemTags }) return true

        // Check block tags if item is a BlockItem
        val blockItem = item.item as? net.minecraft.world.item.BlockItem ?: return false
        val blockTags = blockItem.block.builtInRegistryHolder().tags().map { it.location.toString() }.toList().toSet()
        return specialTags.any { it in blockTags }
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
                    val outputCopy = output.copy()
                    results.add(ConversionTarget(outputCopy, hasSpecialTag(outputCopy)))
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
