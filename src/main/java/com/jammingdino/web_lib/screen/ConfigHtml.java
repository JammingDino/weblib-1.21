package com.jammingdino.web_lib.screen;

/**
 * Generates the HTML for the web_lib config screen.
 * Fixed: Escaped CSS percent signs (%%) and simplified layout for stability.
 */
public class ConfigHtml {

    public static String build(
            int defaultFontSize,
            boolean logCssWarnings,
            boolean logScriptCalls,
            int maxHistory,
            double scrollSpeed
    ) {
        // Base button styles
        String btnBase    = "border:1px solid #555; padding:6px 16px; cursor:pointer; font-size:9px;";
        String btnOff     = btnBase + "background-color:#2a2a2a; color:#aaa;";
        String btnOn      = btnBase + "background-color:#3d2a6e; border-color:#a87de8; color:#e0d0ff;";

        String btnPrimary = btnBase + "background-color:#3d2a6e; border-color:#a87de8; color:#e0d0ff; font-weight:bold;";
        String btnCancel  = btnBase + "background-color:#2a2a2a; color:#ccc; margin-right:10px;";

        String cssWarnStyle  = logCssWarnings  ? btnOn : btnOff;
        String cssWarnLabel  = logCssWarnings  ? "ON"  : "OFF";
        String scriptStyle   = logScriptCalls  ? btnOn : btnOff;
        String scriptLabel   = logScriptCalls  ? "ON"  : "OFF";

        // Note: CSS values like '100%' must be written as '100%%' in Java formatted strings.
        return """
            <!DOCTYPE html>
            <html>
            <head><style>
            body { 
                background-color:#1a1a1a; 
                color:#e0e0e0; 
                font-family:minecraft; 
                margin:0; 
                padding:0;
                /* Standard vertical flow */
                display: block;
            }
            .titlebar { 
                background-color:#111; 
                border-bottom:1px solid #5a3e8a; 
                padding:12px 20px; 
                margin-bottom: 10px;
            }
            .titlebar h1 { font-size:14px; color:#a87de8; margin:0; margin-bottom:4px; font-weight:bold; }
            .titlebar p  { color:#888; font-size:8px; margin:0; }
            
            .content {
                padding: 0 20px 20px 20px;
            }

            .section-title { 
                font-size:9px; 
                color:#9b7ad6; 
                border-bottom:1px solid #333;
                padding-bottom:4px; 
                margin-top:15px; 
                margin-bottom:10px; 
                font-weight:bold; 
            }
            
            /* Flex column card: This guarantees the controls render BELOW the text */
            .card { 
                background-color:#212121; 
                border:1px solid #2e2e2e;
                padding:10px; 
                margin-bottom:10px; 
                display: flex;
                flex-direction: column; 
                gap: 6px;
            }
            
            .card-title { font-weight:bold; color:#ddd; font-size:10px; }
            .card-desc  { color:#888; font-size:8px; line-height: 1.2; }
            
            .control-row {
                margin-top: 4px;
                display: block;
            }

            input { 
                background-color:#111; 
                border:1px solid #444; 
                color:#fff;
                padding:4px 6px; 
                font-size:9px; 
                width:60px; 
            }
            .error { color:#ff5555; font-size:8px; display:none; margin-top: 4px; }

            .footer { 
                border-top:1px solid #2e2e2e; 
                background-color:#111;
                padding:12px; 
                margin-top: 10px;
                text-align: center; /* Centers the buttons */
                display: block;
            }
            </style></head>
            <body>

            <div class="titlebar">
              <h1>web_lib Configuration</h1>
              <p>Adjust the browser engine settings.</p>
            </div>

            <div class="content">

              <div class="section-title">RENDERING</div>
              
              <div class="card">
                <div class="card-title">Default Font Size</div>
                <div class="card-desc">Base font size in pixels. Minecraft default is 8px. Range: 6-32.</div>
                <div class="control-row">
                    <input type="number" id="defaultFontSize" value="%d" onchange="weblib.setFontSize(this.value)" />
                    <div class="error" id="fontSizeError">Invalid range (6-32)</div>
                </div>
              </div>

              <div class="section-title">DEBUG AND LOGGING</div>
              
              <div class="card">
                <div class="card-title">Log CSS Warnings</div>
                <div class="card-desc">Print CSS parse warnings to the game log.</div>
                <div class="control-row">
                    <button id="logCssWarningsBtn" onclick="weblib.toggleLogCssWarnings()" style="%s">%s</button>
                </div>
              </div>
              
              <div class="card">
                <div class="card-title">Log Script Calls</div>
                <div class="card-desc">Print every JavaScript bridge call to the game log.</div>
                <div class="control-row">
                    <button id="logScriptCallsBtn" onclick="weblib.toggleLogScriptCalls()" style="%s">%s</button>
                </div>
              </div>

              <div class="section-title">NAVIGATION</div>
              
              <div class="card">
                <div class="card-title">Max History Depth</div>
                <div class="card-desc">Pages to keep in history per screen. Range: 1-100.</div>
                <div class="control-row">
                    <input type="number" id="maxHistory" value="%d" onchange="weblib.setMaxHistory(this.value)" />
                    <div class="error" id="maxHistoryError">Invalid range (1-100)</div>
                </div>
              </div>
              
              <div class="card">
                <div class="card-title">Scroll Speed</div>
                <div class="card-desc">Mouse wheel sensitivity. Default is 10. Range: 1-50.</div>
                <div class="control-row">
                    <input type="number" id="scrollSpeed" value="%.1f" onchange="weblib.setScrollSpeed(this.value)" />
                    <div class="error" id="scrollSpeedError">Invalid range (1-50)</div>
                </div>
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
                scrollSpeed,
                btnCancel,
                btnPrimary
        );
    }
}