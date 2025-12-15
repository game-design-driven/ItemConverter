package settingdust.item_converter

import com.google.gson.JsonElement
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries

object MoreCodecs {
    val ITEM_STACK =
        Codec.either(ItemStack.CODEC, ForgeRegistries.ITEMS.getCodec())
            .xmap({ it.map({ it }, { ItemStack(it) }) }, { Either.left(it) })

    val ITEM_PREDICATE = gsonCodec<ItemPredicate>({ it.serializeToJson() }, {
        try {
            DataResult.success(ItemPredicate.fromJson(it))
        } catch (e: Exception) {
            DataResult.error { e.message ?: "Unknown error" }
        }
    })

    fun <T> nbtCodec(encoder: (T) -> Tag, decoder: (Tag) -> DataResult<T>) =
        dynamicOpCodec(NbtOps.INSTANCE, encoder, decoder)

    fun <T> nbtCodec(encoder: (T, CompoundTag) -> CompoundTag, decoder: (T, CompoundTag) -> Unit, value: () -> T) =
        nbtCodec({
            encoder.invoke(it, CompoundTag())
        }, {
            if (it is CompoundTag) {
                val value = value.invoke()
                decoder.invoke(value, it)
                DataResult.success(value)
            } else DataResult.error { "Require compound tag" }
        })

    fun <T> gsonCodec(encoder: (T) -> JsonElement, decoder: (JsonElement) -> DataResult<T>) =
        dynamicOpCodec(JsonOps.INSTANCE, encoder, decoder)

    fun <A, T> dynamicOpCodec(ops: DynamicOps<T>, encoder: (A) -> T, decoder: (T) -> DataResult<A>) =
        Codec.PASSTHROUGH.comapFlatMap({
            decoder.invoke(it.convert(ops).value)
        }, {
            Dynamic(ops, encoder.invoke(it))
        })
}