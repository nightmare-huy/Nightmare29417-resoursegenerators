package com.projectinfinity.pigenerator.item;

import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.menu.FilterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource filter for the generators.
 *
 * <p>Holds a list of items and a mode: <em>except</em> (default, the listed items are not generated) or
 * <em>only</em> (nothing but the listed items is generated). Right clicking with the filter in the main
 * hand opens its screen ({@link FilterMenu}); right clicking a generator installs it
 * (see {@code GeneratorBlock#useItemOn}), as does putting it into the filter slot of the generator screen.</p>
 *
 * <p>The mode and the list are kept in the data component {@code pi_generator:filter}, see {@link FilterData}.
 * Crafting the filter by itself clears it.</p>
 */
public class ResourceFilterItem extends Item {
    public static final int MAX_ENTRIES = 18;

    public ResourceFilterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    // ------------------------------------------------------------------ filter data

    /** The data of the filter stack; {@link FilterData#EMPTY} for a new filter. */
    public static FilterData getData(ItemStack filter) {
        return filter.getOrDefault(ModRegistry.FILTER_DATA, FilterData.EMPTY);
    }

    /** {@code true}: only the listed items are generated, {@code false}: everything except them. */
    public static boolean isWhitelist(ItemStack filter) {
        return getData(filter).whitelist();
    }

    public static void setWhitelist(ItemStack filter, boolean whitelist) {
        filter.set(ModRegistry.FILTER_DATA, getData(filter).withWhitelist(whitelist));
    }

    /** Registry ids of the listed items, in the order they were added. */
    public static List<ResourceLocation> getEntries(ItemStack filter) {
        return getData(filter).items();
    }

    /** Replaces the list of items; duplicates are dropped and at most {@link #MAX_ENTRIES} are kept. */
    public static void setEntries(ItemStack filter, List<ResourceLocation> entries) {
        List<ResourceLocation> list = new ArrayList<>();
        for (ResourceLocation id : entries) {
            if (list.size() >= MAX_ENTRIES) {
                break;
            }
            if (!list.contains(id)) {
                list.add(id);
            }
        }
        filter.set(ModRegistry.FILTER_DATA, getData(filter).withItems(list));
    }

    /** Whether a generator holding this filter may produce the item. An empty stack allows everything. */
    public static boolean allows(ItemStack filter, Item item) {
        if (filter.isEmpty()) {
            return true;
        }
        boolean listed = getEntries(filter).contains(BuiltInRegistries.ITEM.getKey(item));
        return isWhitelist(filter) == listed;
    }

    // ------------------------------------------------------------------ opening the screen

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack filter = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(filter);
        }
        openScreen(player, filter);
        return InteractionResultHolder.sidedSuccess(filter, level.isClientSide);
    }

    /** Same as {@link #use} when a block is targeted, so that the off hand item is not placed or used. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        openScreen(player, context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void openScreen(Player player, ItemStack filter) {
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = player.getInventory().selected; // the main hand
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, p) -> new FilterMenu(id, inventory, slot), filter.getHoverName()),
                    buffer -> buffer.writeVarInt(slot));
        }
    }

    // ------------------------------------------------------------------ text

    public static MutableComponent modeName(boolean whitelist) {
        return Component.translatable(whitelist ? "filter.pi_generator.mode.only" : "filter.pi_generator.mode.except")
                .withStyle(whitelist ? ChatFormatting.AQUA : ChatFormatting.GOLD);
    }

    /** Short description for the generator status line: mode and number of listed items. */
    public static Component describe(ItemStack filter) {
        return Component.translatable("message.pi_generator.filter.summary",
                modeName(isWhitelist(filter)), getEntries(filter).size());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean whitelist = isWhitelist(stack);
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.mode", modeName(whitelist)).withStyle(ChatFormatting.GRAY));
        List<ResourceLocation> entries = getEntries(stack);
        if (entries.isEmpty()) {
            tooltip.add(Component.translatable(whitelist ? "tooltip.pi_generator.filter.empty_only"
                    : "tooltip.pi_generator.filter.empty_except").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (ResourceLocation id : entries) {
            Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
            Component name = item != null ? item.getDescription() : Component.literal(id.toString());
            tooltip.add(Component.literal(" - ").append(name).withStyle(item != null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.help_open").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.help_install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
