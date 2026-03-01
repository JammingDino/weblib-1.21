package com.jammingdino.web_lib.neoforge;

import com.jammingdino.web_lib.WebLib;
import com.jammingdino.web_lib.WebLibConfig;
import com.jammingdino.web_lib.screen.WebLibConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * NeoForge entry point for web_lib.
 *
 * Wires the NeoForge config system ({@link Config}) into the platform-agnostic
 * {@link WebLibConfig} so all common code can read/write config values without
 * depending on NeoForge APIs.
 */
@Mod(WebLib.MODID)
public class WebLibNeoForge {

    public WebLibNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        // Register NeoForge config spec (reads/writes the TOML file)
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register the config screen via NeoForge's extension point
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> new WebLibConfigScreen(parent)
        );

        // Wire the NeoForge config backing store into WebLibConfig
        WebLibConfig.defaultFontSizeGetter = Config.DEFAULT_FONT_SIZE::getAsInt;
        WebLibConfig.defaultFontSizeSetter = Config.DEFAULT_FONT_SIZE::set;

        WebLibConfig.logCssWarningsGetter  = Config.LOG_CSS_WARNINGS::getAsBoolean;
        WebLibConfig.logCssWarningsSetter  = Config.LOG_CSS_WARNINGS::set;

        WebLibConfig.logScriptCallsGetter  = Config.LOG_SCRIPT_CALLS::getAsBoolean;
        WebLibConfig.logScriptCallsSetter  = Config.LOG_SCRIPT_CALLS::set;

        WebLibConfig.maxHistoryGetter      = Config.MAX_HISTORY::getAsInt;
        WebLibConfig.maxHistorySetter      = Config.MAX_HISTORY::set;

        WebLibConfig.scrollSpeedGetter     = Config.SCROLL_SPEED::get;
        WebLibConfig.scrollSpeedSetter     = Config.SCROLL_SPEED::set;

        WebLibConfig.saveImpl              = Config::save;
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        WebLib.LOGGER.info("[web_lib] Common setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        WebLib.LOGGER.info("[web_lib] Server starting");
    }

    @EventBusSubscriber(modid = WebLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {

        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            WebLib.LOGGER.info("[web_lib] Client setup complete – HTML renderer ready");
        }
    }
}
