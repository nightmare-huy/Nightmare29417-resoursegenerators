package com.projectinfinity.pigenerator.recipe;

import com.google.gson.JsonObject;
import com.projectinfinity.pigenerator.PiConfig;
import com.projectinfinity.pigenerator.PiGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Recipe condition {@code pi_generator:fallback_recipes}.
 *
 * <p>Enables the 3x3 crafting table fallback recipes of the tier 6-8 generators. True when</p>
 * <ul>
 *   <li>the Avaritia extreme crafting recipe type {@code avaritia:shaped_table} is not registered
 *       (Avaritia missing, or a port with a different recipe format), or</li>
 *   <li>{@code forceFallbackRecipes = true} is set in {@code config/pi_generator-common.toml}.</li>
 * </ul>
 *
 * <pre>{ "type": "pi_generator:fallback_recipes" }</pre>
 */
public class FallbackRecipesCondition implements ICondition {
    public static final ResourceLocation NAME = new ResourceLocation(PiGenerator.MODID, "fallback_recipes");
    public static final ResourceLocation EXTREME_SHAPED = new ResourceLocation("avaritia", "shaped_table");
    public static final FallbackRecipesCondition INSTANCE = new FallbackRecipesCondition();

    private FallbackRecipesCondition() {
    }

    @Override
    public ResourceLocation getID() {
        return NAME;
    }

    @Override
    public boolean test(IContext context) {
        return PiConfig.forceFallbackRecipes() || !ForgeRegistries.RECIPE_SERIALIZERS.containsKey(EXTREME_SHAPED);
    }

    @Override
    public String toString() {
        return "fallback_recipes";
    }

    public static class Serializer implements IConditionSerializer<FallbackRecipesCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, FallbackRecipesCondition value) {
        }

        @Override
        public FallbackRecipesCondition read(JsonObject json) {
            return FallbackRecipesCondition.INSTANCE;
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
