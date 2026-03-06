package net.unfamily.lightning_generator.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.lightning_generator.LightningGeneratorMod;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LightningGeneratorMod.MOD_ID);

    public static final DeferredBlock<LightningGeneratorBlock> LIGHTNING_GENERATOR = BLOCKS.register("lightning_generator",
            () -> new LightningGeneratorBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(2.0f)));
    public static final DeferredBlock<HighPowerLightningRodBlock> HIGH_POWER_LIGHTNING_ROD = BLOCKS.register("high_power_lightning_rod",
            () -> new HighPowerLightningRodBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.COPPER)
                    .strength(2.0f)
                    .noOcclusion()));

    private ModBlocks() {}
}
