package com.projectinfinity.pigenerator.blockentity;

import com.projectinfinity.pigenerator.GeneratorTier;
import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.block.GeneratorBlock;
import com.projectinfinity.pigenerator.energy.GeneratorEnergyStorage;
import com.projectinfinity.pigenerator.item.InstalledItemsHandler;
import com.projectinfinity.pigenerator.item.OutputOnlyItemHandler;
import com.projectinfinity.pigenerator.item.ResourceFilterItem;
import com.projectinfinity.pigenerator.item.UpgradeItem;
import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity shared by all generator tiers.
 *
 * <ul>
 *   <li>Forge Energy input on every side (and for the {@code null} side) without a transfer limit.</li>
 *   <li>Every operation makes one of the tier's outputs, picked at random with equal chances.</li>
 *   <li>27 slot output buffer exposed as an (extract only) item handler on every side.</li>
 *   <li>Generated items are pushed straight into adjacent inventories (ME interfaces, chests, pipes);
 *       only what does not fit is kept in the buffer and retried periodically.</li>
 *   <li>An optional {@link ResourceFilterItem resource filter} limits which of the tier's outputs are made.</li>
 *   <li>Up to three {@link UpgradeItem upgrades}, one per {@link UpgradeType}: speed, efficiency, energy.</li>
 *   <li>Everything is shown and edited in the generator screen, see {@code GeneratorMenu}.</li>
 * </ul>
 */
public class GeneratorBlockEntity extends BlockEntity {
    public static final int SLOTS = 27;
    /** How often (in ticks) buffered leftovers are offered to the neighbours again. */
    private static final int FLUSH_INTERVAL = 10;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_OUTPUT_INDEX = "OutputIndex";
    /** Value of {@link #outputIndex} while nothing has been picked yet. */
    private static final int NOT_PICKED = -1;
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_FILTER = "Filter";
    private static final String TAG_UPGRADES = "Upgrades";

