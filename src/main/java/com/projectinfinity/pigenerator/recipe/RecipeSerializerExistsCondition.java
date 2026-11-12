package com.projectinfinity.pigenerator.recipe;

import com.google.gson.JsonObject;
import com.projectinfinity.pigenerator.PiGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Recipe condition {@code pi_generator:recipe_serializer_exists}.
 *
 * <p>True when the given recipe type (serializer) is registered, e.g. {@code avaritia:shaped_table}.
 * Used so the 9x9 Avaritia recipes are only parsed when Avaritia can actually read them.</p>
 *
 * <pre>{ "type": "pi_generator:recipe_serializer_exists", "serializer": "avaritia:shaped_table" }</pre>
 */
public class RecipeSerializerExistsCondition implements ICondition {
    public static final ResourceLocation NAME = new ResourceLocation(PiGenerator.MODID, "recipe_serializer_exists");

    private final ResourceLocation serializer;

    public RecipeSerializerExistsCondition(ResourceLocation serializer) {
        this.serializer = serializer;
    }

    @Override
    public ResourceLocation getID() {
        return NAME;
    }

    @Override
    public boolean test(IContext context) {
        return ForgeRegistries.RECIPE_SERIALIZERS.containsKey(serializer);
    }

    @Override
    public String toString() {
        return "recipe_serializer_exists(\"" + serializer + "\")";
    }

    public static class Serializer implements IConditionSerializer<RecipeSerializerExistsCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, RecipeSerializerExistsCondition value) {
            json.addProperty("serializer", value.serializer.toString());
        }

        @Override
        public RecipeSerializerExistsCondition read(JsonObject json) {
            return new RecipeSerializerExistsCondition(new ResourceLocation(GsonHelper.getAsString(json, "serializer")));
        }

        @Override
        public ResourceLocation getID() {
            return NAME;
        }
    }
}
