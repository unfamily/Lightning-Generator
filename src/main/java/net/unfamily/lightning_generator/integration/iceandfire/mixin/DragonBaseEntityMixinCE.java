package net.unfamily.lightning_generator.integration.iceandfire.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.LightningGeneratorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Ice and Fire CE EntityDragonBase.
 * Makes the high-power lightning rod a valid burn target for lightning dragons.
 *
 * This mixin mirrors the native updateBurnTarget flow exactly:
 *   lookAt → breathFireAtPos → setBreathingFire(true)
 * letting IaF's own code handle stimulateFire → setHasLightningTarget → TrackedData sync.
 * Energy generation is handled separately by CeFireBlockHandler (ON_DRAGON_FIRE_BLOCK event).
 */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.entity.EntityDragonBase")
public abstract class DragonBaseEntityMixinCE {

    @Shadow(remap = false) public BlockPos burningTarget;

    @Shadow(remap = false) public abstract void setBreathingFire(boolean breathing);
    @Shadow(remap = false) public abstract int getDragonStage();
    @Shadow(remap = false) public abstract boolean canPositionBeSeen(double x, double y, double z);
    @Shadow(remap = false) public abstract boolean isModelDead();
    @Shadow(remap = false) protected abstract void breathFireAtPos(BlockPos pos);

    @Inject(method = "updateBurnTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void lightningGenerator$acceptRodTarget(CallbackInfo ci) {
        if (this.burningTarget == null) return;

        try {
            if (!Class.forName("com.iafenvoy.iceandfire.entity.EntityLightningDragon")
                    .isInstance(this)) return;
        } catch (ClassNotFoundException e) {
            return;
        }

        Mob mob = (Mob) (Object) this;
        Level level = mob.level();
        if (level.isClientSide()) return;

        BlockPos pos = this.burningTarget;
        if (!level.getBlockState(pos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) return;
        if (!(level.getBlockEntity(pos.below()) instanceof LightningGeneratorBlockEntity)) return;

        // Cancel BEFORE distance/LOS checks so native code never clears burningTarget
        ci.cancel();

        if (this.isModelDead()) return;
        if (mob.isSleeping()) return;
        if (mob.isBaby()) return;

        float maxDist = 115F * this.getDragonStage();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        if (mob.distanceToSqr(cx, cy, cz) >= maxDist) return;
        if (!this.canPositionBeSeen(cx, cy, cz)) return;

        mob.getLookControl().setLookAt(cx, cy, cz, 180F, 180F);

        // Exact same calls as native updateBurnTarget for a valid forge:
        // breathFireAtPos → stimulateFire → setHasLightningTarget / setLightningTargetVec
        this.breathFireAtPos(pos);
        this.setBreathingFire(true);

        sendDragonSetBurnBlockToClientsCE(mob, pos);
    }

    @Inject(method = "isFuelingForge", at = @At("RETURN"), cancellable = true, remap = false)
    private void lightningGenerator$rodCountsAsForge(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (this.burningTarget == null) return;
        Mob mob = (Mob) (Object) this;
        if (mob.level().getBlockState(this.burningTarget).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())
                && mob.level().getBlockEntity(this.burningTarget.below()) instanceof LightningGeneratorBlockEntity) {
            cir.setReturnValue(true);
        }
    }

    private static void sendDragonSetBurnBlockToClientsCE(Mob dragon, BlockPos pos) {
        try {
            Object buf = Class.forName("com.iafenvoy.uranus.network.PacketBufferUtils")
                    .getMethod("create").invoke(null);
            buf.getClass().getMethod("writeInt", int.class).invoke(buf, dragon.getId());
            buf.getClass().getMethod("writeBoolean", boolean.class).invoke(buf, true);
            buf.getClass().getMethod("writeBlockPos", BlockPos.class).invoke(buf, pos);
            Object channel = Class.forName("com.iafenvoy.iceandfire.StaticVariables")
                    .getField("DRAGON_SET_BURN_BLOCK").get(null);
            Class<?> serverHelper = Class.forName("com.iafenvoy.uranus.ServerHelper");
            for (java.lang.reflect.Method m : serverHelper.getMethods()) {
                if ("sendToAll".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(null, channel, buf);
                    return;
                }
            }
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.warn("sendDragonSetBurnBlockToClientsCE: {}", t.getMessage());
        }
    }
}
