package net.unfamily.lightning_generator.integration.iceandfire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.unfamily.lightning_generator.Config;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.LightningGeneratorBlockEntity;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Optional integration with Ice and Fire (CE). When the mod is present:
 *
 * - ON_DRAGON_FIRE_BLOCK  (exact target coords, fired inside breathAttack):
 *   When the lightning dragon breathes toward our rod, adds RF once burnProgress >= 40.
 *   Returns false so the attack proceeds normally (visuals, particles, S2C sync).
 *
 * - ON_DRAGON_DAMAGE_BLOCK (randomised centre ±1.5 blocks, fired inside destroyAreaBreath):
 *   Cancels block destruction when our rod or generator is within ±3 blocks of the centre.
 *   This protects the whole generator setup from collateral damage.
 *
 * - Lure callback (called every tick by LightningGeneratorBlockEntity):
 *   Sets burningTarget on a nearby tamed/chained lightning dragon so the mixin can handle it.
 *
 * Uses only reflection so Ice and Fire is not required at compile time.
 */
public final class IceAndFireIntegration {

    private static final int LURE_DISTANCE = 50;

    /** Cast entity to NeoForge Level — dragon IS a Minecraft entity on NeoForge. */
    private static Level getEntityLevel(Object entity) {
        if (entity instanceof Entity e) return e.level();
        try {
            Object w = entity.getClass().getMethod("level").invoke(entity);
            if (w instanceof Level l) return l;
            w = entity.getClass().getMethod("getWorld").invoke(entity);
            return w instanceof Level l ? l : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void register() {
        try {
            Class<?> eventClass = Class.forName("com.iafenvoy.iceandfire.event.IafEvents");

            // --- ON_DRAGON_FIRE_BLOCK: exact coords → energy generation ---
            Object fireEvent = eventClass.getField("ON_DRAGON_FIRE_BLOCK").get(null);
            Class<?> fireCallbackClass = Class.forName("com.iafenvoy.iceandfire.event.IafEvents$DragonFire");
            Object fireHandler = Proxy.newProxyInstance(
                    fireCallbackClass.getClassLoader(),
                    new Class<?>[]{fireCallbackClass},
                    new FireBlockHandler()
            );
            registerEvent(fireEvent, fireHandler);

            // --- ON_DRAGON_DAMAGE_BLOCK: randomised coords → block protection ---
            Object damageEvent = eventClass.getField("ON_DRAGON_DAMAGE_BLOCK").get(null);
            Class<?> damageCallbackClass = Class.forName("com.iafenvoy.iceandfire.event.IafEvents$DragonFireDamageWorld");
            Object damageHandler = Proxy.newProxyInstance(
                    damageCallbackClass.getClassLoader(),
                    new Class<?>[]{damageCallbackClass},
                    new DamageBlockHandler()
            );
            registerEvent(damageEvent, damageHandler);

            DragonLureCallbackHolder.set(new LureImpl());
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.warn(
                    "Failed to register Ice and Fire integration: {}", t.getMessage());
        }
    }

    private static void registerEvent(Object event, Object handler) throws Throwable {
        for (Method m : event.getClass().getMethods())
            if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                m.invoke(event, handler);
                return;
            }
    }

    // -----------------------------------------------------------------------
    // ON_DRAGON_FIRE_BLOCK – fired with the EXACT target coordinates
    // -----------------------------------------------------------------------
    private static final class FireBlockHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (!"onFireBlock".equals(method.getName()) || args == null || args.length < 4)
                return false;
            try {
                Object dragon = args[0];

                Object dragonType = dragon.getClass().getField("dragonType").get(dragon);
                Object lightningType = Class.forName("com.iafenvoy.iceandfire.registry.IafDragonTypes")
                        .getField("LIGHTNING").get(null);
                if (!java.util.Objects.equals(dragonType, lightningType)) return false;

                double x = ((Number) args[1]).doubleValue();
                double y = ((Number) args[2]).doubleValue();
                double z = ((Number) args[3]).doubleValue();

                Level level = getEntityLevel(dragon);
                if (level == null || level.isClientSide()) return false;

                BlockPos rodPos = BlockPos.containing(x, y, z);
                if (!level.getBlockState(rodPos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) return false;
                if (!(level.getBlockEntity(rodPos.below()) instanceof LightningGeneratorBlockEntity gen))
                    return false;

                double burnProgress = ((Number) dragon.getClass().getField("burnProgress").get(dragon)).doubleValue();
                if (burnProgress < 40) return false;

                int rf = Config.LIGHTNING_GENERATOR_DRAGON_RF.get();
                int added = gen.receiveDragonStrikeEnergy(rf);
                if (added > 0) gen.setChanged();

                return false;
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.trace(
                        "IceAndFire FireBlockHandler: {}", t.getMessage());
                return false;
            }
        }
    }

