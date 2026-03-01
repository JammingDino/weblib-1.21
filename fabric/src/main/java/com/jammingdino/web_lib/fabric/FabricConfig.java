package com.jammingdino.web_lib.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jammingdino.web_lib.WebLib;
import com.jammingdino.web_lib.WebLibConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric JSON-backed configuration for web_lib.
 *
 * Values are stored in {@code <config_dir>/web_lib.json} and wired into the
 * platform-agnostic {@link WebLibConfig} so common code can read/write them
 * without depending on Fabric APIs.
 */
public class FabricConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "web_lib.json";

    // ── Config fields (must be public for Gson) ──

    public int    defaultFontSize = 8;
    public boolean logCssWarnings = false;
    public boolean logScriptCalls = false;
    public int    maxHistory      = 20;
    public double scrollSpeed     = 10.0;

    // ── Load / save ──

    static FabricConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                FabricConfig cfg = GSON.fromJson(reader, FabricConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException e) {
                WebLib.LOGGER.warn("[web_lib] Failed to read config file, using defaults", e);
            }
        }
        FabricConfig defaults = new FabricConfig();
        defaults.save(); // write defaults on first run
        return defaults;
    }

    void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            WebLib.LOGGER.warn("[web_lib] Failed to write config file", e);
        }
    }

    /** Wire this instance's fields into the platform-agnostic {@link WebLibConfig}. */
    void wire() {
        WebLibConfig.defaultFontSizeGetter = () -> this.defaultFontSize;
        WebLibConfig.defaultFontSizeSetter = v  -> this.defaultFontSize = v;

        WebLibConfig.logCssWarningsGetter  = () -> this.logCssWarnings;
        WebLibConfig.logCssWarningsSetter  = v  -> this.logCssWarnings = v;

        WebLibConfig.logScriptCallsGetter  = () -> this.logScriptCalls;
        WebLibConfig.logScriptCallsSetter  = v  -> this.logScriptCalls = v;

        WebLibConfig.maxHistoryGetter      = () -> this.maxHistory;
        WebLibConfig.maxHistorySetter      = v  -> this.maxHistory = v;

        WebLibConfig.scrollSpeedGetter     = () -> this.scrollSpeed;
        WebLibConfig.scrollSpeedSetter     = v  -> this.scrollSpeed = v;

        WebLibConfig.saveImpl              = this::save;
    }
}
