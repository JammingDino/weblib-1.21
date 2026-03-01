package com.jammingdino.web_lib;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Platform-agnostic configuration for web_lib.
 *
 * Each platform's init code sets the supplier/consumer pairs and {@code saveImpl}
 * so that this class always reads and writes to the right backing store, whether
 * that is a NeoForge {@code ModConfigSpec} or a Fabric JSON file.
 */
public final class WebLibConfig {

    private WebLibConfig() {}

    // ── Getters and setters wired up by platform init ──

    public static IntSupplier   defaultFontSizeGetter = () -> 8;
    public static IntConsumer   defaultFontSizeSetter = v -> {};

    public static Supplier<Boolean>  logCssWarningsGetter = () -> false;
    public static Consumer<Boolean>  logCssWarningsSetter = v -> {};

    public static Supplier<Boolean>  logScriptCallsGetter = () -> false;
    public static Consumer<Boolean>  logScriptCallsSetter = v -> {};

    public static IntSupplier   maxHistoryGetter = () -> 20;
    public static IntConsumer   maxHistorySetter = v -> {};

    public static DoubleSupplier scrollSpeedGetter = () -> 10.0;
    public static DoubleConsumer scrollSpeedSetter = v -> {};

    /** Called by the platform init to actually persist values to disk. */
    public static Runnable saveImpl = () -> {};

    // ── Public API used by common code ──

    public static int getDefaultFontSize()     { return defaultFontSizeGetter.getAsInt(); }
    public static void setDefaultFontSize(int v)   { defaultFontSizeSetter.accept(v); }

    public static boolean isLogCssWarnings()   { return logCssWarningsGetter.get(); }
    public static void setLogCssWarnings(boolean v) { logCssWarningsSetter.accept(v); }

    public static boolean isLogScriptCalls()   { return logScriptCallsGetter.get(); }
    public static void setLogScriptCalls(boolean v) { logScriptCallsSetter.accept(v); }

    public static int getMaxHistory()          { return maxHistoryGetter.getAsInt(); }
    public static void setMaxHistory(int v)    { maxHistorySetter.accept(v); }

    public static double getScrollSpeed()      { return scrollSpeedGetter.getAsDouble(); }
    public static void setScrollSpeed(double v){ scrollSpeedSetter.accept(v); }

    /** Persist all current values to disk via the platform's backing store. */
    public static void save() { saveImpl.run(); }
}
