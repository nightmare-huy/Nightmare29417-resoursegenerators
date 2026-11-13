package com.projectinfinity.pigenerator;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Common config ({@code config/pi_generator-common.toml}). */
public final class PiConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<CoreRecipes> CORE_RECIPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OUTPUT_WEIGHTS;

    /** When the recipes of the tier 6-8 generators that need no Avaritia are enabled. */
    public enum CoreRecipes {
        ALWAYS, WITHOUT_AVARITIA, NEVER
    }

    /** The largest weight that can be given to an item. */
    public static final int MAX_WEIGHT = 1000;

    /** {@link #OUTPUT_WEIGHTS} as a map, read once: the option needs a restart anyway. */
    private static volatile Map<ResourceLocation, Integer> outputWeights;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("recipes");
        CORE_RECIPES = builder
                .comment("Tier 6-8 generators have two kinds of recipes:",
                        " - in the Avaritia 9x9 extreme crafting table (recipe type 'avaritia:shaped_table'), loaded",
                        "   only when Avaritia is installed;",
                        " - in the normal crafting table, without anything from Avaritia: eight cores (crystal /",
                        "   neutron / infinity core) around the generator of the tier below. More expensive.",
                        "This option decides when the recipes without Avaritia (the cores and the generators made",
                        "from them) are enabled:",
                        "  ALWAYS            - always, next to the Avaritia recipes if Avaritia is installed",
                        "  WITHOUT_AVARITIA  - only when Avaritia is not installed",
                        "  NEVER             - never: tier 6-8 can be crafted with Avaritia only",
                        "Restart the game / server after changing.")
                .defineEnum("coreRecipes", CoreRecipes.ALWAYS);
        builder.pop();
        builder.push("outputs");
        OUTPUT_WEIGHTS = builder
                .comment("A generator picks what to make at random. Every resource has a weight: its chance is the",
                        "weight divided by the sum of the weights of all resources the generator may make (the",
                        "resources of its tier that pass the installed filter). The default weights are listed in",
                        "the README of the mod, from 10 for the cheapest resources down to 1 for the most valuable.",
                        "This list replaces the weight of single items, one \"item id=weight\" entry per item:",
                        "    weights = [\"minecraft:diamond=10\", \"minecraft:coal=0\"]",
                        "Weights are whole numbers from 0 to " + MAX_WEIGHT + "; 0 means that the item is never made.",
                        "A weight applies to the item in every generator tier that makes it.",
                        "Restart the game / server after changing.")
                .defineListAllowEmpty("weights", List.of(), () -> "minecraft:diamond=10", entry -> entry instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    /** Safe accessor: returns the default while the config has not been loaded yet. */
    public static CoreRecipes coreRecipes() {
        try {
            return CORE_RECIPES.get();
        } catch (IllegalStateException notLoadedYet) {
            return CoreRecipes.ALWAYS;
        }
    }

    /**
     * Weights from the config by item id. Entries that cannot be read are reported in the log (once) and
     * skipped. Empty when the config has not been loaded yet.
     */
    public static Map<ResourceLocation, Integer> outputWeights() {
        Map<ResourceLocation, Integer> cached = outputWeights;
        if (cached != null) {
            return cached;
        }
        List<? extends String> entries;
        try {
            entries = OUTPUT_WEIGHTS.get();
        } catch (IllegalStateException notLoadedYet) {
            return Map.of();
        }
        Map<ResourceLocation, Integer> weights = new HashMap<>();
        for (String entry : entries) {
            int separator = entry.lastIndexOf('=');
            ResourceLocation id = separator > 0 ? ResourceLocation.tryParse(entry.substring(0, separator).trim()) : null;
            Integer weight = null;
            if (id != null) {
                try {
                    int value = Integer.parseInt(entry.substring(separator + 1).trim());
                    if (value >= 0 && value <= MAX_WEIGHT) {
                        weight = value;
                    }
                } catch (NumberFormatException notANumber) {
                    // reported below
                }
            }
            if (weight == null) {
                PiGenerator.LOGGER.warn("Config outputs.weights: ignoring '{}', expected \"item id=weight\" with a weight of 0..{}",
                        entry, MAX_WEIGHT);
            } else {
                weights.put(id, weight);
            }
        }
        cached = Map.copyOf(weights);
        outputWeights = cached;
        return cached;
    }

    private PiConfig() {
    }
}