    // -----------------------------------------------------------------------
    // ON_DRAGON_DAMAGE_BLOCK – protect rod+generator from dragon breath damage
    // -----------------------------------------------------------------------
    private static final class DamageBlockHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (!"onDamageBlock".equals(method.getName()) || args == null || args.length < 4)
                return false;
            try {
                double x = ((Number) args[1]).doubleValue();
                double y = ((Number) args[2]).doubleValue();
                double z = ((Number) args[3]).doubleValue();
                Level level = getEntityLevel(args[0]);
                if (level == null) return false;

                BlockPos center = BlockPos.containing((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

                for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
                    if (level.getBlockState(p).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())
                            && level.getBlockEntity(p.below()) instanceof LightningGeneratorBlockEntity) {
                        return true;
                    }
                }
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.trace(
                        "IceAndFire DamageBlockHandler: {}", t.getMessage());
            }
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Lure – sets burningTarget on a nearby lightning dragon every tick
    // -----------------------------------------------------------------------
    private static final class LureImpl implements DragonLureCallback {
        @Override
        public void tick(Level level, BlockPos generatorPos, BlockPos rodPos) {
            if (level.isClientSide()) return;
            try {
                Class<?> dragonClass = Class.forName("com.iafenvoy.iceandfire.entity.DragonBaseEntity");
                Object lightningType = Class.forName("com.iafenvoy.iceandfire.registry.IafDragonTypes")
                        .getField("LIGHTNING").get(null);
                AABB box = new AABB(
                        generatorPos.getX() - LURE_DISTANCE,
                        generatorPos.getY() - LURE_DISTANCE,
                        generatorPos.getZ() - LURE_DISTANCE,
                        generatorPos.getX() + LURE_DISTANCE + 1,
                        generatorPos.getY() + LURE_DISTANCE + 1,
                        generatorPos.getZ() + LURE_DISTANCE + 1
                );
                java.util.List<Entity> dragons = level.getEntities((Entity) null, box, dragonClass::isInstance);
                Vec3 targetVec = Vec3.atCenterOf(rodPos);
                boolean dragonSelected = false;
                for (Object dragon : dragons) {
                    Object dt = dragon.getClass().getField("dragonType").get(dragon);
                    if (!java.util.Objects.equals(dt, lightningType)) continue;
                    if (!(Boolean) dragon.getClass().getMethod("isChained").invoke(dragon)
                            && !(dragon instanceof net.minecraft.world.entity.TamableAnimal tamed && tamed.isTame())) continue;
                    Object bt = dragon.getClass().getField("burningTarget").get(dragon);
                    if (bt != null && isAssembledForgeAt(level, bt)) continue;
                    if (!canSeeTarget(level, (Entity) dragon, targetVec)) continue;
                    if (!dragonSelected) {
                        dragon.getClass().getField("burningTarget").set(dragon, rodPos);
                        dragonSelected = true;
                    } else {
                        if (bt != null && equalsPos(bt, rodPos)) {
                            dragon.getClass().getField("burningTarget").set(dragon, null);
                            dragon.getClass().getMethod("setBreathingFire", boolean.class).invoke(dragon, false);
                        }
                    }
                }
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.trace("IceAndFire lure: {}", t.getMessage());
            }
        }

        private static boolean canSeeTarget(Level level, Entity entity, Vec3 target) {
            Vec3 headVec = entity.getEyePosition();
            var ctx = new net.minecraft.world.level.ClipContext(
                    headVec, target,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    entity);
            var result = level.clip(ctx);
            double dist = headVec.distanceTo(result.getLocation());
            return dist < 10 + entity.getBbWidth() * 2;
        }

        private static boolean equalsPos(Object a, BlockPos b) {
            if (a == null) return false;
            if (a instanceof BlockPos bp) return bp.equals(b);
            try {
                int ax = ((Number) a.getClass().getMethod("getX").invoke(a)).intValue();
                int ay = ((Number) a.getClass().getMethod("getY").invoke(a)).intValue();
                int az = ((Number) a.getClass().getMethod("getZ").invoke(a)).intValue();
                return ax == b.getX() && ay == b.getY() && az == b.getZ();
            } catch (Throwable t) {
                return false;
            }
        }

        private static boolean isAssembledForgeAt(Level level, Object blockPos) {
            try {
                int x = ((Number) blockPos.getClass().getMethod("getX").invoke(blockPos)).intValue();
                int y = ((Number) blockPos.getClass().getMethod("getY").invoke(blockPos)).intValue();
                int z = ((Number) blockPos.getClass().getMethod("getZ").invoke(blockPos)).intValue();
                var be = level.getBlockEntity(BlockPos.containing(x, y, z));
                if (be == null) return false;
                Class<?> forgeInputClass = Class.forName(
                        "com.iafenvoy.iceandfire.item.block.entity.DragonForgeInputBlockEntity");
                if (!forgeInputClass.isInstance(be)) return false;
                return Boolean.TRUE.equals(forgeInputClass.getMethod("isAssembled").invoke(be));
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
