package com.projectinfinity.pigenerator.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Crafting component of the tier 6-8 generators for packs without Avaritia: eight cores around the
 * generator of the tier below make the next generator in a normal crafting table.
 *
 * <p>{@code crystal_core} (tier 6), {@code neutron_core} (tier 7, made from a crystal core) and
 * {@code infinity_core} (tier 8, made from a neutron core).</p>
 */
public class CoreItem extends Item {
    private final int generatorTier;

    public CoreItem(int generatorTier, Rarity rarity) {
        super(new Item.Properties().rarity(rarity));
        this.generatorTier = generatorTier;
    }

    /** The generator tier (6..8) this core is used for. */
    public int getGeneratorTier() {
        return generatorTier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.pi_generator.core", generatorTier, generatorTier - 1)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.core.no_avaritia").withStyle(ChatFormatting.DARK_GRAY));
    }
}
