package com.projectinfinity.pigenerator.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/** What one side of the energy storage block does. Stored in the block state, one property per side. */
public enum SideMode implements StringRepresentable {
    /** Accepts energy from cables and machines on this side. */
    INPUT("input", ChatFormatting.AQUA),
    /** Pushes energy into the block on this side every tick and lets cables pull it. */
    OUTPUT("output", ChatFormatting.GOLD),
    /** No energy connection on this side. */
    OFF("off", ChatFormatting.GRAY);

    private static final SideMode[] VALUES = values();

    private final String name;
    private final ChatFormatting color;

    SideMode(String name, ChatFormatting color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ChatFormatting getColor() {
        return color;
    }

    /** input -> output -> off -> input */
    public SideMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static SideMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : INPUT;
    }

    /** The translated, coloured name of the mode. */
    public MutableComponent getDisplayName() {
        return Component.translatable("mode.pi_generator." + name).withStyle(color);
    }
}
