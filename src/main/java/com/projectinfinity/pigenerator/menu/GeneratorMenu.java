package com.projectinfinity.pigenerator.menu;

import com.projectinfinity.pigenerator.GeneratorTier;
import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.blockentity.GeneratorBlockEntity;
import com.projectinfinity.pigenerator.item.InstalledItemsHandler;
import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Screen of a generator: the 27 slot output buffer (take only), the filter slot, the three upgrade slots
 * and the numbers shown by the screen.
 *
 * <p>Slot order: 0..26 buffer, 27 filter, 28..30 upgrades (speed, efficiency, energy), then the player
 * inventory and hotbar.</p>
 */
public class GeneratorMenu extends AbstractContainerMenu {
    public static final int BUFFER_SLOTS = GeneratorBlockEntity.SLOTS;
    public static final int INSTALLED_START = BUFFER_SLOTS;
    public static final int INVENTORY_START = INSTALLED_START + InstalledItemsHandler.SLOTS;
    public static final int INVENTORY_END = INVENTORY_START + 36;

    public static final int INSTALLED_X = 98;
    public static final int INSTALLED_Y = 50;
    public static final int BUFFER_Y = 72;
    public static final int INVENTORY_Y = 140;

    // Synchronised numbers. The vanilla packet carries 16 bits per value, so ints are sent in two halves.
    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_PROGRESS = 2;
    private static final int DATA_STEPS = 3;
    private static final int DATA_OUTPUT = 4;
    private static final int DATA_COST_LOW = 5;
    private static final int DATA_COST_HIGH = 6;
    private static final int DATA_STATE = 7;
    private static final int DATA_COUNT = 8;

    private final GeneratorTier tier;
    private final ContainerData data;
    /** {@code null} on the client. */
    @Nullable
    private final GeneratorBlockEntity generator;

    /** Client side constructor, see {@code GeneratorBlock#use}. */
    public GeneratorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, GeneratorTier.byLevel(buffer.readVarInt()),
                new ItemStackHandler(BUFFER_SLOTS), new InstalledItemsHandler(), new SimpleContainerData(DATA_COUNT), null);
    }

    public GeneratorMenu(int containerId, Inventory inventory, GeneratorBlockEntity generator) {
        this(containerId, inventory, generator.getTier(), generator.getBuffer(), generator.getInstalledItems(),
                new GeneratorData(generator), generator);
    }

    private GeneratorMenu(int containerId, Inventory inventory, GeneratorTier tier, IItemHandler buffer,
                          IItemHandler installed, ContainerData data, @Nullable GeneratorBlockEntity generator) {
        super(ModRegistry.GENERATOR_MENU.get(), containerId);
        this.tier = tier;
        this.data = data;
        this.generator = generator;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new OutputSlot(buffer, column + row * 9, 8 + column * 18, BUFFER_Y + row * 18));
            }
        }
        for (int slot = 0; slot < InstalledItemsHandler.SLOTS; slot++) {
            addSlot(new SlotItemHandler(installed, slot, INSTALLED_X + slot * 18, INSTALLED_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    // ------------------------------------------------------------------ numbers for the screen

    private int getInt(int low, int high) {
        return (data.get(high) << 16) | (data.get(low) & 0xFFFF);
    }

    public GeneratorTier getTier() {
        return tier;
    }

    public int getEnergy() {
        return getInt(DATA_ENERGY_LOW, DATA_ENERGY_HIGH);
    }

    public int getCapacity() {
        return tier.getCapacity();
    }

    /** Progress of the current operation, 0..{@link #getCycleTicks()}. */
    public int getProgress() {
        return data.get(DATA_PROGRESS);
    }

    public int getCycleTicks() {
        return tier.getCycleTicks();
    }

    /** Speed multiplier (1 without a speed upgrade). */
    public int getSpeed() {
        return Math.max(1, data.get(DATA_STEPS));
    }

    /** Output multiplier (1 without an efficiency upgrade). */
    public int getOutputMultiplier() {
        return Math.max(1, data.get(DATA_OUTPUT));
    }

    /** FE used by one work step. */
    public int getEnergyPerStep() {
        return getInt(DATA_COST_LOW, DATA_COST_HIGH);
    }

    /** FE per tick at full speed. */
    public long getEnergyPerTick() {
        return (long) getEnergyPerStep() * getSpeed();
    }

    public GeneratorBlockEntity.State getState() {
        return GeneratorBlockEntity.State.byId(data.get(DATA_STATE));
    }

    /** Slot index of the filter slot. */
    public static int filterSlot() {
        return INSTALLED_START + InstalledItemsHandler.FILTER_SLOT;
    }

    /** Slot index of the slot for upgrades of the given kind. */
    public static int upgradeSlot(UpgradeType type) {
        return INSTALLED_START + InstalledItemsHandler.upgradeSlot(type);
    }

    // ------------------------------------------------------------------ container logic

    @Override
    public boolean stillValid(Player player) {
        if (generator == null) {
            return true;
        }
        return !generator.isRemoved()
                && player.distanceToSqr(generator.getBlockPos().getX() + 0.5, generator.getBlockPos().getY() + 0.5,
                generator.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    /** Shift click: buffer / installed items go to the inventory, filters and upgrades go into their slots. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INSTALLED_START, INVENTORY_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (generator != null) {
            generator.markChanged(); // the buffer stack was shrunk in place
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Buffer slot: items can only be taken out. */
    private static class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler handler, int slot, int x, int y) {
            super(handler, slot, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }

    /** Server side source of the synchronised numbers. */
    private record GeneratorData(GeneratorBlockEntity generator) implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> generator.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY_HIGH -> generator.getEnergyStored() >>> 16;
                case DATA_PROGRESS -> generator.getProgress();
                case DATA_STEPS -> generator.getStepsPerTick();
                case DATA_OUTPUT -> generator.getOutputMultiplier();
                case DATA_COST_LOW -> generator.getEnergyPerStep() & 0xFFFF;
                case DATA_COST_HIGH -> generator.getEnergyPerStep() >>> 16;
                case DATA_STATE -> generator.getState().ordinal();
                default -> 0;
            };
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
