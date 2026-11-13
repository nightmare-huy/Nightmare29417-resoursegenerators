package com.projectinfinity.pigenerator.blockentity;

import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.block.EnergyStorageBlock;
import com.projectinfinity.pigenerator.energy.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/**
 * Energy storage block: one block, no tiers, no capacity limit and no transfer limit.
 *
 * <p>The stored amount is a {@link BigInteger}, so it can go far beyond what the FE interface
 * ({@code int}) can express. Every side has a {@link SideMode} (kept in the block state):</p>
 * <ul>
 *   <li>input: anything may insert energy, nothing can be taken;</li>
 *   <li>output: the block pushes energy into the neighbour every tick, and cables may pull it;</li>
 *   <li>off: no energy capability on that side.</li>
 * </ul>
 */
public class EnergyStorageBlockEntity extends BlockEntity {
    /** NBT: the amount as a decimal string, e.g. {@code Energy: "12345678901234567890"}. */
    public static final String TAG_ENERGY = "Energy";

    /**
     * What an input side reports as "stored" at most: half of the reported capacity. Some cables and machines
     * only insert while {@code stored < capacity}; this way the block never looks full to them.
     */
    private static final int REPORTED_INPUT_LEVEL = Integer.MAX_VALUE / 2;
    /** Amounts up to this are sent to the screen exactly (they fit into 63 bits). */
    private static final int EXACT_DIGITS = 18;

    private BigInteger energy = BigInteger.ZERO;

    /** FE received / given out since the last tick, and during the tick before (shown by the screen). */
    private long receivedNow, sentNow, receivedLast, sentLast;

    private final IEnergyStorage inputView = new View(true, false);
    private final IEnergyStorage outputView = new View(false, true);
    /** For the {@code null} side (information tooltips of other mods): can be read, not changed. */
    private final IEnergyStorage readOnlyView = new View(false, false);

    /** Cache of the numbers sent to the screen, valid while {@link #energy} is this object. */
    @Nullable
    private BigInteger displayedEnergy;
    private long displayMantissa;
    private int displayExponent;

    public EnergyStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENERGY_STORAGE_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ energy

    public BigInteger getEnergy() {
        return energy;
    }

    public void setEnergy(BigInteger value) {
        energy = value.signum() > 0 ? value : BigInteger.ZERO;
        markDirty();
    }

    public long getReceivedPerTick() {
        return receivedLast;
    }

    public long getSentPerTick() {
        return sentLast;
    }

    public SideMode getMode(Direction side) {
        BlockState state = getBlockState();
        return state.getBlock() instanceof EnergyStorageBlock ? state.getValue(EnergyStorageBlock.property(side)) : SideMode.OFF;
    }

    /** The energy capability of a side, {@code null} for a side that is switched off. */
    @Nullable
    public IEnergyStorage getEnergyView(@Nullable Direction side) {
        if (side == null) {
            return readOnlyView;
        }
        return switch (getMode(side)) {
            case INPUT -> inputView;
            case OUTPUT -> outputView;
            case OFF -> null;
        };
    }

    private int receive(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (!simulate) {
            energy = energy.add(BigInteger.valueOf(amount));
            receivedNow = saturatedAdd(receivedNow, amount);
            markDirty();
        }
        return amount; // there is always room
    }

    private int extract(int amount, boolean simulate) {
        if (amount <= 0 || energy.signum() == 0) {
            return 0;
        }
        int extracted = energy.compareTo(BigInteger.valueOf(amount)) >= 0 ? amount : energy.intValue();
        if (!simulate) {
            energy = energy.subtract(BigInteger.valueOf(extracted));
            sentNow = saturatedAdd(sentNow, extracted);
            markDirty();
        }
        return extracted;
    }

    private static long saturatedAdd(long total, int amount) {
        return total > Long.MAX_VALUE - amount ? Long.MAX_VALUE : total + amount;
    }

