package com.jammingdino.web_lib.css;

import com.jammingdino.web_lib.html.DomNode;

import java.util.*;

/**
 * Applies CSS rules to a DOM tree, computing the final style for each element.
 *
 * Cascade order (ascending priority):
 *   1. Browser defaults (built-in)
 *   2. Author stylesheet rules (by specificity, then source order)
 *   3. Inline styles (highest)
 *
 * Inheritance:
 *   The following properties are inherited from parent to child if not
 *   explicitly set: color, font-size, font-family, font-weight, font-style,
 *   line-height, text-align, visibility, cursor.
 */
public class CssEngine {

    /* Properties that inherit from parent when not explicitly set */
    private static final Set<String> INHERITED = Set.of(
            "color", "font-size", "font-family", "font-weight", "font-style",
            "font-variant", "line-height", "letter-spacing", "word-spacing",
            "text-align", "text-transform", "text-decoration",
            "white-space", "visibility", "cursor", "list-style-type",
            "list-style-position"
    );

    /* Default browser-like styles */
    private static final Map<String, Map<String, String>> BROWSER_DEFAULTS;

    static {
        BROWSER_DEFAULTS = new HashMap<>();
        def("body",    "display:block;margin:8px;font-size:16px;color:#000000;font-family:minecraft;line-height:1.4");
        def("h1",      "display:block;font-size:32px;font-weight:bold;margin-top:10px;margin-bottom:5px");
        def("h2",      "display:block;font-size:24px;font-weight:bold;margin-top:10px;margin-bottom:5px");
        def("h3",      "display:block;font-size:20px;font-weight:bold;margin-top:8px;margin-bottom:4px");
        def("h4",      "display:block;font-size:16px;font-weight:bold;margin-top:8px;margin-bottom:4px");
        def("h5",      "display:block;font-size:14px;font-weight:bold;margin-top:6px;margin-bottom:3px");
        def("h6",      "display:block;font-size:12px;font-weight:bold;margin-top:6px;margin-bottom:3px");
        def("p",       "display:block;margin-top:8px;margin-bottom:8px");
        def("div",     "display:block");
        def("section", "display:block");
        def("article", "display:block");
        def("header",  "display:block");
        def("footer",  "display:block");
        def("main",    "display:block");
        def("nav",     "display:block");
        def("aside",   "display:block");
        def("ul",      "display:block;list-style-type:disc;margin-top:8px;margin-bottom:8px;padding-left:24px");
        def("ol",      "display:block;list-style-type:decimal;margin-top:8px;margin-bottom:8px;padding-left:24px");
        def("li",      "display:list-item");
        def("span",    "display:inline");
        def("a",       "display:inline;color:#0000EE;text-decoration:underline;cursor:pointer");
        def("strong",  "display:inline;font-weight:bold");
        def("b",       "display:inline;font-weight:bold");
        def("em",      "display:inline;font-style:italic");
        def("i",       "display:inline;font-style:italic");
        def("s",       "display:inline;text-decoration:line-through");
        def("u",       "display:inline;text-decoration:underline");
        def("code",    "display:inline;font-family:monospace");
        def("pre",     "display:block;white-space:pre;font-family:monospace;margin-top:8px;margin-bottom:8px");
        def("hr",      "display:block;border-top:1px solid #888;margin-top:8px;margin-bottom:8px");
        def("br",      "display:inline");
        def("img",     "display:inline-block");
        def("button",  "display:inline-block;cursor:pointer;padding-top:4px;padding-bottom:4px;padding-left:8px;padding-right:8px;background-color:#dddddd;border:1px solid #888");
        def("input",   "display:inline-block");
        def("select",  "display:inline-block;cursor:pointer;padding-top:2px;padding-bottom:2px;padding-left:4px;padding-right:4px;background-color:#ffffff;border:1px solid #888");
        def("label",   "display:inline;cursor:default");
        def("table",   "display:table;border-collapse:collapse");
        def("tr",      "display:table-row");
        def("td","th", "display:table-cell;padding:2px 4px");
        def("th",      "font-weight:bold;text-align:center");
        def("script",  "display:none");
        def("style",   "display:none");
        def("head",    "display:none");
    }

