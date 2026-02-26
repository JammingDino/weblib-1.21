package com.jammingdino.web_lib.screen;

import com.jammingdino.web_lib.css.CssEngine;
import com.jammingdino.web_lib.css.CssParser;
import com.jammingdino.web_lib.css.CssRule;
import com.jammingdino.web_lib.html.DomNode;
import com.jammingdino.web_lib.html.HtmlParser;
import com.jammingdino.web_lib.layout.LayoutBox;
import com.jammingdino.web_lib.layout.LayoutEngine;
import com.jammingdino.web_lib.script.ScriptBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A parsed and laid-out web page.
 *
 * Lifecycle:
 *   1. Construct with raw HTML (and optional extra CSS).
 *   2. {@link #layout(int, int)} to compute boxes for a given viewport.
 *   3. Pass to {@link com.jammingdino.web_lib.render.HtmlRenderer#render} each frame.
 *   4. Forward mouse/key events via the dispatch methods.
 */
public class WebPage {

    /* ── source ── */
    private final String rawHtml;
    private final List<String> extraCss = new ArrayList<>();

    /* ── parsed state ── */
    private DomNode document;
    private List<CssRule> cssRules;
    private ScriptBridge scriptBridge;

    /* ── layout ── */
    private LayoutBox rootBox;
    private int layoutWidth;
    private int layoutHeight;

    /* ── scroll ── */
    private int scrollY = 0;
    private int scrollX = 0;

    /* ── event handler ── */
    private BiConsumer<String, String> systemEventHandler;

    /* ── page meta ── */
    private String title = "";
    private String url   = "";

    /* ─────────────── constructors ─────────────── */

    public WebPage(String html) {
        this.rawHtml = html == null ? "" : html;
        parse();
    }

    /** Convenience: load from a ResourceLocation-style path resolved by the caller. */
    public static WebPage fromHtml(String html) {
        return new WebPage(html);
    }

    /* ─────────────── parsing ─────────────── */

    private void parse() {
        // 1. DOM
        document = HtmlParser.parseHtml(rawHtml);

        // 2. Collect <style> blocks + <link rel=stylesheet> (resource-based, resolved by caller)
        List<CssRule> rules = new ArrayList<>();
        for (DomNode styleNode : document.getElementsByTagName("style")) {
            StringBuilder sb = new StringBuilder();
            for (DomNode child : styleNode.getChildren()) {
                if (child.isText() && child.getTextContent() != null) sb.append(child.getTextContent());
            }
            rules.addAll(CssParser.parseString(sb.toString()));
        }
        // Extra CSS injected by the mod (e.g. theme CSS)
        for (String css : extraCss) rules.addAll(CssParser.parseString(css));
        cssRules = rules;

        // 3. Apply styles
        CssEngine engine = new CssEngine(cssRules);
        engine.applyStyles(document);

        // 4. Script bridge
        scriptBridge = new ScriptBridge();
        scriptBridge.setDocument(document);
        if (systemEventHandler != null) scriptBridge.setSystemEventHandler(systemEventHandler);
        scriptBridge.scanDocument(document);
        scriptBridge.processScriptTags(document);

        // 5. Extract <title>
        List<DomNode> titles = document.getElementsByTagName("title");
        if (!titles.isEmpty()) {
            DomNode t = titles.get(0);
            for (DomNode c : t.getChildren()) if (c.isText() && c.getTextContent() != null) title += c.getTextContent();
            title = title.trim();
        }
    }

    /* ─────────────── layout ─────────────── */

    /**
     * Compute the layout for the given viewport size.
     * Call this whenever the screen is resized or the DOM changes.
     */
    public void layout(int viewportWidth, int viewportHeight) {
        this.layoutWidth  = viewportWidth;
        this.layoutHeight = viewportHeight;

        LayoutEngine engine = new LayoutEngine(viewportWidth, viewportHeight);
        rootBox = engine.layout(document, 0, 0, viewportWidth);
    }

    /* ─────────────── interaction ─────────────── */

    /**
     * Handle a mouse click at the given screen coordinates.
     * Returns true if any element was hit.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (rootBox == null) return false;

        int relX = mouseX;
        int relY = mouseY + scrollY;

        LayoutBox hit = rootBox.hitTest(relX, relY);
        if (hit == null) return false;

        DomNode node = hit.getNode();

        // Update active/focus state on the hit node
        clearFocusAll(document);
        node.setData("focus", true);
        node.setData("active", true);

        // Walk UP the DOM from the hit node looking for interactive elements.
        // This is essential: a click on a text node inside a <button> must
        // still trigger the button's onclick.
        DomNode current = node;
        while (current != null) {
            // Fire any onclick listener registered on this ancestor
            scriptBridge.dispatchEvent(current, "click", "");

            // <a href> navigation
            if ("a".equals(current.getTagName())) {
                String href = current.getAttribute("href");
                if (href != null && !href.isBlank()) {
                    scriptBridge.fireEvent("navigate", href);
                    return true;
                }
            }

            // checkbox toggle
            if ("input".equals(current.getTagName()) && "checkbox".equals(current.getAttribute("type"))) {
                boolean checked = Boolean.TRUE.equals(current.getData("checked"));
                current.setData("checked", !checked);
                scriptBridge.dispatchEvent(current, "change", String.valueOf(!checked));
                return true;
            }

            // Stop bubbling at block-level containers
            if (current != node) {
                String tag = current.getTagName();
                if (tag != null && (tag.equals("div") || tag.equals("section") ||
                        tag.equals("body") || tag.equals("html"))) {
                    break;
                }
            }
            current = current.getParent();
        }

        return true;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        clearActiveAll(document);
        return false;
    }

    public boolean mouseMoved(int mouseX, int mouseY) {
        if (rootBox == null) return false;
        int relX = mouseX;
        int relY = mouseY + scrollY;
        LayoutBox hit = rootBox.hitTest(relX, relY);
        // Update hover state across all nodes
        updateHoverAll(document, hit != null ? hit.getNode() : null);
        return false;
    }

    /**
     * Handle mouse scroll wheel.
     * @param delta positive = scroll down
     */
    public boolean mouseScrolled(int mouseX, int mouseY, double delta) {
        scrollY = Math.max(0, scrollY - (int)(delta * 10));
        // Clamp to content height
        if (rootBox != null) {
            int maxScroll = Math.max(0, rootBox.height - layoutHeight);
            scrollY = Math.min(scrollY, maxScroll);
            // Keep rootBox.scrollTop in sync so the renderer and scrollbar can read it
            rootBox.scrollTop = scrollY;
        }
        return true;
    }

    /**
     * Handle a key press (character typed).
     * Forwards to the focused input element.
     */
    public boolean charTyped(char codePoint) {
        DomNode focused = findFocused(document);
        if (focused == null || !"input".equals(focused.getTagName())) return false;
        String val = focused.getAttribute("value");
        if (val == null) val = "";
        String newVal = val + codePoint;
        focused.setAttribute("value", newVal);
        scriptBridge.dispatchEvent(focused, "input",  newVal);
        scriptBridge.dispatchEvent(focused, "change", newVal);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        DomNode focused = findFocused(document);
        if (focused == null || !"input".equals(focused.getTagName())) return false;
        // BACKSPACE = 259
        if (keyCode == 259) {
            String val = focused.getAttribute("value");
            if (val != null && !val.isEmpty()) {
                String newVal = val.substring(0, val.length() - 1);
                focused.setAttribute("value", newVal);
                scriptBridge.dispatchEvent(focused, "change", newVal);
            }
            return true;
        }
        return false;
    }

    /* ─────────────── extra CSS injection ─────────────── */

    /**
     * Inject additional CSS (e.g. a theme) before or after calling {@link #layout}.
     * Triggers a re-parse of styles.
     */
    public void injectCss(String css) {
        if (css == null || css.isBlank()) return;
        extraCss.add(css);
        // Re-apply styles
        cssRules.addAll(CssParser.parseString(css));
        new CssEngine(cssRules).applyStyles(document);
    }

    /* ─────────────── DOM mutation ─────────────── */

    /**
     * Re-layout after a DOM mutation (e.g. text changed, element added/removed).
     */
    public void invalidateLayout() {
        if (layoutWidth > 0) layout(layoutWidth, layoutHeight);
    }

    /**
     * Re-parse the entire page from scratch.
     * Use sparingly – expensive.
     */
    public void reload() {
        extraCss.clear();
        parse();
        if (layoutWidth > 0) layout(layoutWidth, layoutHeight);
    }

    /* ─────────────── helpers ─────────────── */

    private void clearFocusAll(DomNode node) {
        node.setData("focus", false);
        for (DomNode c : node.getChildren()) clearFocusAll(c);
    }

    private void clearActiveAll(DomNode node) {
        node.setData("active", false);
        for (DomNode c : node.getChildren()) clearActiveAll(c);
    }

    private void updateHoverAll(DomNode node, DomNode hovered) {
        node.setData("hover", node == hovered);
        for (DomNode c : node.getChildren()) updateHoverAll(c, hovered);
    }

    private DomNode findFocused(DomNode node) {
        if (Boolean.TRUE.equals(node.getData("focus"))) return node;
        for (DomNode c : node.getChildren()) {
            DomNode f = findFocused(c);
            if (f != null) return f;
        }
        return null;
    }

    private DomNode findAncestor(DomNode node, String tag) {
        DomNode cur = node;
        while (cur != null) {
            if (tag.equals(cur.getTagName())) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    /* ─────────────── accessors ─────────────── */

    public DomNode    getDocument()   { return document; }
    public LayoutBox  getRootBox()    { return rootBox; }
    public ScriptBridge getScriptBridge() { return scriptBridge; }
    public String     getTitle()      { return title; }
    public String     getUrl()        { return url; }
    public void       setUrl(String u){ this.url = u; }
    public int        getScrollY()    { return scrollY; }
    public int        getScrollX()    { return scrollX; }
    public int        getLayoutWidth(){ return layoutWidth; }
    public int        getLayoutHeight(){ return layoutHeight; }

    public void setSystemEventHandler(BiConsumer<String, String> handler) {
        this.systemEventHandler = handler;
        if (scriptBridge != null) scriptBridge.setSystemEventHandler(handler);
    }
}