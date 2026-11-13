package com.projectinfinity.pigenerator.item;

import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The items installed in a generator: slot 0 holds the resource filter, slots 1..3 one upgrade of each
 * {@link UpgradeType}. Every slot holds a single item.
 */
public class InstalledItemsHandler extends ItemStackHandler {
    public static final int FILTER_SLOT = 0;
    public static final int SLOTS = 1 + UpgradeType.values().length;

    public InstalledItemsHandler() {
        super(SLOTS);
    }

    public static int upgradeSlot(UpgradeType type) {
        return 1 + type.ordinal();
    }

    /** Sets a slot while the owner is being read from NBT: no change notification. */
    public void load(int slot, ItemStack stack) {
        stacks.set(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (slot == FILTER_SLOT) {
            return stack.getItem() instanceof ResourceFilterItem;
        }
        return stack.getItem() instanceof UpgradeItem upgrade && upgradeSlot(upgrade.getType()) == slot;
    }
}
