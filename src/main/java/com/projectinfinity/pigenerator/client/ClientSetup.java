package com.projectinfinity.pigenerator.client;

import com.projectinfinity.pigenerator.ModRegistry;
import com.projectinfinity.pigenerator.PiGenerator;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client only setup: connects the menus to their screens. Never loaded on a dedicated server. */
@Mod.EventBusSubscriber(modid = PiGenerator.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // MenuScreens is not thread safe, mod setup runs in parallel
        event.enqueueWork(() -> {
            MenuScreens.register(ModRegistry.GENERATOR_MENU.get(), GeneratorScreen::new);
            MenuScreens.register(ModRegistry.FILTER_MENU.get(), FilterScreen::new);
        });
    }

    private ClientSetup() {
    }
}
