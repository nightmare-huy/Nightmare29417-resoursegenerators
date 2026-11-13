package com.projectinfinity.pigenerator.menu;

import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.block.EnergyStorageBlock;
import com.projectinfinity.pigenerator.blockentity.EnergyStorageBlockEntity;
import com.projectinfinity.pigenerator.energy.SideMode;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/**
 * Screen of the energy storage block. It has no slots: only numbers (stored energy, energy received and
 * given out per tick, the mode of each side) and six buttons that switch the sides.
 */
public class EnergyStorageMenu extends AbstractContainerMenu {
    // Synchronised numbers. The vanilla packet carries 16 bits per value, so longs are sent in four parts.
    private static final int DATA_MANTISSA = 0;     // 4 values: stored energy = mantissa * 10^exponent
    private static final int DATA_EXPONENT = 4;
    private static final int DATA_RECEIVED = 5;     // 4 values
    private static final int DATA_SENT = 9;         // 4 values
    private static final int DATA_SIDES = 13;       // 2 bits per side, in the order of Direction.values()
    private static final int DATA_COUNT = 14;

    private static final Direction[] SIDES = Direction.values();

    private final ContainerData data;
    /** {@code null} on the client. */
    @Nullable
    private final EnergyStorageBlockEntity storage;

    /** Client side constructor, see {@code EnergyStorageBlock#useWithoutItem}. */
    public EnergyStorageMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, new SimpleContainerData(DATA_COUNT), null);
    }

    public EnergyStorageMenu(int containerId, Inventory inventory, EnergyStorageBlockEntity storage) {
        this(containerId, new StorageData(storage), storage);
    }

    private EnergyStorageMenu(int containerId, ContainerData data, @Nullable EnergyStorageBlockEntity storage) {
        super(ModRegistry.ENERGY_STORAGE_MENU.get(), containerId);
        this.data = data;
        this.storage = storage;
        addDataSlots(data);
    }

    // ------------------------------------------------------------------ numbers for the screen

    private long getLong(int first) {
        long value = 0;
        for (int part = 3; part >= 0; part--) {
            value = (value << 16) | (data.get(first + part) & 0xFFFF);
        }
        return value;
    }

    /** Stored energy: exact up to 18 digits, the leading 18 digits followed by zeros beyond that. */
    public BigInteger getEnergy() {
        BigInteger mantissa = BigInteger.valueOf(Math.max(0L, getLong(DATA_MANTISSA)));
        int exponent = data.get(DATA_EXPONENT) & 0xFFFF;
        return exponent == 0 ? mantissa : mantissa.multiply(BigInteger.TEN.pow(exponent));
    }

    /** FE received during the last tick. */
    public long getReceivedPerTick() {
        return Math.max(0L, getLong(DATA_RECEIVED));
    }

    /** FE given out during the last tick. */
    public long getSentPerTick() {
        return Math.max(0L, getLong(DATA_SENT));
    }

    public SideMode getMode(Direction side) {
        return SideMode.byId((data.get(DATA_SIDES) >> (side.ordinal() * 2)) & 3);
    }

    // ------------------------------------------------------------------ container logic

    /** Buttons 0..5: switch the mode of the side with that index in {@code Direction.values()}. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= SIDES.length) {
            return false;
        }
        if (storage != null && storage.getLevel() != null) {
            EnergyStorageBlock.cycleSide(storage.getLevel(), storage.getBlockPos(), SIDES[id]);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (storage == null) {
            return true;
        }
        return !storage.isRemoved()
                && player.distanceToSqr(storage.getBlockPos().getX() + 0.5, storage.getBlockPos().getY() + 0.5,
                storage.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // no slots
    }

    /** Server side source of the synchronised numbers. */
    private record StorageData(EnergyStorageBlockEntity storage) implements ContainerData {
        @Override
        public int get(int index) {
            if (index >= DATA_MANTISSA && index < DATA_MANTISSA + 4) {
                return part(storage.getDisplayMantissa(), index - DATA_MANTISSA);
            }
            if (index == DATA_EXPONENT) {
                return storage.getDisplayExponent();
            }
            if (index >= DATA_RECEIVED && index < DATA_RECEIVED + 4) {
                return part(storage.getReceivedPerTick(), index - DATA_RECEIVED);
            }
            if (index >= DATA_SENT && index < DATA_SENT + 4) {
                return part(storage.getSentPerTick(), index - DATA_SENT);
            }
            if (index == DATA_SIDES) {
                int sides = 0;
                for (Direction side : SIDES) {
                    sides |= storage.getMode(side).ordinal() << (side.ordinal() * 2);
                }
                return sides;
            }
            return 0;
        }

        private static int part(long value, int part) {
            return (int) ((value >>> (part * 16)) & 0xFFFF);
        }

        @Override
        public void set(int index, int value) {
            // read only
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