    private static void def(String tag, String css) {
        Map<String, String> decls = new LinkedHashMap<>();
        for (String decl : css.split(";")) {
            int colon = decl.indexOf(':');
            if (colon > 0) decls.put(decl.substring(0, colon).trim(), decl.substring(colon + 1).trim());
        }
        BROWSER_DEFAULTS.put(tag, decls);
    }

    private static void def(String tag1, String tag2, String css) {
        def(tag1, css); def(tag2, css);
    }

    /* ─────────────────── public API ─────────────────── */

    private final List<CssRule> rules;

    public CssEngine(List<CssRule> authorRules) {
        this.rules = authorRules == null ? Collections.emptyList() : authorRules;
    }

    /**
     * Walk the entire DOM subtree and fill in computedStyle on every element node.
     * Must be called after the DOM is fully built.
     *
     * @param root    Typically the document or &lt;html&gt; node.
     */
    public void applyStyles(DomNode root) {
        applyNode(root, null);
    }

    /* ─────────────────── recursive application ─────────────────── */

    private void applyNode(DomNode node, DomNode parent) {
        if (node.isElement()) {
            computeStyle(node, parent);
        }
        for (DomNode child : node.getChildren()) {
            applyNode(child, node.isElement() ? node : parent);
        }
    }

    private void computeStyle(DomNode node, DomNode parent) {
        // 1. Browser defaults
        Map<String, String> defaults = BROWSER_DEFAULTS.getOrDefault(node.getTagName(), Map.of());
        defaults.forEach(node::setComputedStyle);

        // 2. Inherited properties from parent
        if (parent != null) {
            for (String prop : INHERITED) {
                if (!node.getComputedStyleMap().containsKey(prop)) {
                    String parentVal = parent.getComputedStyle(prop);
                    if (parentVal != null) node.setComputedStyle(prop, parentVal);
                }
            }
        }

        // 3. Author rules – sorted by specificity (stable sort keeps source order for equal specificity)
        List<CssRule> matching = new ArrayList<>();
        for (CssRule rule : rules) {
            for (String sel : rule.getSelectors()) {
                if (CssMatcher.matches(node, sel)) {
                    matching.add(rule);
                    break;
                }
            }
        }
        matching.sort(Comparator.comparingInt(CssRule::getSpecificity));
        for (CssRule rule : matching) {
            rule.getDeclarations().forEach(node::setComputedStyle);
        }

        // 4. Inline styles (highest priority) – use parseDeclarations so that shorthands like
        //    "padding: 6px 16px" and "border: 1px solid red" are expanded to their longhand
        //    equivalents, correctly overriding any browser-default longhands already set above.
        String inlineStyle = node.getAttribute("style");
        if (inlineStyle != null && !inlineStyle.isBlank()) {
            new CssParser("").parseDeclarations(inlineStyle).forEach(node::setComputedStyle);
        }
    }

    /* ─────────────────── value helpers (static utilities) ─────────────────── */

