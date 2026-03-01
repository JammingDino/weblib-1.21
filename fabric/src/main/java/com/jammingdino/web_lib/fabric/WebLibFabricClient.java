package com.jammingdino.web_lib.fabric;

import com.jammingdino.web_lib.WebLib;
import com.jammingdino.web_lib.screen.WebLibConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Fabric client-side entry point for web_lib.
 *
 * Registers the config screen with Mod Menu (optional dependency) so that
 * players can access web_lib settings from the in-game mod list.
 */
@Environment(EnvType.CLIENT)
public class WebLibFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WebLib.LOGGER.info("[web_lib] Client setup complete – HTML renderer ready (Fabric)");

        // Register config screen with Mod Menu (soft dependency)
        registerModMenuConfigScreen();
    }

    private void registerModMenuConfigScreen() {
        try {
            Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
            ModMenuCompat.register();
        } catch (ClassNotFoundException ignored) {
            // Mod Menu not installed – config screen simply won't be shown in the mod list
        }
    }
}
