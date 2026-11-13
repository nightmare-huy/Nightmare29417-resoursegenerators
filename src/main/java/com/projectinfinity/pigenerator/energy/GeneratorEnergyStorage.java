package com.projectinfinity.pigenerator.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Receive-only FE (Forge Energy) buffer of a generator.
 *
 * <p>There is deliberately no transfer limit: a single {@link #receiveEnergy} call may fill the whole
 * buffer, so Flux Networks points, Mekanism cables, AE2 P2P tunnels etc. are never
 * throttled by the block. Energy can not be pulled back out through the capability.</p>
 */
public class GeneratorEnergyStorage implements IEnergyStorage {
    private final int capacity;
    private final Runnable onChanged;
    private int energy;

    public GeneratorEnergyStorage(int capacity, Runnable onChanged) {
        this.capacity = capacity;
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }
        // "capacity - energy" can not overflow: 0 <= energy <= capacity <= Integer.MAX_VALUE
        int received = Math.min(capacity - energy, maxReceive);
        if (!simulate && received > 0) {
            energy += received;
            onChanged.run();
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    /** Internal use: consumes exactly {@code amount} FE, or nothing if less is stored. */
    public boolean consume(int amount) {
        if (amount > energy) {
            return false;
        }
        energy -= amount;
        return true;
    }

    /** Internal use (NBT loading): sets the stored energy, clamped to the buffer size. */
    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(capacity, energy));
    }
}
