package com.projectinfinity.pigenerator;

import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parameters of the eight generator stages.
 *
 * <p>Outputs are plain registry ids, resolved lazily through {@link ForgeRegistries#ITEMS} so that no
 * other mod is needed at compile time. Ids of mods that are not installed are silently skipped.
 * An entry may list several alternatives separated by {@code |}: the first id that exists is used
 * (covers ports of a mod that name the same item differently).</p>
 *
 * <p>Every entry ends with {@code =weight}. A generator picks what to make at random and the chance of an
 * item is its weight divided by the sum of the weights of everything it could make, so the valuable items
 * (low weights) come less often than the cheap ones. The weights can be changed in the config, see
 * {@link PiConfig#outputWeights()}.</p>
 */
public enum GeneratorTier {
    TIER_1(1, 256, 100_000, 40, false, Inherit.NONE, Rarity.COMMON, ChatFormatting.GRAY,
            // vanilla base ores
            "minecraft:coal=10", "minecraft:raw_iron=10", "minecraft:raw_copper=10", "minecraft:raw_gold=6",
            "minecraft:redstone=8", "minecraft:lapis_lazuli=8", "minecraft:diamond=2", "minecraft:emerald=2",
            "minecraft:quartz=8",
            // modded base ores
            "thermal:apatite=8", "mekanism:raw_osmium=8", "thermal:raw_tin=10", "thermal:raw_lead=8",
            "thermal:raw_silver=6", "thermal:raw_nickel=6"),

    TIER_2(2, 1_024, 500_000, 35, false, Inherit.PREVIOUS, Rarity.COMMON, ChatFormatting.GREEN,
            "mekanism:ingot_steel=4", "thermal:invar_ingot=4", "thermal:electrum_ingot=3",
            "ae2:certus_quartz_crystal=8"),

    TIER_3(3, 8_192, 2_000_000, 30, false, Inherit.NONE, Rarity.UNCOMMON, ChatFormatting.AQUA,
            "ae2:fluix_crystal=10", "thermal:signalum_ingot=5", "thermal:lumium_ingot=5",
            "mekanism:alloy_infused=8", "mekanism:ingot_refined_obsidian=3"),

    TIER_4(4, 65_536, 10_000_000, 20, false, Inherit.NONE, Rarity.UNCOMMON, ChatFormatting.BLUE,
            "thermal:enderium_ingot=3", "mekanism:alloy_reinforced=6", "ae2:calculation_processor=8",
            "ae2:engineering_processor=8", "ae2:logic_processor=8", "mekanism:raw_uranium=6"),

    TIER_5(5, 524_288, 50_000_000, 15, false, Inherit.NONE, Rarity.RARE, ChatFormatting.YELLOW,
            "mekanism:alloy_atomic=8", "mekanism:ultimate_control_circuit=5", "ae2:singularity=3",
            "minecraft:nether_star=1"),

    TIER_6(6, 4_194_304, 500_000_000, 10, false, Inherit.NONE, Rarity.RARE, ChatFormatting.GOLD,
            "mekanism:pellet_polonium=6", "mekanism:pellet_plutonium=6", "avaritia:crystal_matrix_ingot=2"),

    TIER_7(7, 67_108_864, 2_000_000_000, 5, false, Inherit.NONE, Rarity.EPIC, ChatFormatting.RED,
            "mekanism:pellet_antimatter=2", "avaritia:neutron_pile=8", "avaritia:neutron_nugget=4"),

    TIER_8(8, 1_000_000_000, Integer.MAX_VALUE, 1, true, Inherit.ALL_PREVIOUS, Rarity.EPIC, ChatFormatting.LIGHT_PURPLE,
            // Re-Avaritia registers the neutronium ingot as "avaritia:neutron_ingot"
            "avaritia:neutronium_ingot|avaritia:neutron_ingot=2", "avaritia:infinity_catalyst=1",
            "mekanism:pellet_antimatter=2");

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
    /** Entry (ids separated by {@code |}) to weight, in the order they were listed. */
    private final Map<String, Integer> ownOutputs;

    /** Resolved lazily on first use (registries are complete long before the first block entity tick). */
    private volatile Outputs resolvedOutputs;

    /** The items of a tier in list order and the (positive) weight of each of them. */
    private record Outputs(List<Item> items, Map<Item, Integer> weights) {
    }

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
        Map<String, Integer> entries = new LinkedHashMap<>();
        for (String entry : ownOutputs) {
            int separator = entry.lastIndexOf('=');
            entries.put(entry.substring(0, separator), Integer.parseInt(entry.substring(separator + 1)));
        }
        this.ownOutputs = entries;
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

    /**
     * Output entries of this tier including the inherited ones, in list order and without duplicates, with
     * their weights. The weight a tier gives to an entry itself replaces the inherited one.
     */
    private Map<String, Integer> getOutputEntries() {
        Map<String, Integer> entries = new LinkedHashMap<>();
        GeneratorTier[] tiers = values();
        switch (inherit) {
            case PREVIOUS -> {
                if (ordinal() > 0) {
                    entries.putAll(tiers[ordinal() - 1].getOutputEntries());
                }
            }
            case ALL_PREVIOUS -> {
                for (int i = 0; i < ordinal(); i++) {
                    entries.putAll(tiers[i].getOutputEntries());
                }
            }
            default -> {
            }
        }
        entries.putAll(ownOutputs);
        return entries;
    }

    /**
     * Items this tier can generate in the current instance: only items that are actually registered and
     * whose weight is not zero.
     */
    public List<Item> getOutputs() {
        return outputs().items();
    }

    /**
     * Weight of the item among the outputs of this tier, 0 if the tier does not make it. The chance of an
     * item is its weight divided by the sum of the weights of all items the generator may make.
     */
    public int getWeight(Item item) {
        return outputs().weights().getOrDefault(item, 0);
    }

    private Outputs outputs() {
        Outputs outputs = resolvedOutputs;
        if (outputs == null) {
            outputs = resolveOutputs();
            resolvedOutputs = outputs;
        }
        return outputs;
    }

    private Outputs resolveOutputs() {
        Map<ResourceLocation, Integer> configured = PiConfig.outputWeights();
        Map<Item, Integer> weights = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : getOutputEntries().entrySet()) {
            Item item = resolve(entry.getKey());
            if (item == null) {
                missing.add(entry.getKey());
                continue;
            }
            Integer override = configured.get(ForgeRegistries.ITEMS.getKey(item));
            int weight = override != null ? override : entry.getValue();
            if (weight > 0) {
                weights.put(item, weight);
            } else {
                weights.remove(item); // weight 0: never made
            }
        }
        if (!missing.isEmpty()) {
            PiGenerator.LOGGER.info("{}: skipping outputs that are not registered (mod not installed?): {}", this, missing);
        }
        PiGenerator.LOGGER.debug("{}: resolved {} output item(s): {}", this, weights.size(), weights);
        return new Outputs(List.copyOf(weights.keySet()), Map.copyOf(weights));
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
