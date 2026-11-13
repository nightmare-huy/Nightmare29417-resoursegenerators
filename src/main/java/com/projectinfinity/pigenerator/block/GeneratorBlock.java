package com.projectinfinity.pigenerator.block;

import com.projectinfinity.pigenerator.GeneratorTier;
import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.blockentity.GeneratorBlockEntity;
import com.projectinfinity.pigenerator.item.ResourceFilterItem;
import com.projectinfinity.pigenerator.item.UpgradeItem;
import com.projectinfinity.pigenerator.menu.GeneratorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Resource generator block. One class for all eight tiers, the tier is fixed per block instance. */
public class GeneratorBlock extends BaseEntityBlock {
    private final GeneratorTier tier;

    public GeneratorBlock(GeneratorTier tier) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
        this.tier = tier;
    }

    public GeneratorTier getTier() {
        return tier;
    }

    /** 1234567 -> "1,234,567" */
    public static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // All logic is server side.
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.GENERATOR_BLOCK_ENTITY.get(), GeneratorBlockEntity::serverTick);
    }

    /** BaseEntityBlock defaults to INVISIBLE, we use a normal JSON block model. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Right click:
     * <ul>
     *   <li>holding a resource filter or an upgrade: install it, the one it replaces is handed back;</li>
     *   <li>sneaking with an empty hand: take out the filter, then the upgrades, one per click;</li>
     *   <li>otherwise: open the generator screen ({@link GeneratorMenu}).</li>
     * </ul>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof ResourceFilterItem) {
                giveBack(player, hand, generator.setFilter(held.split(1)));
                player.displayClientMessage(Component.translatable("message.pi_generator.filter.installed",
                        ResourceFilterItem.describe(generator.getFilter())).withStyle(ChatFormatting.GREEN), true);
            } else if (held.getItem() instanceof UpgradeItem upgrade) {
                Component name = held.getHoverName();
                giveBack(player, hand, generator.setUpgrade(upgrade.getType(), held.split(1)));
                player.displayClientMessage(Component.translatable("message.pi_generator.installed", name)
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (player.isShiftKeyDown() && held.isEmpty() && generator.hasInstalledItems()) {
                ItemStack taken = generator.takeInstalledItem();
                player.displayClientMessage(Component.translatable("message.pi_generator.taken", taken.getHoverName())
                        .withStyle(ChatFormatting.YELLOW), true);
                player.setItemInHand(hand, taken);
            } else if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider((id, inventory, p) -> new GeneratorMenu(id, inventory, generator), getName()),
                        buffer -> buffer.writeVarInt(tier.getLevel()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Returns a replaced filter / upgrade to the player: into the (now empty) hand if possible. */
    private static void giveBack(Player player, InteractionHand hand, ItemStack previous) {
        if (previous.isEmpty()) {
            return;
        }
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, previous);
        } else {
            ItemHandlerHelper.giveItemToPlayer(player, previous);
        }
    }

    /** Drop the buffered items, the filter and the upgrades when the block is broken or replaced. */
    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
            generator.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.pi_generator.tier", tier.getLevel()).withStyle(tier.getColor()));
        tooltip.add(Component.translatable("tooltip.pi_generator.usage", formatNumber(tier.getEnergyPerTick()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.buffer", formatNumber(tier.getCapacity()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.cycle", tier.getCycleTicks())
                .withStyle(ChatFormatting.GRAY));
    }
}
