package com.jammingdino.web_lib.screen;

/**
 * Generates the HTML for the web_lib config screen using Java text blocks
 * to avoid quote-escaping issues with HTML attributes inside Java strings.
 */
public class ConfigHtml {

    public static String build(
            int defaultFontSize,
            boolean logCssWarnings,
            boolean logScriptCalls,
            int maxHistory
    ) {
        String btnOff     = "background-color:#2a2a2a;border:1px solid #555;color:#aaa;padding:4px 12px;cursor:pointer;";
        String btnOn      = "background-color:#3d2a6e;border:1px solid #a87de8;color:#e0d0ff;padding:4px 12px;cursor:pointer;";
        String btnPrimary = "background-color:#3d2a6e;border:1px solid #a87de8;color:#e0d0ff;padding:5px 20px;cursor:pointer;";
        String btnCancel  = "background-color:#2a2a2a;border:1px solid #555;color:#ccc;padding:5px 20px;cursor:pointer;margin-right:8px;";

        String cssWarnStyle  = logCssWarnings  ? btnOn : btnOff;
        String cssWarnLabel  = logCssWarnings  ? "ON"  : "OFF";
        String scriptStyle   = logScriptCalls  ? btnOn : btnOff;
        String scriptLabel   = logScriptCalls  ? "ON"  : "OFF";

        return """
            <!DOCTYPE html>
            <html>
            <head><style>
            body { background-color:#1a1a1a; color:#e0e0e0; font-size:9px; margin:0; padding:0; }
            .titlebar { background-color:#111; border-bottom:1px solid #5a3e8a; padding:10px 14px; }
            .titlebar h1 { font-size:13px; color:#a87de8; margin:0; font-weight:bold; }
            .titlebar p  { color:#666; font-size:8px; margin-top:3px; }
            .content { padding:12px 14px; }
            .section-title { font-size:8px; color:#7a5cb8; border-bottom:1px solid #2e2e2e;
                padding-bottom:3px; margin-top:14px; margin-bottom:8px; font-weight:bold; }
            .card { background-color:#212121; border:1px solid #2e2e2e;
                padding:8px 10px; margin-bottom:8px; }
            .card-title { font-weight:bold; color:#ccc; font-size:9px; margin-bottom:3px; }
            .card-desc  { color:#666; font-size:7px; margin-bottom:7px; }
            input { background-color:#2a2a2a; border:1px solid #444; color:#e0e0e0;
                padding:3px 5px; font-size:9px; width:55px; }
            .error { color:#e07070; font-size:7px; margin-top:4px; display:none; }
            .footer { border-top:1px solid #2e2e2e; background-color:#111;
                padding:8px 14px; margin-top:8px; }
            </style></head>
            <body>

            <div class="titlebar">
              <h1>web_lib Configuration</h1>
              <p>Changes are saved to the config file when you click Save.</p>
            </div>

            <div class="content">

              <div class="section-title">RENDERING</div>
              <div class="card">
                <div class="card-title">Default Font Size</div>
                <div class="card-desc">Base font size in pixels when no CSS font-size is set. Minecraft default is 8px. Range: 6 to 32</div>
                <input type="number" id="defaultFontSize" value="%d" onchange="weblib.setFontSize(this.value)" />
                <div class="error" id="fontSizeError">Must be between 6 and 32.</div>
              </div>

              <div class="section-title">DEBUG AND LOGGING</div>
              <div class="card">
                <div class="card-title">Log CSS Warnings</div>
                <div class="card-desc">Print CSS parse warnings to the game log.</div>
                <button id="logCssWarningsBtn" onclick="weblib.toggleLogCssWarnings()" style="%s">%s</button>
              </div>
              <div class="card">
                <div class="card-title">Log Script Calls</div>
                <div class="card-desc">Print every JavaScript bridge call to the game log.</div>
                <button id="logScriptCallsBtn" onclick="weblib.toggleLogScriptCalls()" style="%s">%s</button>
              </div>

              <div class="section-title">NAVIGATION</div>
              <div class="card">
                <div class="card-title">Max History Depth</div>
                <div class="card-desc">Pages to keep in back/forward stack per WebScreen. Range: 1 to 100</div>
                <input type="number" id="maxHistory" value="%d" onchange="weblib.setMaxHistory(this.value)" />
                <div class="error" id="maxHistoryError">Must be between 1 and 100.</div>
              </div>

            </div>

            <div class="footer">
              <button onclick="weblib.cancel()" style="%s">Cancel</button>
              <button onclick="weblib.save()"   style="%s">Save and Close</button>
            </div>

            </body></html>
            """.formatted(
                defaultFontSize,
                cssWarnStyle,  cssWarnLabel,
                scriptStyle,   scriptLabel,
                maxHistory,
                btnCancel,
                btnPrimary
        );
    }
}