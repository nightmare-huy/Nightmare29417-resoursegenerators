package com.projectinfinity.pigenerator.client;

import com.projectinfinity.pigenerator.ModRegistry;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client only setup: connects the menus to their screens. Never loaded on a dedicated server. */
public final class ClientSetup {
    /** Listener on the mod event bus, added by the mod constructor on the client only. */
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.GENERATOR_MENU.get(), GeneratorScreen::new);
        event.register(ModRegistry.FILTER_MENU.get(), FilterScreen::new);
        event.register(ModRegistry.ENERGY_STORAGE_MENU.get(), EnergyStorageScreen::new);
    }

    private ClientSetup() {
    }
}
