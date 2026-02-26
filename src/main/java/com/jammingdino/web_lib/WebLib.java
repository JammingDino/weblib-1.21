package com.jammingdino.web_lib;

import com.jammingdino.web_lib.screen.WebLibConfigScreen;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

/**
 * web_lib – A browser-like HTML/CSS/JS GUI framework for NeoForge mods.
 *
 * Other mods integrate via {@link com.jammingdino.web_lib.api.WebLibApi}.
 */
@Mod(WebLib.MODID)
public class WebLib {

    public static final String MODID  = "web_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WebLib(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        // Register config spec so NeoForge reads/writes the config file
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register our own config screen – rendered by web_lib itself
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> new WebLibConfigScreen(parent)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[web_lib] Common setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[web_lib] Server starting");
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {

        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[web_lib] Client setup complete – HTML renderer ready");
        }
    }
}