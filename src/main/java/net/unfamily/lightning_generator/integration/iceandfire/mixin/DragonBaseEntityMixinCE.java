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
 * Mixin for Ice and Fire CE EntityDragonBase.
 * Makes the high-power lightning rod + generator count as a valid "forge" target:
 * we run the same code path as when the dragon targets BlockEntityDragonForgeInput
 * (lookAt → breathFireAtPos → setBreathingFire), so the dragon emits lightning and
 * CeFireBlockHandler handles energy. No @Shadow to avoid silent mixin discard with @Pseudo.
 */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.entity.EntityDragonBase")
public abstract class DragonBaseEntityMixinCE {

    @Inject(method = "updateBurnTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void lightningGenerator$acceptRodTarget(CallbackInfo ci) {
        try {
            Object burningTarget = this.getClass().getField("burningTarget").get(this);
            if (burningTarget == null) return;

            // Use BlockPos directly: LureCE sets our BlockPos, so no reflection on getX/getY/getZ (obfuscated at runtime)
            BlockPos pos = burningTarget instanceof BlockPos ? (BlockPos) burningTarget : null;
            if (pos == null) return;

            if (!Class.forName("com.iafenvoy.iceandfire.entity.EntityLightningDragon").isInstance(this)) return;

            Mob mob = (Mob) (Object) this;
            Level level = mob.level();
            if (level.isClientSide()) return;

            if (!level.getBlockState(pos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) return;
            if (!(level.getBlockEntity(pos.below()) instanceof LightningGeneratorBlockEntity)) return;

            ci.cancel();

            try {
                if ((Boolean) this.getClass().getMethod("isModelDead").invoke(this)) return;
            } catch (Throwable ignored) {}
            if (mob.isSleeping()) return;
            if (mob.isBaby()) return;

            int stage;
            try {
                stage = ((Number) this.getClass().getMethod("getDragonStage").invoke(this)).intValue();
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.warn("[LG-CE] getDragonStage failed: {}", t.getMessage());
                stage = 1;
            }

            float maxDist = 115F * stage;
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
            if (mob.distanceToSqr(cx, cy, cz) >= maxDist) return;

            try {
                if (!(Boolean) this.getClass()
                        .getMethod("canPositionBeSeen", double.class, double.class, double.class)
                        .invoke(this, cx, cy, cz)) return;
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.warn("[LG-CE] canPositionBeSeen failed: {}", t.getMessage());
            }

            mob.getLookControl().setLookAt(cx, cy, cz, 180F, 180F);

            // Mirror native updateBurnTarget valid-forge path: breathFireAtPos → stimulateFire → setHasLightningTarget
            // Find by name only: CE may use Yarn BlockPos in descriptor, so BlockPos.class can fail at runtime
            try {
                Method breath = findMethod(this.getClass(), "breathFireAtPos", 1);
                if (breath != null) breath.invoke(this, pos);
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.warn("[LG-CE] breathFireAtPos failed: {}", t.getMessage());
            }

            try {
                this.getClass().getMethod("setBreathingFire", boolean.class).invoke(this, true);
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.warn("[LG-CE] setBreathingFire failed: {}", t.getMessage());
            }

            sendDragonSetBurnBlockToClientsCE(mob, pos);

        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.warn("[LG-CE] updateBurnTarget mixin error: {}", t.getMessage());
        }
    }

    @Inject(method = "isFuelingForge", at = @At("RETURN"), cancellable = true, remap = false)
    private void lightningGenerator$rodCountsAsForge(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        try {
            Object raw = this.getClass().getField("burningTarget").get(this);
            if (raw == null || !(raw instanceof BlockPos)) return;
            BlockPos pos = (BlockPos) raw;
            Mob mob = (Mob) (Object) this;
            if (mob.level().getBlockState(pos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())
                    && mob.level().getBlockEntity(pos.below()) instanceof LightningGeneratorBlockEntity) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {}
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (name.equals(m.getName()) && m.getParameterCount() == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static void sendDragonSetBurnBlockToClientsCE(Mob dragon, BlockPos pos) {
        try {
            Object buf = Class.forName("com.iafenvoy.uranus.network.PacketBufferUtils")
                    .getMethod("create").invoke(null);
            buf.getClass().getMethod("writeInt", int.class).invoke(buf, dragon.getId());
            buf.getClass().getMethod("writeBoolean", boolean.class).invoke(buf, true);
            // Find writeBlockPos by name: buffer may use different BlockPos type in descriptor
            Method writeBlockPos = findMethod(buf.getClass(), "writeBlockPos", 1);
            if (writeBlockPos != null) writeBlockPos.invoke(buf, pos);
            else return;
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
            LightningGeneratorMod.LOGGER.warn("[LG-CE] sendDragonSetBurnBlock failed: {}", t.getMessage());
        }
    }
}
