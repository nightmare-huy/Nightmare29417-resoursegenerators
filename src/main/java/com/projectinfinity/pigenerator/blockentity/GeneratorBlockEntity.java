package com.projectinfinity.pigenerator.blockentity;

import com.projectinfinity.pigenerator.GeneratorTier;
import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.block.GeneratorBlock;
import com.projectinfinity.pigenerator.energy.GeneratorEnergyStorage;
import com.projectinfinity.pigenerator.item.OutputOnlyItemHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 *   <li>27 slot output buffer exposed as an (extract only) item handler on every side.</li>
 *   <li>Generated items are pushed straight into adjacent inventories (ME interfaces, chests, pipes);
 *       only what does not fit is kept in the buffer and retried periodically.</li>
 * </ul>
 */
public class GeneratorBlockEntity extends BlockEntity {
    public static final int SLOTS = 27;
    /** How often (in ticks) buffered leftovers are offered to the neighbours again. */
    private static final int FLUSH_INTERVAL = 10;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_OUTPUT_INDEX = "OutputIndex";
    private static final String TAG_INVENTORY = "Inventory";

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
    private int outputIndex;
    private int flushTimer;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GENERATOR_BLOCK_ENTITY.get(), pos, state);
        this.tier = state.getBlock() instanceof GeneratorBlock block ? block.getTier() : GeneratorTier.TIER_1;
        this.energy = new GeneratorEnergyStorage(tier.getCapacity(), this::markDirty);
        createCapabilities();
    }

    public GeneratorTier getTier() {
        return tier;
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
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

        List<Item> outputs = tier.getOutputs();
        if (outputs.isEmpty()) {
            return; // none of the output items exist in this instance
        }
        if (outputIndex >= outputs.size()) {
            outputIndex = 0;
        }

        if (energy.getEnergyStored() < tier.getEnergyPerTick()) {
            return; // waiting for power, progress is kept
        }
        ItemStack output = createOutput(outputs.get(outputIndex));
        // Pause without burning energy while the buffer has no room for the next output.
        if (!ItemHandlerHelper.insertItemStacked(inventory, output, true).isEmpty()) {
            return;
        }
        energy.consume(tier.getEnergyPerTick());

        if (++progress >= tier.getCycleTicks()) {
            progress = 0;
            outputIndex = (outputIndex + 1) % outputs.size();

            ItemStack remainder = eject(output, neighbourInventories(level, pos));
            if (!remainder.isEmpty()) {
                ItemHandlerHelper.insertItemStacked(inventory, remainder, false);
            }
        }
        markDirty();
    }

    private ItemStack createOutput(Item item) {
        ItemStack stack = new ItemStack(item);
        if (tier.producesFullStacks()) {
            stack.setCount(stack.getMaxStackSize());
        }
        return stack;
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
    }

    // ------------------------------------------------------------------ status

    /** Action bar text shown on right click: tier, stored / max FE and FE per tick. */
    public Component getStatusMessage() {
        return Component.translatable("message.pi_generator.status",
                Component.literal(String.valueOf(tier.getLevel())).withStyle(tier.getColor()),
                Component.literal(GeneratorBlock.formatNumber(energy.getEnergyStored())).withStyle(ChatFormatting.GREEN),
                Component.literal(GeneratorBlock.formatNumber(energy.getMaxEnergyStored())).withStyle(ChatFormatting.GREEN),
                Component.literal(GeneratorBlock.formatNumber(tier.getEnergyPerTick())).withStyle(ChatFormatting.YELLOW));
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        outputIndex = Math.max(0, tag.getInt(TAG_OUTPUT_INDEX));
        if (tag.contains(TAG_INVENTORY)) {
            CompoundTag inventoryTag = tag.getCompound(TAG_INVENTORY).copy();
            inventoryTag.putInt("Size", SLOTS); // the buffer size is fixed, ignore whatever was saved
            inventory.deserializeNBT(inventoryTag);
        }
    }
}
