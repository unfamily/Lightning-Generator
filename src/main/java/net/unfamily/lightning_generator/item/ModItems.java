package net.unfamily.lightning_generator.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LightningGeneratorMod.MOD_ID);

    public static final RegistryObject<BlockItem> LIGHTNING_GENERATOR = ITEMS.register("lightning_generator",
            () -> new BlockItem(ModBlocks.LIGHTNING_GENERATOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> HIGH_POWER_LIGHTNING_ROD = ITEMS.register("high_power_lightning_rod",
            () -> new BlockItem(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get(), new Item.Properties()));

    private ModItems() {}
}
