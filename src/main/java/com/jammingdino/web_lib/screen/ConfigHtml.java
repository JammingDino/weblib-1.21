package com.jammingdino.web_lib.screen;

import java.util.Map;

/**
 * Generates the HTML for the web_lib config screen by loading the template
 * from {@code assets/web_lib/screens/config.html} and substituting values.
 */
public class ConfigHtml {

    private static final String TEMPLATE_PATH = "assets/web_lib/screens/config.html";

    public static String build(
            int defaultFontSize,
            boolean logCssWarnings,
            boolean logScriptCalls,
            int maxHistory,
            double scrollSpeed
    ) {
        String btnBase    = "display:inline-block;border:1px solid #555;padding:6px 16px;cursor:pointer;font-size:9px;";
        String btnOff     = btnBase + "background-color:#2a2a2a;color:#aaa;";
        String btnOn      = btnBase + "background-color:#3d2a6e;border-color:#a87de8;color:#e0d0ff;";

        String btnPrimary = btnBase + "background-color:#3d2a6e;border-color:#a87de8;color:#e0d0ff;font-weight:bold;";
        String btnCancel  = btnBase + "background-color:#2a2a2a;color:#ccc;";
        String btnReset   = btnBase + "background-color:#2a1800;border-color:#664400;color:#cc8844;";

        return ScreenHtmlLoader.load(TEMPLATE_PATH, Map.of(
                "defaultFontSize", String.valueOf(defaultFontSize),
                "cssWarnStyle",    logCssWarnings ? btnOn : btnOff,
                "cssWarnLabel",    logCssWarnings ? "ON" : "OFF",
                "scriptStyle",     logScriptCalls ? btnOn : btnOff,
                "scriptLabel",     logScriptCalls ? "ON" : "OFF",
                "maxHistory",      String.valueOf(maxHistory),
                "scrollSpeed",     String.format("%.1f", scrollSpeed),
                "btnCancel",       btnCancel,
                "btnReset",        btnReset,
                "btnPrimary",      btnPrimary
        ));
    }
}