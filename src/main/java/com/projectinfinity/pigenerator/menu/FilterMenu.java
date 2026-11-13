package com.projectinfinity.pigenerator.menu;

import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.item.ResourceFilterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen of the resource filter held in the main hand.
 *
 * <p>The 18 slots at the top are "ghost" slots: clicking one with an item on the cursor lists that item
 * (nothing is consumed), clicking it with an empty cursor clears it. Shift clicking an item in the
 * inventory lists it as well. Menu button {@link #BUTTON_TOGGLE_MODE} switches the mode.</p>
 */
public class FilterMenu extends AbstractContainerMenu {
    public static final int GHOST_SLOTS = ResourceFilterItem.MAX_ENTRIES;
    public static final int BUTTON_TOGGLE_MODE = 0;

    public static final int GHOST_X = 8;
    public static final int GHOST_Y = 18;
    public static final int INVENTORY_Y = 92;

    private final Inventory inventory;
    /** Inventory (hotbar) slot of the filter that is being edited. */
    private final int filterSlot;
    private final SimpleContainer ghosts = new SimpleContainer(GHOST_SLOTS);

    /** Client side constructor, see {@code ResourceFilterItem#openScreen}. */
    public FilterMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, data.readVarInt());
    }

    public FilterMenu(int containerId, Inventory inventory, int filterSlot) {
        super(ModRegistry.FILTER_MENU.get(), containerId);
        this.inventory = inventory;
        this.filterSlot = filterSlot;

        int index = 0;
        for (ResourceLocation id : ResourceFilterItem.getEntries(getFilter())) {
            if (index < GHOST_SLOTS && ForgeRegistries.ITEMS.containsKey(id)) {
                ghosts.setItem(index++, new ItemStack(ForgeRegistries.ITEMS.getValue(id)));
            }
        }

        for (int slot = 0; slot < GHOST_SLOTS; slot++) {
            addSlot(new GhostSlot(ghosts, slot, GHOST_X + (slot % 9) * 18, GHOST_Y + (slot / 9) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addInventorySlot(column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            addInventorySlot(column, 8 + column * 18, INVENTORY_Y + 58);
        }
    }

    private void addInventorySlot(int inventorySlot, int x, int y) {
        // The filter itself must stay where it is while its screen is open.
        addSlot(inventorySlot == filterSlot ? new LockedSlot(inventory, inventorySlot, x, y)
                : new Slot(inventory, inventorySlot, x, y));
    }

    /** The filter being edited (empty if it is gone, the menu closes then). */
    public ItemStack getFilter() {
        ItemStack stack = inventory.getItem(filterSlot);
        return stack.getItem() instanceof ResourceFilterItem ? stack : ItemStack.EMPTY;
    }

    public boolean isWhitelist() {
        return ResourceFilterItem.isWhitelist(getFilter());
    }

    @Override
    public boolean stillValid(Player player) {
        return !getFilter().isEmpty();
    }

    // ------------------------------------------------------------------ editing

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                setGhost(slotId, getCarried(), player);
            } else if (clickType == ClickType.QUICK_MOVE) {
                setGhost(slotId, ItemStack.EMPTY, player);
            }
            return; // nothing is ever taken from or put into a ghost slot
        }
        if (clickType == ClickType.SWAP) {
            return; // number keys and the off hand key could move the filter away
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Shift click on an inventory item: list it in the first free ghost slot. Nothing is moved. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= GHOST_SLOTS && index < slots.size()) {
            ItemStack stack = slots.get(index).getItem();
            for (int slot = 0; slot < GHOST_SLOTS && !stack.isEmpty(); slot++) {
                if (ghosts.getItem(slot).isEmpty()) {
                    setGhost(slot, stack, player);
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private void setGhost(int slot, ItemStack sample, Player player) {
        ItemStack ghost = ItemStack.EMPTY;
        if (!sample.isEmpty()) {
            Item item = sample.getItem();
            if (item instanceof ResourceFilterItem || contains(item)) {
                return;
            }
            ghost = new ItemStack(item);
        }
        ghosts.setItem(slot, ghost);
        if (!player.level().isClientSide) {
            save();
        }
    }

    private boolean contains(Item item) {
        for (int slot = 0; slot < GHOST_SLOTS; slot++) {
            if (ghosts.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Writes the ghost slots into the filter stack (server side; the client gets it with the slot sync). */
    private void save() {
        ItemStack filter = getFilter();
        if (filter.isEmpty()) {
            return;
        }
        List<ResourceLocation> entries = new ArrayList<>();
        for (int slot = 0; slot < GHOST_SLOTS; slot++) {
            ItemStack ghost = ghosts.getItem(slot);
            if (!ghost.isEmpty()) {
                entries.add(ForgeRegistries.ITEMS.getKey(ghost.getItem()));
            }
        }
        ResourceFilterItem.setEntries(filter, entries);
        inventory.setChanged();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack filter = getFilter();
        if (id == BUTTON_TOGGLE_MODE && !filter.isEmpty()) {
            ResourceFilterItem.setWhitelist(filter, !ResourceFilterItem.isWhitelist(filter));
            inventory.setChanged();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ slots

    /** Shows a listed item. Holds a display copy only. */
    private static class GhostSlot extends Slot {
        GhostSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static class LockedSlot extends Slot {
        LockedSlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
