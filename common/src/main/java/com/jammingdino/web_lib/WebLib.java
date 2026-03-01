package com.jammingdino.web_lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Shared constants for web_lib.
 *
 * Platform-specific initialization lives in the {@code neoforge} and {@code fabric}
 * subprojects. All common code references constants from this class only.
 */
public final class WebLib {

    private WebLib() {}

    public static final String MODID   = "web_lib";

    /**
     * Mod version read from the {@code web_lib_version.txt} classpath resource,
     * which has the real version token substituted by ProcessResources at build time.
     */
    public static final String VERSION = readVersion();

    public static final Logger LOGGER  = LoggerFactory.getLogger(MODID);

    private static String readVersion() {
        try (InputStream is = WebLib.class.getResourceAsStream("/web_lib_version.txt")) {
            if (is != null) return new String(is.readAllBytes()).trim();
        } catch (Exception ignored) {
            // Fall through to default
        }
        return "?";
    }
}
