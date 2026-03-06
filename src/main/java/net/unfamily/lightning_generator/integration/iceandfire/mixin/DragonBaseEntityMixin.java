package net.unfamily.lightning_generator.integration.iceandfire.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.LightningGeneratorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Makes the high-power lightning rod + generator a valid burn target for lightning dragons,
 * identical in behaviour to DragonForgeInputBlockEntity.
 */
@Mixin(targets = "com.iafenvoy.iceandfire.entity.DragonBaseEntity")
public abstract class DragonBaseEntityMixin {

    @Inject(method = "updateBurnTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void lightningGenerator$acceptRodTarget(CallbackInfo ci) {
        try {
            Object burningTarget = this.getClass().getField("burningTarget").get(this);
            if (burningTarget == null) return;

            Object dragonType = this.getClass().getField("dragonType").get(this);
            Object lightningType = Class.forName("com.iafenvoy.iceandfire.registry.IafDragonTypes")
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

            try {
                Class<?> payloadClass = Class.forName(
                        "com.iafenvoy.iceandfire.network.payload.DragonSetBurnBlockS2CPayload");
                Object payload = payloadClass
                        .getConstructor(int.class, boolean.class, BlockPos.class)
                        .newInstance(mob.getId(), true, pos);
                Class<?> helper = Class.forName("com.iafenvoy.uranus.ServerHelper");
                for (Method m : helper.getMethods())
                    if ("sendToAll".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.invoke(null, payload);
                        break;
                    }
            } catch (Throwable t2) {
                LightningGeneratorMod.LOGGER.trace("DragonBaseEntityMixin S2C: {}", t2.getMessage());
            }

            ci.cancel();
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.trace("DragonBaseEntityMixin: {}", t.getMessage());
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
        } catch (Throwable ignored) {
        }
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
}
