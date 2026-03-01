package com.jammingdino.web_lib.fabric;

import com.jammingdino.web_lib.WebLib;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric common-side entry point for web_lib.
 */
public class WebLibFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Wire the Fabric JSON config backing store into WebLibConfig
        FabricConfig cfg = FabricConfig.load();
        cfg.wire();

        WebLib.LOGGER.info("[web_lib] Common setup complete (Fabric)");
    }
}
