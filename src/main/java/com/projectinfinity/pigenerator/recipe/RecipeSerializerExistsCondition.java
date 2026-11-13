package com.projectinfinity.pigenerator.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Recipe condition {@code pi_generator:recipe_serializer_exists}.
 *
 * <p>True when the given recipe type (serializer) is registered, e.g. {@code avaritia:shaped_table}.
 * Used so the 9x9 Avaritia recipes are only read when Avaritia is installed and can read them.</p>
 *
 * <pre>"neoforge:conditions": [{ "type": "pi_generator:recipe_serializer_exists", "serializer": "avaritia:shaped_table" }]</pre>
 */
public record RecipeSerializerExistsCondition(ResourceLocation serializer) implements ICondition {
    public static final MapCodec<RecipeSerializerExistsCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("serializer").forGetter(RecipeSerializerExistsCondition::serializer)
    ).apply(instance, RecipeSerializerExistsCondition::new));

    @Override
    public boolean test(IContext context) {
        return BuiltInRegistries.RECIPE_SERIALIZER.containsKey(serializer);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "recipe_serializer_exists(\"" + serializer + "\")";
    }
}
