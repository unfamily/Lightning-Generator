package net.unfamily.lightning_generator.integration.iceandfire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Optional callback to lure Ice and Fire lightning dragons to the lightning generator (same as dragon forge).
 * Set by {@link IceAndFireIntegration} when the mod is present.
 */
public interface DragonLureCallback {
    void tick(Level level, BlockPos generatorPos, BlockPos rodPos);
}
