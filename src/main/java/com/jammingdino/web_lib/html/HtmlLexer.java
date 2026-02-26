package com.jammingdino.web_lib.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-written HTML lexer (tokenizer).
 *
 * Supported:
 *  - Open/close/self-closing tags with quoted or unquoted attribute values
 *  - Text nodes (whitespace-collapsed)
 *  - Comments <!-- ... -->
 *  - DOCTYPE declaration (discarded as a single token)
 *  - Character references for &amp; &lt; &gt; &quot; &apos; &#decimal; &#xhex;
 *
 * Does NOT attempt spec-compliant error recovery – it is tolerant but simple.
 */
public class HtmlLexer {

    private final String src;
    private int pos;

    public HtmlLexer(String html) {
        this.src = html == null ? "" : html;
        this.pos = 0;
    }

    /* ───────────────────────── public API ───────────────────────── */

    public List<HtmlToken> tokenize() {
        List<HtmlToken> tokens = new ArrayList<>();
        while (pos < src.length()) {
            if (peek() == '<') {
                HtmlToken t = readTag();
                if (t != null) tokens.add(t);
            } else {
                String text = readText();
                if (!text.isEmpty()) tokens.add(HtmlToken.text(text));
            }
        }
        tokens.add(HtmlToken.eof());
        return tokens;
    }

    /* ───────────────────────── tag reading ─────────────────────── */

    private HtmlToken readTag() {
        consume('<');

        // Comment
        if (startsWith("!--")) {
            pos += 3;
            int end = src.indexOf("-->", pos);
            String body = end == -1 ? src.substring(pos) : src.substring(pos, end);
            pos = end == -1 ? src.length() : end + 3;
            return HtmlToken.comment(body);
        }

        // DOCTYPE
        if (startsWithIgnoreCase("!doctype")) {
            int end = src.indexOf('>', pos);
            pos = end == -1 ? src.length() : end + 1;
            return HtmlToken.doctype();
        }

        // Close tag
        if (peek() == '/') {
            pos++;
            String name = readName();
            skipTo('>');
            return HtmlToken.closeTag(name);
        }

        // Open / self-closing tag
        String name = readName();
        if (name.isEmpty()) {
            // Stray '<', treat as text
            return HtmlToken.text("<");
        }

        Map<String, String> attrs = readAttributes();
        skipWs();

        boolean selfClose = false;
        if (peek() == '/') { selfClose = true; pos++; }
        if (peek() == '>') pos++;

        // Void elements are always self-closing regardless of the slash
        if (selfClose || isVoidElement(name)) {
            return HtmlToken.selfClosing(name, attrs);
        }
        return HtmlToken.openTag(name, attrs);
    }

    /* ───────────────────── attribute reading ───────────────────── */

    private Map<String, String> readAttributes() {
        Map<String, String> attrs = new LinkedHashMap<>();
        while (pos < src.length()) {
            skipWs();
            char c = peek();
            if (c == '>' || c == '/') break;
            if (c == '\0') break;

            String key = readName();
            if (key.isEmpty()) { pos++; continue; } // skip unexpected chars

            skipWs();
            if (peek() == '=') {
                pos++; // consume '='
                skipWs();
                String value = readAttributeValue();
                attrs.put(key.toLowerCase(), decodeEntities(value));
            } else {
                // Boolean attribute
                attrs.put(key.toLowerCase(), key.toLowerCase());
            }
        }
        return attrs;
    }

    private String readAttributeValue() {
        char q = peek();
        if (q == '"' || q == '\'') {
            pos++;
            int start = pos;
            while (pos < src.length() && src.charAt(pos) != q) pos++;
            String val = src.substring(start, pos);
            if (pos < src.length()) pos++; // closing quote
            return val;
        }
        // Unquoted
        int start = pos;
        while (pos < src.length() && !Character.isWhitespace(src.charAt(pos))
                && src.charAt(pos) != '>' && src.charAt(pos) != '/') pos++;
        return src.substring(start, pos);
    }

    /* ─────────────────────── text reading ─────────────────────── */

    private String readText() {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && peek() != '<') {
            sb.append(src.charAt(pos++));
        }
        return decodeEntities(sb.toString());
    }

    /* ─────────────────────── entity decoding ───────────────────── */

    private String decodeEntities(String s) {
        if (!s.contains("&")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '&') {
                int semi = s.indexOf(';', i);
                if (semi != -1 && semi - i <= 8) {
                    String ref = s.substring(i + 1, semi);
                    String decoded = decodeRef(ref);
                    if (decoded != null) {
                        sb.append(decoded);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(s.charAt(i++));
        }
        return sb.toString();
    }

    private String decodeRef(String ref) {
        return switch (ref) {
            case "amp"  -> "&";
            case "lt"   -> "<";
            case "gt"   -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> "\u00A0";
            case "copy" -> "\u00A9";
            case "reg"  -> "\u00AE";
            case "trade"-> "\u2122";
            case "mdash"-> "\u2014";
            case "ndash"-> "\u2013";
            case "hellip"-> "\u2026";
            default -> {
                if (ref.startsWith("#x") || ref.startsWith("#X")) {
                    try { yield String.valueOf((char) Integer.parseInt(ref.substring(2), 16)); }
                    catch (NumberFormatException e) { yield null; }
                } else if (ref.startsWith("#")) {
                    try { yield String.valueOf((char) Integer.parseInt(ref.substring(1))); }
                    catch (NumberFormatException e) { yield null; }
                }
                yield null;
            }
        };
    }

    /* ───────────────────────── helpers ─────────────────────────── */

    private String readName() {
        int start = pos;
        while (pos < src.length() && isNameChar(src.charAt(pos))) pos++;
        return src.substring(start, pos);
    }

    private boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':';
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private void skipTo(char target) {
        while (pos < src.length() && src.charAt(pos) != target) pos++;
        if (pos < src.length()) pos++;
    }

    private void consume(char c) {
        if (pos < src.length() && src.charAt(pos) == c) pos++;
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private boolean startsWith(String s) {
        return src.startsWith(s, pos);
    }

    private boolean startsWithIgnoreCase(String s) {
        if (pos + s.length() > src.length()) return false;
        return src.substring(pos, pos + s.length()).equalsIgnoreCase(s);
    }

    private static boolean isVoidElement(String name) {
        return switch (name.toLowerCase()) {
            case "area","base","br","col","embed","hr","img","input",
                 "link","meta","param","source","track","wbr" -> true;
            default -> false;
        };
    }
}
