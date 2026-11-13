package com.projectinfinity.pigenerator.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource filter for the generators.
 *
 * <p>Holds a list of items and a mode: <em>except</em> (default, the listed items are not generated) or
 * <em>only</em> (nothing but the listed items is generated). Like the generators it has no GUI:</p>
 * <ul>
 *   <li>right click with the filter in the main hand and a resource in the off hand: add / remove that resource;</li>
 *   <li>sneak + right click: switch the mode;</li>
 *   <li>right click a generator: install the filter (see {@code GeneratorBlock#use}).</li>
 * </ul>
 *
 * <p>NBT: {@code {Whitelist: 1b, Items: ["minecraft:coal", ...]}}. Crafting the filter by itself clears it.</p>
 */
public class ResourceFilterItem extends Item {
    public static final int MAX_ENTRIES = 18;
    private static final String TAG_WHITELIST = "Whitelist";
    private static final String TAG_ITEMS = "Items";

    public ResourceFilterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    // ------------------------------------------------------------------ filter data

    /** {@code true}: only the listed items are generated, {@code false}: everything except them. */
    public static boolean isWhitelist(ItemStack filter) {
        CompoundTag tag = filter.getTag();
        return tag != null && tag.getBoolean(TAG_WHITELIST);
    }

    public static void setWhitelist(ItemStack filter, boolean whitelist) {
        filter.getOrCreateTag().putBoolean(TAG_WHITELIST, whitelist);
    }

    /** Registry ids of the listed items, in the order they were added. */
    public static List<ResourceLocation> getEntries(ItemStack filter) {
        List<ResourceLocation> entries = new ArrayList<>();
        CompoundTag tag = filter.getTag();
        if (tag != null) {
            ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
                if (id != null && !entries.contains(id)) {
                    entries.add(id);
                }
            }
        }
        return entries;
    }

    private static void setEntries(ItemStack filter, List<ResourceLocation> entries) {
        ListTag list = new ListTag();
        for (ResourceLocation id : entries) {
            list.add(StringTag.valueOf(id.toString()));
        }
        filter.getOrCreateTag().put(TAG_ITEMS, list);
    }

    /** Whether a generator holding this filter may produce the item. An empty stack allows everything. */
    public static boolean allows(ItemStack filter, Item item) {
        if (filter.isEmpty()) {
            return true;
        }
        boolean listed = getEntries(filter).contains(ForgeRegistries.ITEMS.getKey(item));
        return isWhitelist(filter) == listed;
    }

    // ------------------------------------------------------------------ configuration by right click

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack filter = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(filter);
        }
        if (!level.isClientSide) {
            configure(filter, player);
        }
        return InteractionResultHolder.sidedSuccess(filter, level.isClientSide);
    }

    /** Same as {@link #use} when a block is targeted, so that the off hand item is not placed or used. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            configure(context.getItemInHand(), player);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void configure(ItemStack filter, Player player) {
        if (player.isShiftKeyDown()) {
            boolean whitelist = !isWhitelist(filter);
            setWhitelist(filter, whitelist);
            player.displayClientMessage(Component.translatable("message.pi_generator.filter.mode", modeName(whitelist)), true);
            return;
        }
        ItemStack sample = player.getOffhandItem();
        if (sample.isEmpty() || sample.getItem() instanceof ResourceFilterItem) {
            player.displayClientMessage(Component.translatable("message.pi_generator.filter.hint")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(sample.getItem());
        if (id == null) {
            return;
        }
        Component name = sample.getItem().getDescription();
        List<ResourceLocation> entries = getEntries(filter);
        if (entries.remove(id)) {
            setEntries(filter, entries);
            player.displayClientMessage(Component.translatable("message.pi_generator.filter.removed", name)
                    .withStyle(ChatFormatting.RED), true);
        } else if (entries.size() >= MAX_ENTRIES) {
            player.displayClientMessage(Component.translatable("message.pi_generator.filter.full", MAX_ENTRIES)
                    .withStyle(ChatFormatting.RED), true);
        } else {
            entries.add(id);
            setEntries(filter, entries);
            player.displayClientMessage(Component.translatable("message.pi_generator.filter.added", name)
                    .withStyle(ChatFormatting.GREEN), true);
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean whitelist = isWhitelist(stack);
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.mode", modeName(whitelist)).withStyle(ChatFormatting.GRAY));
        List<ResourceLocation> entries = getEntries(stack);
        if (entries.isEmpty()) {
            tooltip.add(Component.translatable(whitelist ? "tooltip.pi_generator.filter.empty_only"
                    : "tooltip.pi_generator.filter.empty_except").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (ResourceLocation id : entries) {
            Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
            Component name = item != null ? item.getDescription() : Component.literal(id.toString());
            tooltip.add(Component.literal(" - ").append(name).withStyle(item != null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.help_toggle").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.help_mode").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.help_install").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.filter.help_remove").withStyle(ChatFormatting.DARK_GRAY));
    }
}
