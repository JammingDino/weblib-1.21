package com.jammingdino.web_lib.css;

import java.util.*;

/**
 * Parses a CSS string into a list of {@link CssRule}s.
 *
 * Supports:
 *   - Type, class, id, attribute, pseudo-class selectors
 *   - Combinator grouping (,)
 *   - @media blocks (parsed as a nested stylesheet; media queries are ignored)
 *   - @keyframes (discarded – animation playback not implemented)
 *   - /* ... * / comments (stripped before parsing)
 *   - CSS custom properties (--my-var: value)
 *   - !important flag (tracked but currently treated as higher priority)
 *   - Shorthand expansion for: margin, padding, border, background, font
 */
public class CssParser {

    private final String src;

    public CssParser(String css) {
        this.src = stripComments(css == null ? "" : css);
    }

    /* ─────────────────── public API ─────────────────── */

    public List<CssRule> parse() {
        List<CssRule> rules = new ArrayList<>();
        parseBlock(src, rules);
        return rules;
    }

    /* ─────────────────── block parser ─────────────────── */

    private void parseBlock(String text, List<CssRule> out) {
        int i = 0;
        while (i < text.length()) {
            // Skip whitespace
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= text.length()) break;

            // At-rule?
            if (text.charAt(i) == '@') {
                int[] range = consumeAtRule(text, i);
                String atBlock = text.substring(range[0], range[1]);
                handleAtRule(atBlock, out);
                i = range[1];
                continue;
            }

            // Selector + { declarations }
            int openBrace = text.indexOf('{', i);
            if (openBrace < 0) break;

            String selectorPart = text.substring(i, openBrace).trim();
            int closeBrace = findMatchingBrace(text, openBrace);
            String declarationBlock = text.substring(openBrace + 1, closeBrace).trim();

            List<String> selectors = splitSelectors(selectorPart);
            if (!selectors.isEmpty()) {
                Map<String, String> decls = parseDeclarations(declarationBlock);
                if (!decls.isEmpty()) {
                    out.add(new CssRule(selectors, decls));
                }
            }
            i = closeBrace + 1;
        }
    }

    /* ─────────────────── at-rule handling ─────────────────── */

    private void handleAtRule(String atBlock, List<CssRule> out) {
        String lower = atBlock.toLowerCase();
        if (lower.startsWith("@media")) {
            // Recurse into the inner block
            int open = atBlock.indexOf('{');
            int close = findMatchingBrace(atBlock, open);
            if (open >= 0 && close > open) {
                parseBlock(atBlock.substring(open + 1, close), out);
            }
        }
        // @keyframes, @font-face, @charset, @import → discard for now
    }

    private int[] consumeAtRule(String text, int start) {
        int i = start;
        // Find opening brace or semicolon
        while (i < text.length() && text.charAt(i) != '{' && text.charAt(i) != ';') i++;
        if (i >= text.length()) return new int[]{start, text.length()};
        if (text.charAt(i) == ';') return new int[]{start, i + 1};
        int close = findMatchingBrace(text, i);
        return new int[]{start, close + 1};
    }

    /* ─────────────────── declaration parsing ─────────────────── */

    public Map<String, String> parseDeclarations(String block) {
        Map<String, String> decls = new LinkedHashMap<>();
        for (String decl : block.split(";")) {
            int colon = decl.indexOf(':');
            if (colon < 0) continue;
            String prop  = decl.substring(0, colon).trim().toLowerCase();
            String value = decl.substring(colon + 1).trim();
            if (value.endsWith("!important")) {
                value = value.substring(0, value.length() - "!important".length()).trim();
                prop = prop + "!"; // mark importance internally
            }
            if (prop.isEmpty() || value.isEmpty()) continue;
            expand(prop, value, decls);
        }
        return decls;
    }

    /* ─────────────────── shorthand expansion ─────────────────── */

    private void expand(String prop, String value, Map<String, String> out) {
        switch (prop) {
            case "margin"  -> expandBox("margin",  value, out);
            case "padding" -> expandBox("padding", value, out);
            case "border"  -> {
                // Simple: border: 1px solid red
                String[] parts = value.split("\\s+", 3);
                if (parts.length >= 1) out.put("border-width", parts[0]);
                if (parts.length >= 2) out.put("border-style", parts[1]);
                if (parts.length >= 3) out.put("border-color", parts[2]);
                out.put("border", value); // also keep the shorthand
            }
            case "background" -> {
                out.put("background", value);
                // Try to detect a plain color (no url(), no gradient)
                if (!value.contains("url(") && !value.contains("gradient(")) {
                    out.put("background-color", value);
                }
            }
            case "font" -> {
                out.put("font", value);
                // Minimal font shorthand: [style] [weight] size[/line-height] family
                // Just store the whole value; font sub-properties extracted by renderer as needed
            }
            default -> out.put(prop, value);
        }
    }

    private void expandBox(String prefix, String value, Map<String, String> out) {
        out.put(prefix, value); // keep shorthand
        String[] parts = value.trim().split("\\s+");
        String top, right, bottom, left;
        switch (parts.length) {
            case 1 -> { top = right = bottom = left = parts[0]; }
            case 2 -> { top = bottom = parts[0]; right = left = parts[1]; }
            case 3 -> { top = parts[0]; right = left = parts[1]; bottom = parts[2]; }
            default -> { top = parts[0]; right = parts[1]; bottom = parts[2]; left = parts[3]; }
        }
        out.put(prefix + "-top",    top);
        out.put(prefix + "-right",  right);
        out.put(prefix + "-bottom", bottom);
        out.put(prefix + "-left",   left);
    }

    /* ─────────────────── selector splitting ─────────────────── */

    /** Split "a, b, c" on commas, but not commas inside attribute selectors. */
    private List<String> splitSelectors(String raw) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth--;
            else if (c == ',' && depth == 0) {
                String s = raw.substring(start, i).trim();
                if (!s.isEmpty()) result.add(s);
                start = i + 1;
            }
        }
        String last = raw.substring(start).trim();
        if (!last.isEmpty()) result.add(last);
        return result;
    }

    /* ─────────────────── utilities ─────────────────── */

    private int findMatchingBrace(String text, int openPos) {
        int depth = 0;
        for (int i = openPos; i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            else if (text.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return text.length() - 1;
    }

    private static String stripComments(String css) {
        return css.replaceAll("/\\*.*?\\*/", " ");
    }

    /* ─────────────────── convenience ─────────────────── */

    public static List<CssRule> parseString(String css) {
        return new CssParser(css).parse();
    }
}
