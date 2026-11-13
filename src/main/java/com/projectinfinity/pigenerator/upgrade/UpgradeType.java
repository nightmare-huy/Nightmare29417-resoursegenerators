package com.projectinfinity.pigenerator.upgrade;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/**
 * The three kinds of generator upgrades. Every kind comes in {@link #TIERS} tiers and a generator
 * holds at most one upgrade of each kind.
 */
public enum UpgradeType {
    /** Work steps per tick. Each step costs the normal FE, so FE/t grows with the speed. */
    SPEED(ChatFormatting.RED, 2, 4, 8, 16),
    /** Multiplier of the number of items made per operation. */
    EFFICIENCY(ChatFormatting.GREEN, 2, 3, 4, 5),
    /** Percent of the energy cost that is saved. */
    ENERGY(ChatFormatting.YELLOW, 25, 50, 75, 90);

    public static final int TIERS = 4;

    private final ChatFormatting color;
    private final int[] values;

    UpgradeType(ChatFormatting color, int... values) {
        this.color = color;
        this.values = values;
    }

    /** speed / efficiency / energy: used in registry names, NBT keys and translation keys. */
    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ChatFormatting getColor() {
        return color;
    }

    /** Effect of the given tier (1..4), see the constants for its meaning. */
    public int getValue(int tier) {
        return values[Math.max(1, Math.min(TIERS, tier)) - 1];
    }
}
