package com.projectinfinity.pigenerator.item;

import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Generator upgrade ({@code speed_upgrade_N}, {@code efficiency_upgrade_N}, {@code energy_upgrade_N}, N = 1..4).
 * Installed by right clicking a generator, see {@code GeneratorBlock#use}.
 */
public class UpgradeItem extends Item {
    private static final Rarity[] RARITIES = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};

    private final UpgradeType type;
    private final int tier;

    public UpgradeItem(UpgradeType type, int tier) {
        super(new Item.Properties().stacksTo(16).rarity(RARITIES[tier - 1]));
        this.type = type;
        this.tier = tier;
    }

    public UpgradeType getType() {
        return type;
    }

    /** 1..4 */
    public int getTier() {
        return tier;
    }

    /** Effect of this upgrade, see {@link UpgradeType}. */
    public int getValue() {
        return type.getValue(tier);
    }

    public static String registryName(UpgradeType type, int tier) {
        return type.getName() + "_upgrade_" + tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.pi_generator.upgrade." + type.getName(), getValue())
                .withStyle(type.getColor()));
        tooltip.add(Component.translatable("tooltip.pi_generator.upgrade.help_install").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.pi_generator.upgrade.help_remove").withStyle(ChatFormatting.DARK_GRAY));
    }
}
