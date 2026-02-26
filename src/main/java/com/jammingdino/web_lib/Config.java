package com.jammingdino.web_lib;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for web_lib.
 */
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Default font size (px) used when no CSS font-size is specified. */
    public static final ModConfigSpec.IntValue DEFAULT_FONT_SIZE = BUILDER
            .comment("Default font size in pixels when no CSS font-size is specified")
            .defineInRange("defaultFontSize", 8, 6, 32);

    /** Whether to log CSS parsing warnings. */
    public static final ModConfigSpec.BooleanValue LOG_CSS_WARNINGS = BUILDER
            .comment("Log CSS parsing warnings to the console")
            .define("logCssWarnings", false);

    /** Whether to log script evaluation calls. */
    public static final ModConfigSpec.BooleanValue LOG_SCRIPT_CALLS = BUILDER
            .comment("Log JavaScript bridge calls (useful for debugging mod HTML pages)")
            .define("logScriptCalls", false);

    /** Maximum number of pages to keep in back/forward history. */
    public static final ModConfigSpec.IntValue MAX_HISTORY = BUILDER
            .comment("Maximum back/forward navigation history depth per WebScreen")
            .defineInRange("maxHistory", 20, 1, 100);

    /** Scroll speed multiplier. Default is 10.0. */
    public static final ModConfigSpec.DoubleValue SCROLL_SPEED = BUILDER
            .comment("Mouse wheel scroll sensitivity")
            .defineInRange("scrollSpeed", 10.0, 1.0, 50.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Flush all current values to the config file on disk. */
    public static void save() {
        SPEC.save();
    }
}