package net.unfamily.lightning_generator.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.unfamily.lightning_generator.LightningGeneratorMod;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LightningGeneratorMod.MOD_ID);

    public static final RegistryObject<LightningGeneratorBlock> LIGHTNING_GENERATOR = BLOCKS.register("lightning_generator",
            () -> new LightningGeneratorBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.METAL)
                    .strength(2.0f)));

    public static final RegistryObject<HighPowerLightningRodBlock> HIGH_POWER_LIGHTNING_ROD = BLOCKS.register("high_power_lightning_rod",
            () -> new HighPowerLightningRodBlock(BlockBehaviour.Properties.of()
                    .sound(SoundType.COPPER)
                    .strength(2.0f)
                    .noOcclusion()));

    private ModBlocks() {}
}
