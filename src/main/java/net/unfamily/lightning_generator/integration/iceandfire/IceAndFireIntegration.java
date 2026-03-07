package net.unfamily.lightning_generator.integration.iceandfire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.unfamily.lightning_generator.Config;
import net.unfamily.lightning_generator.LightningGeneratorMod;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.blockentity.LightningGeneratorBlockEntity;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Optional integration with Ice and Fire. Supports both:
 * - CE (com.iafenvoy.iceandfire): uses IafEvents callback system
 * - Original (com.github.alexthe666.iceandfire): uses Forge event bus
 *
 * Detection: tries CE package first; falls back to Original.
 * All cross-mod class references go through reflection so neither IaF version
 * is required at compile time (both are compileOnly).
 */
public final class IceAndFireIntegration {

    private static final int LURE_DISTANCE = 50;

    public static void register() {
        try {
            Class.forName("com.iafenvoy.iceandfire.api.IafEvents");
            registerCE();
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("com.github.alexthe666.iceandfire.entity.EntityDragonBase");
                registerOG();
            } catch (Throwable t) {
                LightningGeneratorMod.LOGGER.warn("Failed to detect Ice and Fire version: {}", t.getMessage());
            }
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.warn("Failed to register Ice and Fire (CE) integration: {}", t.getMessage());
        }
    }

    // =========================================================================
    // CE integration (com.iafenvoy.iceandfire) — IafEvents callback system
    // =========================================================================

    private static void registerCE() throws Throwable {
        Class<?> eventClass = Class.forName("com.iafenvoy.iceandfire.api.IafEvents");

        // ON_DRAGON_FIRE_BLOCK
        Object fireEvent = eventClass.getField("ON_DRAGON_FIRE_BLOCK").get(null);
        Class<?> fireCallbackClass = Class.forName("com.iafenvoy.iceandfire.api.IafEvents$DragonFire");
        Object fireHandler = Proxy.newProxyInstance(
                fireCallbackClass.getClassLoader(),
                new Class<?>[]{fireCallbackClass},
                new CeFireBlockHandler()
        );
        registerCeEvent(fireEvent, fireHandler);

        // ON_DRAGON_DAMAGE_BLOCK
        Object damageEvent = eventClass.getField("ON_DRAGON_DAMAGE_BLOCK").get(null);
        Class<?> damageCallbackClass = Class.forName("com.iafenvoy.iceandfire.api.IafEvents$DragonFireDamageWorld");
        Object damageHandler = Proxy.newProxyInstance(
                damageCallbackClass.getClassLoader(),
                new Class<?>[]{damageCallbackClass},
                new CeDamageBlockHandler()
        );
        registerCeEvent(damageEvent, damageHandler);

        DragonLureCallbackHolder.set(new LureCE());
        LightningGeneratorMod.LOGGER.info("Ice and Fire CE integration registered.");
    }

    private static void registerCeEvent(Object event, Object handler) throws Throwable {
        for (Method m : event.getClass().getMethods()) {
            if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                m.invoke(event, handler);
                return;
            }
        }
    }

    // =========================================================================
    // OG integration (com.github.alexthe666.iceandfire) — Forge event bus
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerOG() throws Throwable {
        Class<?> dragonFireEventClass =
                Class.forName("com.github.alexthe666.iceandfire.api.event.DragonFireEvent");
        Class<?> dragonDamageEventClass =
                Class.forName("com.github.alexthe666.iceandfire.api.event.DragonFireDamageWorldEvent");

        // addListener(EventPriority, boolean, Class<T>, Consumer<T>)
        Method addListener = null;
        for (Method m : MinecraftForge.EVENT_BUS.getClass().getMethods()) {
            if ("addListener".equals(m.getName()) && m.getParameterCount() == 4) {
                addListener = m;
                break;
            }
        }
        if (addListener == null) throw new NoSuchMethodException("IEventBus.addListener(priority, receiveCanceled, class, consumer)");

        Consumer<Object> fireConsumer = event -> handleOGFireEvent(event);
        Consumer<Object> damageConsumer = event -> handleOGDamageEvent(event);

        addListener.invoke(MinecraftForge.EVENT_BUS, EventPriority.NORMAL, false, dragonFireEventClass, fireConsumer);
        addListener.invoke(MinecraftForge.EVENT_BUS, EventPriority.NORMAL, false, dragonDamageEventClass, damageConsumer);

        DragonLureCallbackHolder.set(new LureOG());
        LightningGeneratorMod.LOGGER.info("Ice and Fire (Original) integration registered.");
    }

    private static void handleOGFireEvent(Object event) {
        try {
            Object dragon = event.getClass().getMethod("getDragon").invoke(event);
            if (!isOGLightningDragon(dragon)) return;

            double x = ((Number) event.getClass().getMethod("getTargetX").invoke(event)).doubleValue();
            double y = ((Number) event.getClass().getMethod("getTargetY").invoke(event)).doubleValue();
            double z = ((Number) event.getClass().getMethod("getTargetZ").invoke(event)).doubleValue();

            Level level = (Level) dragon.getClass().getMethod("level").invoke(dragon);
            if (level == null || level.isClientSide()) return;

            BlockPos rodPos = BlockPos.containing(x, y, z);
            if (!level.getBlockState(rodPos).is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) return;
            if (!(level.getBlockEntity(rodPos.below()) instanceof LightningGeneratorBlockEntity gen)) return;

            double burnProgress = ((Number) dragon.getClass().getField("burnProgress").get(dragon)).doubleValue();
            if (burnProgress < 40) return;

            int rf = Config.LIGHTNING_GENERATOR_DRAGON_RF.get();
            int added = gen.receiveDragonStrikeEnergy(rf);
            if (added > 0) gen.setChanged();
        } catch (Throwable t) {
            LightningGeneratorMod.LOGGER.trace("IaF OG FireEvent handler: {}", t.getMessage());
        }
    }

    private static void handleOGDamageEvent(Object event) {
        // Not cancelling: cancelling this event prevents destroyAreaBreath from running entirely,
        // which would block all lightning effects. The lightning dragon does not destroy our custom blocks.
    }

    // =========================================================================
    // CE inner handlers
    // =========================================================================

    private static final class CeFireBlockHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (!"onFireBlock".equals(method.getName()) || args == null || args.length < 4)
                return false;
            try {
                Object dragon = args[0];
                if (!isCeLightningDragon(dragon)) return false;

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
                LightningGeneratorMod.LOGGER.trace("IaF CE FireBlockHandler: {}", t.getMessage());
                return false;
            }
        }
    }

    private static final class CeDamageBlockHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // Not cancelling: returning true here cancels destroyAreaBreath entirely,
            // which blocks all lightning effects. The lightning dragon does not destroy our custom blocks.
            return false;
        }
    }

    // =========================================================================
    // Lure implementations
    // =========================================================================

    private static final class LureCE implements DragonLureCallback {
        @Override
        public void tick(Level level, BlockPos generatorPos, BlockPos rodPos) {
            if (level.isClientSide()) return;
            try {
                Class<?> dragonClass = Class.forName("com.iafenvoy.iceandfire.entity.EntityDragonBase");
                AABB box = makeLureBox(generatorPos);
                java.util.List<Entity> dragons = level.getEntities((Entity) null, box, dragonClass::isInstance);
                Vec3 targetVec = Vec3.atCenterOf(rodPos);
                boolean dragonSelected = false;
                for (Object dragon : dragons) {
                    if (!isCeLightningDragon(dragon)) continue;
                    if (!(Boolean) dragon.getClass().getMethod("isChained").invoke(dragon)
                            && !(dragon instanceof net.minecraft.world.entity.TamableAnimal tamed && tamed.isTame()))
                        continue;
                    Object bt = dragon.getClass().getField("burningTarget").get(dragon);
                    if (bt != null && isCeAssembledForgeAt(level, bt)) continue;
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
                LightningGeneratorMod.LOGGER.trace("IaF CE lure: {}", t.getMessage());
            }
        }
    }

    private static final class LureOG implements DragonLureCallback {
        @Override
        public void tick(Level level, BlockPos generatorPos, BlockPos rodPos) {
            if (level.isClientSide()) return;
            try {
                Class<?> dragonClass = Class.forName("com.github.alexthe666.iceandfire.entity.EntityDragonBase");
                AABB box = makeLureBox(generatorPos);
                java.util.List<Entity> dragons = level.getEntities((Entity) null, box, dragonClass::isInstance);
                Vec3 targetVec = Vec3.atCenterOf(rodPos);
                boolean dragonSelected = false;
                for (Object dragon : dragons) {
                    if (!isOGLightningDragon(dragon)) continue;
                    if (!(Boolean) dragon.getClass().getMethod("isChained").invoke(dragon)
                            && !(dragon instanceof net.minecraft.world.entity.TamableAnimal tamed && tamed.isTame()))
                        continue;
                    Object bt = dragon.getClass().getField("burningTarget").get(dragon);
                    if (bt != null && isOGAssembledForgeAt(level, bt)) continue;
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
                LightningGeneratorMod.LOGGER.trace("IaF OG lure: {}", t.getMessage());
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Level getEntityLevel(Object entity) {
        if (entity instanceof Entity e) return e.level();
        try {
            Object w = entity.getClass().getMethod("level").invoke(entity);
            if (w instanceof Level l) return l;
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isCeLightningDragon(Object dragon) {
        try {
            Object dragonType = dragon.getClass().getField("dragonType").get(dragon);
            Object lightningType = Class.forName("com.iafenvoy.iceandfire.data.DragonType")
                    .getField("LIGHTNING").get(null);
            return java.util.Objects.equals(dragonType, lightningType);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOGLightningDragon(Object dragon) {
        try {
            Object dragonType = dragon.getClass().getField("dragonType").get(dragon);
            Object lightningType = Class.forName("com.github.alexthe666.iceandfire.entity.DragonType")
                    .getField("LIGHTNING").get(null);
            return java.util.Objects.equals(dragonType, lightningType);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isCeAssembledForgeAt(Level level, Object blockPos) {
        try {
            BlockPos pos = resolveBlockPos(blockPos);
            var be = level.getBlockEntity(pos);
            if (be == null) return false;
            Class<?> forgeInputClass = Class.forName("com.iafenvoy.iceandfire.entity.block.BlockEntityDragonForgeInput");
            if (!forgeInputClass.isInstance(be)) return false;
            return Boolean.TRUE.equals(forgeInputClass.getMethod("isAssembled").invoke(be));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOGAssembledForgeAt(Level level, Object blockPos) {
        try {
            BlockPos pos = resolveBlockPos(blockPos);
            var be = level.getBlockEntity(pos);
            if (be == null) return false;
            Class<?> forgeInputClass = Class.forName("com.github.alexthe666.iceandfire.entity.tile.TileEntityDragonforgeInput");
            if (!forgeInputClass.isInstance(be)) return false;
            return Boolean.TRUE.equals(forgeInputClass.getMethod("isAssembled").invoke(be));
        } catch (Throwable t) {
            return false;
        }
    }

    private static BlockPos resolveBlockPos(Object obj) throws Throwable {
        if (obj instanceof BlockPos bp) return bp;
        int x = ((Number) obj.getClass().getMethod("getX").invoke(obj)).intValue();
        int y = ((Number) obj.getClass().getMethod("getY").invoke(obj)).intValue();
        int z = ((Number) obj.getClass().getMethod("getZ").invoke(obj)).intValue();
        return new BlockPos(x, y, z);
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

    private static AABB makeLureBox(BlockPos center) {
        return new AABB(
                center.getX() - LURE_DISTANCE, center.getY() - LURE_DISTANCE, center.getZ() - LURE_DISTANCE,
                center.getX() + LURE_DISTANCE + 1, center.getY() + LURE_DISTANCE + 1, center.getZ() + LURE_DISTANCE + 1
        );
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
}
