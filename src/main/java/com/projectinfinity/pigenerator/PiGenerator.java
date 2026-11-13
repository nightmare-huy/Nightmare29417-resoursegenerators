package com.projectinfinity.pigenerator;

import com.mojang.logging.LogUtils;
import com.projectinfinity.pigenerator.client.ClientSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Project Infinity: Resource Generators (NeoForge, Minecraft 1.21.1).
 *
 * <p>Adds eight tiers of FE-powered resource generators. The mod has no compile-time dependency on
 * Mekanism, AE2, Avaritia or any ore mod: their items are resolved at runtime by registry id.</p>
 */
@Mod(PiGenerator.MODID)
public class PiGenerator {
    public static final String MODID = "pi_generator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PiGenerator(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(ModRegistry::registerCapabilities);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // the only place that touches client classes, never reached on a dedicated server
            modBus.addListener(ClientSetup::registerScreens);
        }

        container.registerConfig(ModConfig.Type.COMMON, PiConfig.SPEC);
    }
}
