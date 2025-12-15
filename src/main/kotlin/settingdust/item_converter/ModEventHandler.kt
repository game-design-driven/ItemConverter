package settingdust.item_converter

import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.DataPackRegistryEvent
import net.minecraftforge.registries.NewRegistryEvent
import net.minecraftforge.registries.RegistryBuilder
import settingdust.item_converter.ItemConverter.id

object ModEventHandler {
    @SubscribeEvent
    internal fun onNewRegistry(event: NewRegistryEvent) {
        RuleGeneratorTypes.REGISTRY = event.create(
            RegistryBuilder<RuleGeneratorType>().setName(RuleGeneratorTypes.KEY.location()).disableSync()
        ) { it.register(id("recipe"), RuleGeneratorTypes.RECIPE) }
    }

    @SubscribeEvent
    internal fun onDataPackRegistry(event: DataPackRegistryEvent.NewRegistry) {
        event.dataPackRegistry(RuleGenerators.KEY, RuleGenerator.CODEC)
        event.dataPackRegistry(ConvertRules.KEY, ConvertRule.CODEC, ConvertRule.CODEC)
    }
}