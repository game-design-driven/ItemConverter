package settingdust.item_converter

import net.minecraft.advancements.critereon.NbtPredicate
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import org.antlr.v4.runtime.misc.Predicate

data class SimpleItemPredicate(
    val predicate: ItemStack
) : Predicate<ItemStack> {
    private val nbt = predicate.tag?.let { NbtPredicate(it) }

    // Cache serialized NBT without count for fast equals/hashCode
    private val identity: CompoundTag by lazy {
        predicate.serializeNBT().also { it.remove("Count") }
    }
    private val cachedHashCode: Int by lazy { identity.hashCode() }

    override fun test(item: ItemStack): Boolean {
        if (!item.`is`(predicate.item)) return false
        if (nbt?.matches(item) == false) return false
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherPredicate = other as? SimpleItemPredicate ?: return false
        // Fast path: different hash = definitely not equal
        if (cachedHashCode != otherPredicate.cachedHashCode) return false
        return identity == otherPredicate.identity
    }

    override fun hashCode(): Int = cachedHashCode
}
