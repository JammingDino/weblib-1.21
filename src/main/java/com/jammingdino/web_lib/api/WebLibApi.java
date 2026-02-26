package com.jammingdino.web_lib.api;

import com.jammingdino.web_lib.screen.WebPage;
import com.jammingdino.web_lib.screen.WebScreen;
import com.jammingdino.web_lib.script.ScriptBridge;
import net.minecraft.client.Minecraft;

/**
 * Public API for the {@code web_lib} mod.
 *
 * Other mods should interact exclusively through this class rather than
 * depending on internal implementation classes directly.
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // 1. Open a simple HTML page
 * WebLibApi.openPage("<h1>Hello from MyMod!</h1><p>This is rendered HTML.</p>");
 *
 * // 2. Register a Java function callable from HTML onclick="..."
 * WebLibApi.registerFunction("myMod.openInventory", (args, ctx) -> {
 *     Minecraft.getInstance().tell(() ->
 *         Minecraft.getInstance().setScreen(new InventoryScreen(...)));
 *     return null;
 * });
 *
 * // 3. Open a page with navigation
 * WebLibApi.openPage(homeHtml, url -> switch (url) {
 *     case "/settings" -> settingsHtml;
 *     case "/shop"     -> shopHtml;
 *     default          -> null;  // cancel navigation
 * });
 * }</pre>
 *
 * <h2>Custom Syntax in HTML</h2>
 * Use any registered function name directly in event attributes:
 * <pre>{@code
 * <button onclick="myMod.openInventory()">Open Inventory</button>
 * <button onclick="myMod.giveItem('diamond', 64)">Give Diamonds</button>
 * <input type="range" onchange="myMod.setVolume(this.value)">
 * }</pre>
 *
 * Built-in functions available in HTML without registration:
 * <ul>
 *   <li>{@code alert(msg)}             – shows message in chat</li>
 *   <li>{@code console.log(msg)}       – logs to mod logger</li>
 *   <li>{@code navigate(url)}          – navigate to another page</li>
 *   <li>{@code reload()}               – reload the current page</li>
 *   <li>{@code setVar(name, val)}       – set a page-scope variable</li>
 *   <li>{@code getVar(name)}            – get a page-scope variable</li>
 *   <li>{@code getElementById(id)}      – returns a DomNode</li>
 *   <li>{@code setAttribute(id, a, v)} – set an attribute on an element</li>
 *   <li>{@code setStyle(id, prop, val)}- set a CSS property on an element</li>
 *   <li>{@code setInnerText(id, text)} – replace text content of element</li>
 *   <li>{@code toggleClass(id, cls)}   – toggle a CSS class on an element</li>
 * </ul>
 */
public final class WebLibApi {

    private WebLibApi() {}

    /* ─────────────────── Opening screens ─────────────────── */

    /**
     * Open an HTML page in the WebScreen GUI.
     * Must be called on the client thread.
     *
     * @param html Raw HTML string to render.
     */
    public static void openPage(String html) {
        openPage(html, (WebScreen.PageLoader) null);
    }

    /**
     * Open an HTML page with navigation support.
     *
     * @param html       Initial HTML content.
     * @param pageLoader Called when links are clicked or navigate() is called.
     *                   Return the HTML for the new page, or null to cancel.
     */
    public static void openPage(String html, WebScreen.PageLoader pageLoader) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.tell(() -> mc.setScreen(new WebScreen("web_lib", html, pageLoader)));
    }

    /**
     * Open a pre-built {@link WebPage} object.
     */
    public static void openPage(WebPage page) {
        openPage(page, null);
    }

    public static void openPage(WebPage page, WebScreen.PageLoader pageLoader) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.tell(() -> mc.setScreen(new WebScreen(page, pageLoader)));
    }

    /**
     * Open a page without the browser chrome (address bar, back/forward buttons).
     * Useful for in-game GUIs that don't need navigation.
     */
    public static void openPageHeadless(String html) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.tell(() -> mc.setScreen(new WebScreen("web_lib", html).withoutChrome()));
    }

    /* ─────────────────── Function registration ─────────────────── */

    /**
     * Register a Java-backed function accessible from HTML event attributes.
     *
     * The function name may contain dots (e.g. "myMod.openInventory") to
     * create a namespaced API, reducing the risk of collisions with other mods.
     *
     * Functions are registered globally and persist for the lifetime of the game session.
     *
     * @param name     The function name as used in HTML, e.g. "myMod.doThing"
     * @param function The Java handler.
     *                 {@code args} is the parsed argument list (Strings, Integers, Doubles, Booleans).
     *                 {@code ctx}  provides access to the DOM and current event.
     *                 Return value is available to the script engine but is typically ignored.
     */
    public static void registerFunction(String name, ScriptBridge.ScriptFunction function) {
        ScriptBridge.register(name, function);
    }

    /* ─────────────────── Page building utilities ─────────────────── */

    /**
     * Build a {@link WebPage} without opening it.
     * Useful for pre-warming the layout or injecting extra CSS before display.
     *
     * @param html Raw HTML.
     * @return A fully parsed WebPage (not yet laid out – call {@link WebPage#layout} before rendering).
     */
    public static WebPage buildPage(String html) {
        return new WebPage(html);
    }

    /**
     * Convenience method to build a complete HTML document from fragments.
     *
     * @param title   Page title (for the chrome title bar)
     * @param css     CSS string (may be null)
     * @param body    HTML body content
     * @return Full HTML document string
     */
    public static String buildDocument(String title, String css, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>");
        sb.append("<meta charset=\"utf-8\">");
        if (title != null && !title.isEmpty()) sb.append("<title>").append(title).append("</title>");
        if (css != null && !css.isBlank())     sb.append("<style>").append(css).append("</style>");
        sb.append("</head><body>");
        if (body != null) sb.append(body);
        sb.append("</body></html>");
        return sb.toString();
    }
}
