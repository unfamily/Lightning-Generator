package net.unfamily.lightning_generator.block;

import net.minecraft.world.level.block.LightningRodBlock;

/**
 * Extension of the vanilla lightning rod. Same model and behaviour (attracts lightning).
 * When placed on top of the Lightning Generator, the generator also creates lightning by itself
 * when it rains (15–30s) or thunders (3–5s).
 */
public class HighPowerLightningRodBlock extends LightningRodBlock {

    public HighPowerLightningRodBlock(Properties properties) {
        super(properties);
    }
}