    /**
     * Resolve a CSS length value (px, em, %, rem, pt, vw, vh) to pixels.
     *
     * @param value       The CSS value string, e.g. "12px" or "1.5em"
     * @param parentPx    Parent element's font-size or containing-block width in px
     * @param rootFontPx  Root (html) font size for rem resolution
     * @param vpDim       Viewport width or height for vw/vh
     */
    public static int resolveLength(String value, int parentPx, int rootFontPx, int vpDim) {
        if (value == null || value.isBlank()) return 0;
        value = value.trim();
        try {
            if (value.endsWith("px"))   return Math.round(Float.parseFloat(value.substring(0, value.length() - 2)));
            if (value.endsWith("em"))   return Math.round(Float.parseFloat(value.substring(0, value.length() - 2)) * parentPx);
            if (value.endsWith("rem"))  return Math.round(Float.parseFloat(value.substring(0, value.length() - 3)) * rootFontPx);
            if (value.endsWith("%"))    return Math.round(Float.parseFloat(value.substring(0, value.length() - 1)) / 100f * parentPx);
            if (value.endsWith("pt"))   return Math.round(Float.parseFloat(value.substring(0, value.length() - 2)) * 1.333f);
            if (value.endsWith("vw"))   return Math.round(Float.parseFloat(value.substring(0, value.length() - 2)) / 100f * vpDim);
            if (value.endsWith("vh"))   return Math.round(Float.parseFloat(value.substring(0, value.length() - 2)) / 100f * vpDim);
            if (value.equals("0"))      return 0;
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    /**
     * Parse a CSS color string to an ARGB integer (0xAARRGGBB).
     * Supports: #RGB, #RRGGBB, #RRGGBBAA, rgb(), rgba(), named colors.
     */
    public static int resolveColor(String value) {
        if (value == null || value.isBlank()) return 0xFF000000;
        value = value.trim();

        if (value.startsWith("#")) {
            String hex = value.substring(1);
            return switch (hex.length()) {
                case 3 -> {
                    int r = Integer.parseInt(hex.substring(0,1), 16) * 17;
                    int g = Integer.parseInt(hex.substring(1,2), 16) * 17;
                    int b = Integer.parseInt(hex.substring(2,3), 16) * 17;
                    yield 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                case 6 -> 0xFF000000 | Integer.parseInt(hex, 16);
                case 8 -> {
                    int rgb = Integer.parseInt(hex.substring(0,6), 16);
                    int a   = Integer.parseInt(hex.substring(6,8), 16);
                    yield (a << 24) | rgb;
                }
                default -> 0xFF000000;
            };
        }

        if (value.startsWith("rgb")) {
            String inner = value.replaceAll("rgba?\\(|\\)", "").trim();
            String[] parts = inner.split(",");
            int r = parts.length > 0 ? clamp(parseIntOrFloat(parts[0].trim())) : 0;
            int g = parts.length > 1 ? clamp(parseIntOrFloat(parts[1].trim())) : 0;
            int b = parts.length > 2 ? clamp(parseIntOrFloat(parts[2].trim())) : 0;
            int a = parts.length > 3 ? Math.round(Float.parseFloat(parts[3].trim()) * 255) : 255;
            return (clamp(a) << 24) | (r << 16) | (g << 8) | b;
        }

        // Named colors
        Integer named = NAMED_COLORS.get(value.toLowerCase());
        return named != null ? named : 0xFF000000;
    }

    private static int parseIntOrFloat(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) {}
        try { return Math.round(Float.parseFloat(s)); } catch (NumberFormatException e) {}
        return 0;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /* ─────────────────── named color table ─────────────────── */

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    static {
        NAMED_COLORS.put("black",       0xFF000000);
        NAMED_COLORS.put("white",       0xFFFFFFFF);
        NAMED_COLORS.put("red",         0xFFFF0000);
        NAMED_COLORS.put("green",       0xFF008000);
        NAMED_COLORS.put("blue",        0xFF0000FF);
        NAMED_COLORS.put("yellow",      0xFFFFFF00);
        NAMED_COLORS.put("orange",      0xFFFFA500);
        NAMED_COLORS.put("purple",      0xFF800080);
        NAMED_COLORS.put("pink",        0xFFFFC0CB);
        NAMED_COLORS.put("gray",        0xFF808080);
        NAMED_COLORS.put("grey",        0xFF808080);
        NAMED_COLORS.put("silver",      0xFFC0C0C0);
        NAMED_COLORS.put("navy",        0xFF000080);
        NAMED_COLORS.put("teal",        0xFF008080);
        NAMED_COLORS.put("cyan",        0xFF00FFFF);
        NAMED_COLORS.put("aqua",        0xFF00FFFF);
        NAMED_COLORS.put("magenta",     0xFFFF00FF);
        NAMED_COLORS.put("fuchsia",     0xFFFF00FF);
        NAMED_COLORS.put("maroon",      0xFF800000);
        NAMED_COLORS.put("olive",       0xFF808000);
        NAMED_COLORS.put("lime",        0xFF00FF00);
        NAMED_COLORS.put("indigo",      0xFF4B0082);
        NAMED_COLORS.put("violet",      0xFFEE82EE);
        NAMED_COLORS.put("brown",       0xFFA52A2A);
        NAMED_COLORS.put("gold",        0xFFFFD700);
        NAMED_COLORS.put("coral",       0xFFFF7F50);
        NAMED_COLORS.put("salmon",      0xFFFA8072);
        NAMED_COLORS.put("khaki",       0xFFF0E68C);
        NAMED_COLORS.put("transparent", 0x00000000);
        NAMED_COLORS.put("inherit",     -1); // sentinel
    }
}
