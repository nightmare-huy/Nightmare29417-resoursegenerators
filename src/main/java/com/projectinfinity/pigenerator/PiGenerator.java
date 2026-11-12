package com.projectinfinity.pigenerator;

import com.mojang.logging.LogUtils;
import com.projectinfinity.pigenerator.recipe.FallbackRecipesCondition;
import com.projectinfinity.pigenerator.recipe.RecipeSerializerExistsCondition;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * Project Infinity: Resource Generators.
 *
 * <p>Adds eight tiers of FE-powered resource generators. The mod has no compile-time dependency on
 * Mekanism, AE2, Thermal or Avaritia: their items are resolved at runtime by registry id.</p>
 */
@Mod(PiGenerator.MODID)
public class PiGenerator {
    public static final String MODID = "pi_generator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PiGenerator() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModRegistry.register(modBus);
        modBus.addListener(PiGenerator::onRegister);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PiConfig.SPEC);
    }

    /** Recipe conditions have no registry of their own, Forge registers its own ones at this point too. */
    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
            CraftingHelper.register(RecipeSerializerExistsCondition.Serializer.INSTANCE);
            CraftingHelper.register(FallbackRecipesCondition.Serializer.INSTANCE);
        }
    }
}
