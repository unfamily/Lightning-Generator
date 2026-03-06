package net.unfamily.lightning_generator.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.lightning_generator.LightningGeneratorMod;

public class ModCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LightningGeneratorMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LIGHTNING_GENERATOR_TAB =
            CREATIVE_MODE_TABS.register("lightning_generator_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.lightning_generator"))
                            .icon(() -> new ItemStack(ModItems.LIGHTNING_GENERATOR.get()))
                            .displayItems((params, output) -> {
                                output.accept(ModItems.LIGHTNING_GENERATOR.get());
                                output.accept(ModItems.HIGH_POWER_LIGHTNING_ROD.get());
                            }).build());

    private ModCreativeModeTabs() {}
}
