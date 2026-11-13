package com.projectinfinity.pigenerator.recipe;

import com.mojang.serialization.MapCodec;
import com.projectinfinity.pigenerator.PiConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Recipe condition {@code pi_generator:core_recipes}.
 *
 * <p>Enables the recipes of the tier 6-8 generators that need no Avaritia (the three cores and the
 * "eight cores around the previous generator" recipes). Follows {@code recipes.coreRecipes} in
 * {@code config/pi_generator-common.toml}:</p>
 * <ul>
 *   <li>{@code ALWAYS} (default): enabled, next to the Avaritia recipes if Avaritia is installed;</li>
 *   <li>{@code WITHOUT_AVARITIA}: enabled only while the Avaritia extreme crafting recipe type
 *       {@code avaritia:shaped_table} is not registered;</li>
 *   <li>{@code NEVER}: disabled.</li>
 * </ul>
 *
 * <pre>"neoforge:conditions": [{ "type": "pi_generator:core_recipes" }]</pre>
 */
public final class CoreRecipesCondition implements ICondition {
    public static final ResourceLocation EXTREME_SHAPED = ResourceLocation.fromNamespaceAndPath("avaritia", "shaped_table");
    public static final CoreRecipesCondition INSTANCE = new CoreRecipesCondition();
    public static final MapCodec<CoreRecipesCondition> CODEC = MapCodec.unit(INSTANCE);

    private CoreRecipesCondition() {
    }

    /** Whether Avaritia's 9x9 recipes can be read in this instance. */
    public static boolean hasExtremeCrafting() {
        return BuiltInRegistries.RECIPE_SERIALIZER.containsKey(EXTREME_SHAPED);
    }

    @Override
    public boolean test(IContext context) {
        return switch (PiConfig.coreRecipes()) {
            case ALWAYS -> true;
            case WITHOUT_AVARITIA -> !hasExtremeCrafting();
            case NEVER -> false;
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "core_recipes";
    }
}
