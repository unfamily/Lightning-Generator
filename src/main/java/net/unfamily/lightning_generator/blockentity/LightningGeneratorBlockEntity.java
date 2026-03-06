package net.unfamily.lightning_generator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.unfamily.lightning_generator.Config;
import net.unfamily.lightning_generator.block.LightningGeneratorBlock;
import net.unfamily.lightning_generator.block.ModBlocks;
import net.unfamily.lightning_generator.integration.iceandfire.DragonLureCallbackHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lightning generator: energy from lightning strikes (vanilla rod) or auto-lightning (high-power rod).
 * Energy extractable only from the front face.
 */
public class LightningGeneratorBlockEntity extends BlockEntity {

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_TIMER = "AutoLightningTimer";

    private final LightningGeneratorEnergyStorage energyStorage;
    private final LazyOptional<IEnergyStorage> energyCap;
    private int autoLightningTimer;
    private final Set<UUID> processedBoltIds = new HashSet<>();

    public LightningGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHTNING_GENERATOR_BE.get(), pos, state);
        int capacity = Config.LIGHTNING_GENERATOR_CAPACITY.get();
        int maxExtract = Config.LIGHTNING_GENERATOR_MAX_EXTRACT.get();
        this.energyStorage = new LightningGeneratorEnergyStorage(capacity, maxExtract);
        this.energyCap = LazyOptional.of(() -> new OutputOnlyEnergyWrapper(energyStorage));
        this.autoLightningTimer = randomInterval(Config.LIGHTNING_GENERATOR_RAIN_INTERVAL_MIN.get(), Config.LIGHTNING_GENERATOR_RAIN_INTERVAL_MAX.get());
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY
                && side == getBlockState().getValue(LightningGeneratorBlock.FACING)) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;
        BlockPos above = worldPosition.above();
        BlockState aboveState = level.getBlockState(above);

        // Any valid rod above (vanilla or high-power): detect lightning bolts at rod position and add RF
        boolean hasRod = aboveState.is(Blocks.LIGHTNING_ROD) || aboveState.is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get());
        if (hasRod) {
            int rfPerStrike = Config.LIGHTNING_GENERATOR_RF_PER_STRIKE.get();
            AABB aabb = new AABB(above).inflate(0.5);
            for (LightningBolt bolt : level.getEntities(EntityType.LIGHTNING_BOLT, aabb, e -> true)) {
                if (!processedBoltIds.contains(bolt.getUUID())) {
                    int added = energyStorage.receiveEnergyInternal(rfPerStrike);
                    if (added > 0) setChanged();
                    processedBoltIds.add(bolt.getUUID());
                }
            }
            if (processedBoltIds.size() > 100) processedBoltIds.clear();
        }

        // High-power lightning rod: lure Ice and Fire lightning dragons (same as dragon forge input)
        if (aboveState.is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) {
            var lure = DragonLureCallbackHolder.get();
            if (lure != null) lure.tick(level, worldPosition, above);
        }

        // High-power lightning rod: also create lightning on a timer when raining/thundering
        if (aboveState.is(ModBlocks.HIGH_POWER_LIGHTNING_ROD.get())) {
            boolean thunder = level.isThundering();
            boolean rain = level.isRaining();
            if (thunder || rain) {
                autoLightningTimer--;
                if (autoLightningTimer <= 0) {
                    spawnLightningAt(above);
                    int rfPerStrike = Config.LIGHTNING_GENERATOR_RF_PER_STRIKE.get();
                    int added = energyStorage.receiveEnergyInternal(rfPerStrike);
                    if (added > 0) setChanged();
                    if (thunder) {
                        autoLightningTimer = randomInterval(Config.LIGHTNING_GENERATOR_THUNDER_INTERVAL_MIN.get(), Config.LIGHTNING_GENERATOR_THUNDER_INTERVAL_MAX.get());
                    } else {
                        autoLightningTimer = randomInterval(Config.LIGHTNING_GENERATOR_RAIN_INTERVAL_MIN.get(), Config.LIGHTNING_GENERATOR_RAIN_INTERVAL_MAX.get());
                    }
                }
            } else {
                autoLightningTimer = randomInterval(Config.LIGHTNING_GENERATOR_RAIN_INTERVAL_MIN.get(), Config.LIGHTNING_GENERATOR_RAIN_INTERVAL_MAX.get());
            }
        }

        // Push energy to front face
        Direction front = getBlockState().getValue(LightningGeneratorBlock.FACING);
        BlockPos frontPos = worldPosition.relative(front);
        BlockEntity neighbor = level.getBlockEntity(frontPos);
        if (neighbor != null) {
            neighbor.getCapability(ForgeCapabilities.ENERGY, front.getOpposite()).ifPresent(es -> {
                if (es.canReceive()) {
                    int toSend = Math.min(Config.LIGHTNING_GENERATOR_MAX_EXTRACT.get(), energyStorage.getEnergyStored());
                    if (toSend > 0) {
                        int received = es.receiveEnergy(toSend, false);
                        if (received > 0) {
                            energyStorage.extractEnergy(received, false);
                            setChanged();
                        }
                    }
                }
            });
        }
    }

    private void spawnLightningAt(BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos));
                bolt.setVisualOnly(false);
                serverLevel.addFreshEntity(bolt);
            }
        }
    }

    private static int randomInterval(int min, int max) {
        if (min >= max) return min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    /** Called by Ice and Fire integration when a lightning dragon strikes the rod/generator. Returns RF actually added. */
    public int receiveDragonStrikeEnergy(int maxReceive) {
        return energyStorage.receiveEnergyInternal(maxReceive);
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_ENERGY, energyStorage.getEnergyStored());
        tag.putInt(TAG_TIMER, autoLightningTimer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt(TAG_ENERGY));
        autoLightningTimer = tag.getInt(TAG_TIMER);
        processedBoltIds.clear();
    }

    private static final class LightningGeneratorEnergyStorage extends EnergyStorage {
        LightningGeneratorEnergyStorage(int capacity, int maxExtract) {
            super(capacity, 0, maxExtract);
        }

        void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(energy, capacity));
        }

        int receiveEnergyInternal(int maxReceive) {
            int received = Math.min(capacity - energy, Math.max(0, maxReceive));
            energy += received;
            return received;
        }
    }

    private static final class OutputOnlyEnergyWrapper implements IEnergyStorage {
        private final LightningGeneratorEnergyStorage delegate;

        OutputOnlyEnergyWrapper(LightningGeneratorEnergyStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return delegate.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return delegate.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return delegate.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
