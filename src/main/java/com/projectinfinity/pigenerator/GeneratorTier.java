package com.projectinfinity.pigenerator;

import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parameters of the eight generator stages.
 *
 * <p>Outputs are plain registry ids, resolved lazily through {@link ForgeRegistries#ITEMS} so that no
 * other mod is needed at compile time. Ids of mods that are not installed are silently skipped.
 * An entry may list several alternatives separated by {@code |}: the first id that exists is used
 * (covers ports of a mod that name the same item differently).</p>
 */
public enum GeneratorTier {
    TIER_1(1, 256, 100_000, 40, false, Inherit.NONE, Rarity.COMMON, ChatFormatting.GRAY,
            // vanilla base ores
            "minecraft:coal", "minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold",
            "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:diamond", "minecraft:emerald",
            "minecraft:quartz",
            // modded base ores
            "thermal:apatite", "mekanism:raw_osmium", "thermal:raw_tin", "thermal:raw_lead",
            "thermal:raw_silver", "thermal:raw_nickel"),

    TIER_2(2, 1_024, 500_000, 35, false, Inherit.PREVIOUS, Rarity.COMMON, ChatFormatting.GREEN,
            "mekanism:ingot_steel", "thermal:invar_ingot", "thermal:electrum_ingot",
            "ae2:certus_quartz_crystal"),

    TIER_3(3, 8_192, 2_000_000, 30, false, Inherit.NONE, Rarity.UNCOMMON, ChatFormatting.AQUA,
            "ae2:fluix_crystal", "thermal:signalum_ingot", "thermal:lumium_ingot",
            "mekanism:alloy_infused", "mekanism:ingot_refined_obsidian"),

    TIER_4(4, 65_536, 10_000_000, 20, false, Inherit.NONE, Rarity.UNCOMMON, ChatFormatting.BLUE,
            "thermal:enderium_ingot", "mekanism:alloy_reinforced", "ae2:calculation_processor",
            "ae2:engineering_processor", "ae2:logic_processor", "mekanism:raw_uranium"),

    TIER_5(5, 524_288, 50_000_000, 15, false, Inherit.NONE, Rarity.RARE, ChatFormatting.YELLOW,
            "mekanism:alloy_atomic", "mekanism:ultimate_control_circuit", "ae2:singularity",
            "minecraft:nether_star"),

    TIER_6(6, 4_194_304, 500_000_000, 10, false, Inherit.NONE, Rarity.RARE, ChatFormatting.GOLD,
            "mekanism:pellet_polonium", "mekanism:pellet_plutonium", "avaritia:crystal_matrix_ingot"),

    TIER_7(7, 67_108_864, 2_000_000_000, 5, false, Inherit.NONE, Rarity.EPIC, ChatFormatting.RED,
            "mekanism:pellet_antimatter", "avaritia:neutron_pile", "avaritia:neutron_nugget"),

    TIER_8(8, 1_000_000_000, Integer.MAX_VALUE, 1, true, Inherit.ALL_PREVIOUS, Rarity.EPIC, ChatFormatting.LIGHT_PURPLE,
            // Re-Avaritia registers the neutronium ingot as "avaritia:neutron_ingot"
            "avaritia:neutronium_ingot|avaritia:neutron_ingot", "avaritia:infinity_catalyst",
            "mekanism:pellet_antimatter");

    /** Which lower tiers contribute their outputs to this tier. */
    private enum Inherit {
        NONE, PREVIOUS, ALL_PREVIOUS
    }

    private final int level;
    private final int energyPerTick;
    private final int capacity;
    private final int cycleTicks;
    private final boolean fullStacks;
    private final Inherit inherit;
    private final Rarity rarity;
    private final ChatFormatting color;
    private final List<String> ownOutputs;

    /** Resolved lazily on first use (registries are complete long before the first block entity tick). */
    private volatile List<Item> resolvedOutputs;

    GeneratorTier(int level, int energyPerTick, int capacity, int cycleTicks, boolean fullStacks,
                  Inherit inherit, Rarity rarity, ChatFormatting color, String... ownOutputs) {
        this.level = level;
        this.energyPerTick = energyPerTick;
        this.capacity = capacity;
        this.cycleTicks = cycleTicks;
        this.fullStacks = fullStacks;
        this.inherit = inherit;
        this.rarity = rarity;
        this.color = color;
        this.ownOutputs = List.of(ownOutputs);
    }

    /** 1..8 */
    /** Tier with the given level (1..8); tier 1 for anything else. */
    public static GeneratorTier byLevel(int level) {
        for (GeneratorTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return TIER_1;
    }

    public int getLevel() {
        return level;
    }

    /** Registry name of the block and its item: {@code generator_tier_N}. */
    public String getRegistryName() {
        return "generator_tier_" + level;
    }

    /** FE consumed every tick while the generator is working. */
    public int getEnergyPerTick() {
        return energyPerTick;
    }

    /** Size of the internal FE buffer. Also the per-call receive limit, i.e. input is effectively unlimited. */
    public int getCapacity() {
        return capacity;
    }

    /** Number of working ticks per generated output. */
    public int getCycleTicks() {
        return cycleTicks;
    }

    /** {@code true}: a full stack per operation (tier 8), {@code false}: a single item. */
    public boolean producesFullStacks() {
        return fullStacks;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public ChatFormatting getColor() {
        return color;
    }

    /** Output ids of this tier including inherited ones, in generation order and without duplicates. */
    public List<String> getOutputIds() {
        Set<String> ids = new LinkedHashSet<>();
        GeneratorTier[] tiers = values();
        switch (inherit) {
            case PREVIOUS -> {
                if (ordinal() > 0) {
                    ids.addAll(tiers[ordinal() - 1].getOutputIds());
                }
            }
            case ALL_PREVIOUS -> {
                for (int i = 0; i < ordinal(); i++) {
                    ids.addAll(tiers[i].getOutputIds());
                }
            }
            default -> {
            }
        }
        ids.addAll(ownOutputs);
        return List.copyOf(ids);
    }

    /** Items this tier can generate in the current instance (only items that are actually registered). */
    public List<Item> getOutputs() {
        List<Item> outputs = resolvedOutputs;
        if (outputs == null) {
            outputs = resolveOutputs();
            resolvedOutputs = outputs;
        }
        return outputs;
    }

    private List<Item> resolveOutputs() {
        Set<Item> items = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (String entry : getOutputIds()) {
            Item item = resolve(entry);
            if (item != null) {
                items.add(item);
            } else {
                missing.add(entry);
            }
        }
        if (!missing.isEmpty()) {
            PiGenerator.LOGGER.info("{}: skipping outputs that are not registered (mod not installed?): {}", this, missing);
        }
        PiGenerator.LOGGER.debug("{}: resolved {} output item(s)", this, items.size());
        return List.copyOf(items);
    }

    /** Returns the first registered item among the {@code |}-separated alternatives, or {@code null}. */
    private static Item resolve(String entry) {
        for (String id : entry.split("\\|")) {
            try {
                // Resource locations must be lower case, be lenient about ids such as "mekanism:Raw_osmium".
                ResourceLocation key = new ResourceLocation(id.trim().toLowerCase(Locale.ROOT));
                if (ForgeRegistries.ITEMS.containsKey(key)) {
                    return ForgeRegistries.ITEMS.getValue(key);
                }
            } catch (ResourceLocationException invalidId) {
                PiGenerator.LOGGER.warn("Invalid item id '{}' in generator output list", id);
            }
        }
        return null;
    }
}
