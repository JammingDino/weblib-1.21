package com.jammingdino.web_lib.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.jammingdino.web_lib.screen.WebLibConfigScreen;

/**
 * Mod Menu integration – provides a config screen entry when Mod Menu is installed.
 *
 * This class is only loaded when Mod Menu is present (guarded by a try/catch in
 * {@link WebLibFabricClient#registerModMenuConfigScreen()}).
 */
public class ModMenuCompat implements ModMenuApi {

    public static void register() {
        // Mod Menu discovers implementations via entrypoints defined in fabric.mod.json.
        // The static call here is just a no-op hook used to trigger class loading.
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WebLibConfigScreen::new;
    }
}
