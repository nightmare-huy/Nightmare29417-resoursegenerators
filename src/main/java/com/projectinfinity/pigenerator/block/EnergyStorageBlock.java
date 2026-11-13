package com.projectinfinity.pigenerator.block;

import com.mojang.serialization.MapCodec;
import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.blockentity.EnergyStorageBlockEntity;
import com.projectinfinity.pigenerator.energy.EnergyFormat;
import com.projectinfinity.pigenerator.energy.SideMode;
import com.projectinfinity.pigenerator.menu.EnergyStorageMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Energy storage block: unlimited capacity, a single kind (no tiers).
 *
 * <p>Each of the six sides is an input, an output or switched off; the mode is a block state property
 * ({@code down}, {@code up}, {@code north}, {@code south}, {@code west}, {@code east}), so the faces show it.
 * When the block is placed, the side it is placed against becomes the output, the others are inputs.</p>
 */
public class EnergyStorageBlock extends BaseEntityBlock {
    public static final MapCodec<EnergyStorageBlock> CODEC = simpleCodec(EnergyStorageBlock::new);

    private static final Map<Direction, EnumProperty<SideMode>> SIDES = new EnumMap<>(Direction.class);

    static {
        for (Direction side : Direction.values()) {
            SIDES.put(side, EnumProperty.create(side.getSerializedName(), SideMode.class));
        }
    }

    public EnergyStorageBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }

    public EnergyStorageBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<SideMode> property : SIDES.values()) {
            state = state.setValue(property, SideMode.INPUT);
        }
        registerDefaultState(state);
    }

    /** The block state property that holds the mode of a side. */
    public static EnumProperty<SideMode> property(Direction side) {
        return SIDES.get(side);
    }

    @Override
    protected MapCodec<EnergyStorageBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    /** The side the block is placed against gives energy out, e.g. placed onto a generator it feeds it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(property(context.getClickedFace().getOpposite()), SideMode.OUTPUT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyStorageBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.ENERGY_STORAGE_BLOCK_ENTITY.get(), EnergyStorageBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Switches a side to the next mode (input -> output -> off). Other mods cache the energy capability of
     * a side, so they are told that it changed.
     */
    public static SideMode cycleSide(Level level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EnergyStorageBlock)) {
            return SideMode.OFF;
        }
        SideMode mode = state.getValue(property(side)).next();
        level.setBlock(pos, state.setValue(property(side), mode), Block.UPDATE_ALL);
        level.invalidateCapabilities(pos);
        return mode;
    }

    /** "North: output" */
    public static MutableComponent describeSide(Direction side, SideMode mode) {
        return Component.translatable("gui.pi_generator.energy_storage.side",
                Component.translatable("side.pi_generator." + side.getSerializedName()), mode.getDisplayName());
    }

    /**
     * Right click:
     * <ul>
     *   <li>sneaking with an empty hand: switch the mode of the clicked side;</li>
     *   <li>otherwise: open the screen ({@link EnergyStorageMenu}).</li>
     * </ul>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnergyStorageBlockEntity storage) {
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                Direction side = hit.getDirection();
                player.displayClientMessage(describeSide(side, cycleSide(level, pos, side)), true);
            } else if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(
                        new SimpleMenuProvider((id, inventory, p) -> new EnergyStorageMenu(id, inventory, storage), getName()),
                        buffer -> buffer.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BigInteger stored = stack.getOrDefault(ModRegistry.STORED_ENERGY.get(), BigInteger.ZERO);
        tooltip.add(Component.translatable("tooltip.pi_generator.energy_storage.stored", EnergyFormat.full(stored))
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.pi_generator.energy_storage.capacity").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.pi_generator.energy_storage.help_place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.energy_storage.help_sides").withStyle(ChatFormatting.GRAY));
    }
}
