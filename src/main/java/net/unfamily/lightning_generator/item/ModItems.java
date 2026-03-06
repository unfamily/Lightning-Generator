package net.unfamily.lightning_generator.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LightningGeneratorMod.MOD_ID);

    public static final DeferredItem<BlockItem> LIGHTNING_GENERATOR = ITEMS.register("lightning_generator",
            () -> new BlockItem(ModBlocks.LIGHTNING_GENERATOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> HIGH_POWER_LIGHTNING_ROD = ITEMS.register("high_power_lightning_rod",
            () -> new BlockItem(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get(), new Item.Properties()));

    private ModItems() {}
}