    private final GeneratorTier tier;
    private final GeneratorEnergyStorage energy;
    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };
    private final IItemHandler outputView = new OutputOnlyItemHandler(inventory);

    private LazyOptional<IEnergyStorage> energyCapability;
    private LazyOptional<IItemHandler> itemCapability;

    private int progress;
    /**
     * Index in {@link #getActiveOutputs()} of the item the current operation makes, picked at random when
     * the operation starts. Saved, so that an operation in progress still ends with the same item after a reload.
     */
    private int outputIndex = NOT_PICKED;
    private int flushTimer;
    /** Set while the buffer has no room for the next output (shown in the screen). */
    private boolean bufferFull;

    /** The installed resource filter (slot 0) and upgrades (one slot per {@link UpgradeType}). */
    private final InstalledItemsHandler installed = new InstalledItemsHandler() {
        @Override
        protected void onContentsChanged(int slot) {
            installedItemChanged(slot);
            markDirty();
        }
    };
    /** The tier's outputs that pass the filter; {@code null} until computed or after the filter changed. */
    @Nullable
    private List<Item> activeOutputs;

    /** Work steps per tick (speed upgrade). A step is what the generator does in one tick without upgrades. */
    private int stepsPerTick = 1;
    /** Multiplier of the items made per operation (efficiency upgrade). */
    private int outputMultiplier = 1;
    /** FE used by one work step (energy upgrade applied). */
    private int energyPerStep;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GENERATOR_BLOCK_ENTITY.get(), pos, state);
        this.tier = state.getBlock() instanceof GeneratorBlock block ? block.getTier() : GeneratorTier.TIER_1;
        this.energy = new GeneratorEnergyStorage(tier.getCapacity(), this::markDirty);
        applyUpgrades();
        createCapabilities();
    }

    public GeneratorTier getTier() {
        return tier;
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    /** Ticks done of the current operation, 0..{@link GeneratorTier#getCycleTicks()}. */
    public int getProgress() {
        return progress;
    }

    /** The output buffer itself (the generator screen takes items out of it). */
    public ItemStackHandler getBuffer() {
        return inventory;
    }

    /** Filter and upgrade slots, see {@link InstalledItemsHandler}. */
    public InstalledItemsHandler getInstalledItems() {
        return installed;
    }

    /** What the generator is doing right now. */
    public State getState() {
        if (getActiveOutputs().isEmpty()) {
            return State.NO_OUTPUTS;
        }
        if (energy.getEnergyStored() < energyPerStep) {
            return State.NO_ENERGY;
        }
        return bufferFull ? State.BUFFER_FULL : State.WORKING;
    }

    public enum State {
        WORKING, NO_ENERGY, BUFFER_FULL, NO_OUTPUTS;

        public static State byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : WORKING;
        }
    }

    /** Called when the contents of the buffer were changed in place (shift click in the screen). */
    public void markChanged() {
        markDirty();
    }

    private void installedItemChanged(int slot) {
        if (slot == InstalledItemsHandler.FILTER_SLOT) {
            activeOutputs = null;
            outputIndex = NOT_PICKED; // the list has changed, the index would point at another item
        } else {
            applyUpgrades();
        }
    }

    // ------------------------------------------------------------------ upgrades

    public ItemStack getUpgrade(UpgradeType type) {
        return installed.getStackInSlot(InstalledItemsHandler.upgradeSlot(type));
    }

    /** Installs the upgrade ({@link ItemStack#EMPTY} removes it) and returns the previous one of that kind. */
    public ItemStack setUpgrade(UpgradeType type, ItemStack upgrade) {
        ItemStack previous = getUpgrade(type);
        installed.setStackInSlot(InstalledItemsHandler.upgradeSlot(type), upgrade);
        return previous;
    }

    private int upgradeValue(UpgradeType type, int withoutUpgrade) {
        return getUpgrade(type).getItem() instanceof UpgradeItem upgrade && upgrade.getType() == type
                ? upgrade.getValue() : withoutUpgrade;
    }

    /** Work steps per tick: 1, more with a speed upgrade. */
    public int getStepsPerTick() {
        return stepsPerTick;
    }

    /** Multiplier of the items made per operation: 1, more with an efficiency upgrade. */
    public int getOutputMultiplier() {
        return outputMultiplier;
    }

    /** FE used by one work step, the energy upgrade applied. */
    public int getEnergyPerStep() {
        return energyPerStep;
    }

    /** Recomputes the working parameters from the installed upgrades. */
    private void applyUpgrades() {
        stepsPerTick = upgradeValue(UpgradeType.SPEED, 1);
        outputMultiplier = upgradeValue(UpgradeType.EFFICIENCY, 1);
        int saved = upgradeValue(UpgradeType.ENERGY, 0);
        // rounded up, never free
        energyPerStep = (int) Math.max(1L, ((long) tier.getEnergyPerTick() * (100 - saved) + 99) / 100);
    }

    /** FE per tick at full speed with the installed upgrades. May exceed the range of an int. */
    public long getEnergyPerTick() {
        return (long) energyPerStep * stepsPerTick;
    }

    public boolean hasInstalledItems() {
        for (int slot = 0; slot < installed.getSlots(); slot++) {
            if (!installed.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Takes out one installed item: the filter first, then the upgrades. Empty if there is nothing. */
    public ItemStack takeInstalledItem() {
        for (int slot = 0; slot < installed.getSlots(); slot++) {
            ItemStack stack = installed.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                installed.setStackInSlot(slot, ItemStack.EMPTY);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ filter

    public boolean hasFilter() {
        return !getFilter().isEmpty();
    }

    public ItemStack getFilter() {
        return installed.getStackInSlot(InstalledItemsHandler.FILTER_SLOT);
    }

    /** Installs the given filter ({@link ItemStack#EMPTY} removes it) and returns the previous one. */
    public ItemStack setFilter(ItemStack newFilter) {
        ItemStack previous = getFilter();
        installed.setStackInSlot(InstalledItemsHandler.FILTER_SLOT, newFilter);
        return previous;
    }

    /** Outputs of the tier that exist in this instance and pass the installed filter. */
    public List<Item> getActiveOutputs() {
        List<Item> outputs = activeOutputs;
        if (outputs == null) {
            outputs = tier.getOutputs();
            ItemStack filter = getFilter();
            if (!filter.isEmpty()) {
                List<Item> allowed = new ArrayList<>(outputs.size());
                for (Item item : outputs) {
                    if (ResourceFilterItem.allows(filter, item)) {
                        allowed.add(item);
                    }
                }
                outputs = allowed;
            }
            activeOutputs = outputs;
        }
        return outputs;
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        generator.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        if (++flushTimer >= FLUSH_INTERVAL) {
            flushTimer = 0;
            flushBuffer(level, pos);
        }

        List<Item> outputs = getActiveOutputs();
        if (outputs.isEmpty()) {
            bufferFull = false;
            return; // nothing to make (filtered out or not present in this instance): idle, no energy is used
        }
        if (outputIndex < 0 || outputIndex >= outputs.size()) {
            outputIndex = pickOutput(level, outputs);
        }

        // One step per tick, more with a speed upgrade. With too little power it simply does fewer steps.
        boolean worked = false;
        ItemStack output = null; // the next output, created once its room in the buffer has to be checked
        for (int step = 0; step < stepsPerTick; step++) {
            if (energy.getEnergyStored() < energyPerStep) {
                break; // waiting for power, progress is kept
            }
            if (output == null) {
                output = createOutput(outputs.get(outputIndex));
                // Pause without burning energy while the buffer has no room for the next output.
                bufferFull = !ItemHandlerHelper.insertItemStacked(inventory, output, true).isEmpty();
                if (bufferFull) {
                    break;
                }
            }
            energy.consume(energyPerStep);
            worked = true;

            if (++progress >= tier.getCycleTicks()) {
                progress = 0;
                outputIndex = pickOutput(level, outputs);
                deliver(output, level, pos);
                output = null;
            }
        }
        if (worked) {
            markDirty();
        }
    }

    /** Index of a random output; every output of the list has the same chance, whatever was made before. */
    private static int pickOutput(Level level, List<Item> outputs) {
        return outputs.size() > 1 ? level.random.nextInt(outputs.size()) : 0;
    }

    /** One operation's worth of the item. The count can exceed the item's maximum stack size. */
    private ItemStack createOutput(Item item) {
        ItemStack stack = new ItemStack(item);
        int count = tier.producesFullStacks() ? stack.getMaxStackSize() : 1;
        stack.setCount(count * outputMultiplier);
        return stack;
    }

    /** Pushes the output into the neighbours in regular sized stacks; what does not fit goes to the buffer. */
    private void deliver(ItemStack output, Level level, BlockPos pos) {
        List<IItemHandler> targets = neighbourInventories(level, pos);
        int maxStackSize = output.getMaxStackSize();
        while (!output.isEmpty()) {
            ItemStack remainder = eject(output.split(maxStackSize), targets);
            if (!remainder.isEmpty()) {
                ItemHandlerHelper.insertItemStacked(inventory, remainder, false);
            }
        }
    }

    // ------------------------------------------------------------------ auto eject

    /** Item handlers of the (up to six) adjacent blocks, queried on the face that touches the generator. */
    private List<IItemHandler> neighbourInventories(Level level, BlockPos pos) {
        List<IItemHandler> handlers = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue; // never force-load a chunk
            }
            BlockEntity neighbour = level.getBlockEntity(neighbourPos);
            if (neighbour == null || neighbour instanceof GeneratorBlockEntity) {
                continue;
            }
            neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).ifPresent(handlers::add);
        }
        return handlers;
    }

    /** Inserts as much of the stack as possible into the given handlers and returns what is left. */
    private static ItemStack eject(ItemStack stack, List<IItemHandler> targets) {
        ItemStack remainder = stack;
        for (IItemHandler target : targets) {
            if (remainder.isEmpty()) {
                break;
            }
            remainder = ItemHandlerHelper.insertItemStacked(target, remainder, false);
        }
        return remainder;
    }

    /** Offers everything that is still buffered to the neighbours. */
    private void flushBuffer(Level level, BlockPos pos) {
        List<IItemHandler> targets = null;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            if (targets == null) {
                targets = neighbourInventories(level, pos);
                if (targets.isEmpty()) {
                    return;
                }
            }
            ItemStack remainder = eject(stored.copy(), targets);
            if (remainder.getCount() != stored.getCount()) {
                inventory.setStackInSlot(slot, remainder);
            }
        }
    }

    /** Called by the block when it is broken. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        while (hasInstalledItems()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), takeInstalledItem());
        }
    }

    // ------------------------------------------------------------------ capabilities

    private void createCapabilities() {
        energyCapability = LazyOptional.of(() -> energy);
        itemCapability = LazyOptional.of(() -> outputView);
    }

    /** Energy and items are available on all six sides and for the {@code null} (internal) side. */
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!remove) {
            if (capability == ForgeCapabilities.ENERGY) {
                return energyCapability.cast();
            }
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilities();
    }

    // ------------------------------------------------------------------ persistence

    /** Marks the chunk as needing a save, without the neighbour updates of {@link #setChanged()}. */
    private void markDirty() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_OUTPUT_INDEX, outputIndex);
        tag.put(TAG_INVENTORY, inventory.serializeNBT());
        if (hasFilter()) {
            tag.put(TAG_FILTER, getFilter().save(new CompoundTag()));
        }
        CompoundTag upgradesTag = new CompoundTag();
        for (UpgradeType type : UpgradeType.values()) {
            if (!getUpgrade(type).isEmpty()) {
                upgradesTag.put(type.getName(), getUpgrade(type).save(new CompoundTag()));
            }
        }
        if (!upgradesTag.isEmpty()) {
            tag.put(TAG_UPGRADES, upgradesTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        outputIndex = tag.contains(TAG_OUTPUT_INDEX) ? tag.getInt(TAG_OUTPUT_INDEX) : NOT_PICKED;
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag inventoryTag = tag.getCompound(TAG_INVENTORY).copy();
            inventoryTag.putInt("Size", SLOTS); // the buffer size is fixed, ignore whatever was saved
            inventory.deserializeNBT(inventoryTag);
        }
        // The saved layout ({Filter: {...}, Upgrades: {speed: {...}, ...}}) is older than the slots: keep it.
        // Written straight into the handler's list so that loading does not mark the chunk as changed.
        installed.load(InstalledItemsHandler.FILTER_SLOT,
                tag.contains(TAG_FILTER) ? ItemStack.of(tag.getCompound(TAG_FILTER)) : ItemStack.EMPTY);
        CompoundTag upgradesTag = tag.getCompound(TAG_UPGRADES);
        for (UpgradeType type : UpgradeType.values()) {
            installed.load(InstalledItemsHandler.upgradeSlot(type), upgradesTag.contains(type.getName())
                    ? ItemStack.of(upgradesTag.getCompound(type.getName())) : ItemStack.EMPTY);
        }
        activeOutputs = null;
        applyUpgrades();
    }
}
