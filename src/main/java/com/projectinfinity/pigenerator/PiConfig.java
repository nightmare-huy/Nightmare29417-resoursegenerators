package com.projectinfinity.pigenerator;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common config ({@code config/pi_generator-common.toml}). */
public final class PiConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue FORCE_FALLBACK_RECIPES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("recipes");
        FORCE_FALLBACK_RECIPES = builder
                .comment("Tier 6-8 generators are normally crafted in the Avaritia 9x9 extreme crafting table",
                        "(recipe type 'avaritia:shaped_table'). When that recipe type is not registered, the 3x3",
                        "crafting table fallback recipes are enabled automatically.",
                        "Set this to true to always enable the 3x3 fallback recipes as well, e.g. if your Avaritia",
                        "version uses a different JSON format and the 9x9 recipes fail to load.",
                        "Restart the game / server after changing.")
                .define("forceFallbackRecipes", false);
        builder.pop();
        SPEC = builder.build();
    }

    /** Safe accessor: returns {@code false} when the config has not been loaded yet. */
    public static boolean forceFallbackRecipes() {
        try {
            return FORCE_FALLBACK_RECIPES.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    private PiConfig() {
    }
}
