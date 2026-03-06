package net.unfamily.lightning_generator.blockentity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LightningGeneratorMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<LightningGeneratorBlockEntity>> LIGHTNING_GENERATOR_BE =
            BLOCK_ENTITY_TYPES.register("lightning_generator",
                    () -> BlockEntityType.Builder.of(LightningGeneratorBlockEntity::new, ModBlocks.LIGHTNING_GENERATOR.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }

    private ModBlockEntities() {}
}