    /** Stored energy as the {@code int} of the FE interface, at most {@code limit}. */
    private int reportedEnergy(int limit) {
        return energy.compareTo(BigInteger.valueOf(limit)) >= 0 ? limit : energy.intValue();
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyStorageBlockEntity storage) {
        for (Direction side : Direction.values()) {
            if (storage.energy.signum() == 0) {
                break;
            }
            if (state.getValue(EnergyStorageBlock.property(side)) != SideMode.OUTPUT) {
                continue;
            }
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(side), side.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            // no transfer limit: everything the neighbour takes in one call
            int offered = storage.reportedEnergy(Integer.MAX_VALUE);
            int accepted = Math.min(offered, target.receiveEnergy(offered, false));
            if (accepted > 0) {
                storage.energy = storage.energy.subtract(BigInteger.valueOf(accepted));
                storage.sentNow = saturatedAdd(storage.sentNow, accepted);
                storage.markDirty();
            }
        }
        storage.receivedLast = storage.receivedNow;
        storage.sentLast = storage.sentNow;
        storage.receivedNow = 0;
        storage.sentNow = 0;
    }

    // ------------------------------------------------------------------ numbers for the screen

    /**
     * The stored amount for the screen is sent as {@code mantissa * 10^exponent}: exact up to 18 digits,
     * the leading 18 digits beyond that.
     */
    public long getDisplayMantissa() {
        updateDisplay();
        return displayMantissa;
    }

    public int getDisplayExponent() {
        updateDisplay();
        return displayExponent;
    }

    private void updateDisplay() {
        if (displayedEnergy == energy) {
            return;
        }
        displayedEnergy = energy;
        if (energy.bitLength() <= 59) { // below 5.7 * 10^17: at most 18 digits
            displayMantissa = energy.longValue();
            displayExponent = 0;
            return;
        }
        int digits = energy.toString().length();
        displayExponent = Math.max(0, digits - EXACT_DIGITS);
        displayMantissa = energy.divide(BigInteger.TEN.pow(displayExponent)).longValue();
    }

    // ------------------------------------------------------------------ persistence

    /** Marks the chunk as needing a save, without the neighbour updates of {@link #setChanged()}. */
    private void markDirty() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_ENERGY, energy.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = parse(tag);
    }

    /** Reads the amount from a decimal string; plain numbers ({@code Energy: 1000}) are accepted as well. */
    private static BigInteger parse(CompoundTag tag) {
        try {
            if (tag.contains(TAG_ENERGY, Tag.TAG_STRING)) {
                BigInteger value = new BigInteger(tag.getString(TAG_ENERGY).trim());
                return value.signum() > 0 ? value : BigInteger.ZERO;
            }
            if (tag.contains(TAG_ENERGY, Tag.TAG_ANY_NUMERIC)) {
                return BigInteger.valueOf(Math.max(0L, tag.getLong(TAG_ENERGY)));
            }
        } catch (NumberFormatException ignored) {
            // broken data: start empty
        }
        return BigInteger.ZERO;
    }

    // The stored energy stays in the item when the block is broken (component "pi_generator:energy",
    // copied by the loot table) and comes back when the item is placed.

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        BigInteger stored = input.get(ModRegistry.STORED_ENERGY.get());
        if (stored != null && stored.signum() > 0) {
            energy = stored;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.signum() > 0) { // empty blocks get no component, so they stack
            components.set(ModRegistry.STORED_ENERGY.get(), energy);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove(TAG_ENERGY);
    }

    // ------------------------------------------------------------------ FE interface

    /** One of the three faces the block shows to the outside: input, output or read only. */
    private final class View implements IEnergyStorage {
        private final boolean receive;
        private final boolean extract;

        View(boolean receive, boolean extract) {
            this.receive = receive;
            this.extract = extract;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return receive ? receive(maxReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return extract ? extract(maxExtract, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return reportedEnergy(receive ? REPORTED_INPUT_LEVEL : Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return extract;
        }

        @Override
        public boolean canReceive() {
            return receive;
        }
    }
}
