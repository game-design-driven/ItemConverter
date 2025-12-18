package settingdust.item_converter.compat.emi

import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiTexture
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

class ItemConversionEmiRecipe(
    private val source: ItemStack,
    private val target: ItemStack,
    private val id: ResourceLocation
) : EmiRecipe {

    override fun getCategory(): EmiRecipeCategory = ItemConverterEmiPlugin.CATEGORY

    override fun getId(): ResourceLocation = id

    override fun getInputs(): List<EmiIngredient> = listOf(EmiStack.of(source))

    override fun getOutputs(): List<EmiStack> = listOf(EmiStack.of(target))

    override fun getDisplayWidth(): Int = 76

    override fun getDisplayHeight(): Int = 18

    override fun addWidgets(widgets: WidgetHolder) {
        widgets.addSlot(EmiStack.of(source), 0, 0)
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 26, 1)
        widgets.addSlot(EmiStack.of(target), 58, 0).recipeContext(this)
    }
}
