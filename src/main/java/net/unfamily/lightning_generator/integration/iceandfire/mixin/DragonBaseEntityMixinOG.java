package net.unfamily.lightning_generator.integration.iceandfire.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.LightningGeneratorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Mixin for Ice and Fire Original (com.github.alexthe666.iceandfire).
 * Makes the high-power lightning rod a valid burn target for lightning dragons.
 *
 * Key fix: ci.cancel() is called immediately after verifying the rod is present,
 * BEFORE distance/LOS checks. This prevents the original updateBurnTarget from
 * ever clearing burningTarget, which would trigger the stopping-check and reset
 * fireTicks to 0 before burnProgress reaches 40.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.EntityDragonBase")
public abstract class DragonBaseEntityMixinOG {

    @Inject(method = "updateBurnTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void lightningGenerator$acceptRodTarget(CallbackInfo ci) {
        try {
            Object burningTarget = this.getClass().getField("burningTarget").get(this);
            if (burningTarget == null) return;

            Object dragonType = this.getClass().getField("dragonType").get(this);
            Object lightningType = Class.forName("com.github.alexthe666.iceandfire.entity.DragonType")
                    .getField("LIGHTNING").get(null);
            if (!java.util.Objects.equals(dragonType, lightningType)) return;

            Mob mob = (Mob) (Object) this;
            Level level = mob.level();
            if (level.isClientSide()) return;

            int tx = ((Number) burningTarget.getClass().getMethod("getX").invoke(burningTarget)).intValue();
            int ty = ((Number) burningTarget.getClass().getMethod("getY").invoke(burningTarget)).intValue();
            int tz = ((Number) burningTarget.getClass().getMethod("getZ").invoke(burningTarget)).intValue();
            BlockPos pos = new BlockPos(tx, ty, tz);

            if (!level.getBlockState(pos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) return;
            if (!(level.getBlockEntity(pos.below()) instanceof LightningGeneratorBlockEntity)) return;

            // Cancel BEFORE distance/LOS checks so the original never clears burningTarget.
            // If the original cleared it and fireTicks > stage*25, the stopping-check resets
            // fireTicks to 0 and setBreathingFire(false), preventing burnProgress from reaching 40.
            ci.cancel();

            // Skip breathing if dragon is in an invalid state
            try {
                boolean modelDead = (Boolean) this.getClass().getMethod("isModelDead").invoke(this);
                if (modelDead) return;
            } catch (Throwable ignored) {}
            if (mob.isSleeping()) return;

            float maxDist = 115F * ((Number) this.getClass().getMethod("getDragonStage").invoke(this)).floatValue();
            double cx = tx + 0.5, cy = ty + 0.5, cz = tz + 0.5;
            if (mob.distanceToSqr(cx, cy, cz) >= maxDist) return;

            if (!(Boolean) this.getClass()
                    .getMethod("canPositionBeSeen", double.class, double.class, double.class)
                    .invoke(this, cx, cy, cz)) return;

            mob.getLookControl().setLookAt(cx, cy, cz, 180F, 180F);

            Method breathMethod = findDeclaredMethod(this.getClass(), "breathFireAtPos", BlockPos.class);
            if (breathMethod != null) {
                breathMethod.setAccessible(true);
                breathMethod.invoke(this, pos);
            } else {
                this.getClass().getMethod("setBreathingFire", boolean.class).invoke(this, true);
            }
            // Sync burn target and breathing state to clients so they render lightning/particles.
            // IaF normally sends this in updateBurnTarget's else branch; we cancel that path.
            sendDragonSetBurnBlockToClients(mob, pos);
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.trace("DragonBaseEntityMixinOG: {}", t.getMessage());
        }
    }

    @Inject(method = "isFuelingForge", at = @At("RETURN"), cancellable = true, remap = false)
    private void lightningGenerator$rodCountsAsForge(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        try {
            Object burningTarget = this.getClass().getField("burningTarget").get(this);
            if (burningTarget == null) return;
            int tx = ((Number) burningTarget.getClass().getMethod("getX").invoke(burningTarget)).intValue();
            int ty = ((Number) burningTarget.getClass().getMethod("getY").invoke(burningTarget)).intValue();
            int tz = ((Number) burningTarget.getClass().getMethod("getZ").invoke(burningTarget)).intValue();
            Mob mob = (Mob) (Object) this;
            BlockPos pos = new BlockPos(tx, ty, tz);
            if (mob.level().getBlockState(pos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())
                    && mob.level().getBlockEntity(pos.below()) instanceof LightningGeneratorBlockEntity) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {}
    }

    private static Method findDeclaredMethod(Class<?> clazz, String name, Class<?>... params) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** Sends IaF MessageDragonSetBurnBlock so clients have burningTarget + breathingFire for rendering. */
    private static void sendDragonSetBurnBlockToClients(Mob dragon, BlockPos pos) {
        try {
            Class<?> msgClass = Class.forName("com.github.alexthe666.iceandfire.message.MessageDragonSetBurnBlock");
            Object message = msgClass.getConstructor(int.class, boolean.class, BlockPos.class)
                    .newInstance(dragon.getId(), true, pos);
            Class.forName("com.github.alexthe666.iceandfire.IceAndFire")
                    .getMethod("sendMSGToAll", Object.class)
                    .invoke(null, message);
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.trace("DragonBaseEntityMixinOG sendDragonSetBurnBlock: {}", t.getMessage());
        }
    }
}
