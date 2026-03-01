package com.jammingdino.web_lib.html;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single HTML token produced by the Lexer.
 */
public class HtmlToken {

    private final TokenType type;
    private final String tagName;   // null for TEXT / COMMENT
    private final String text;      // raw text content or comment body
    private final Map<String, String> attributes;

    private HtmlToken(TokenType type, String tagName, String text, Map<String, String> attributes) {
        this.type = type;
        this.tagName = tagName == null ? null : tagName.toLowerCase();
        this.text = text;
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    /* ---- factories ---- */

    public static HtmlToken openTag(String name, Map<String, String> attrs) {
        return new HtmlToken(TokenType.OPEN_TAG, name, null, attrs);
    }

    public static HtmlToken closeTag(String name) {
        return new HtmlToken(TokenType.CLOSE_TAG, name, null, null);
    }

    public static HtmlToken selfClosing(String name, Map<String, String> attrs) {
        return new HtmlToken(TokenType.SELF_CLOSING_TAG, name, null, attrs);
    }

    public static HtmlToken text(String content) {
        return new HtmlToken(TokenType.TEXT, null, content, null);
    }

    public static HtmlToken comment(String body) {
        return new HtmlToken(TokenType.COMMENT, null, body, null);
    }

    public static HtmlToken doctype() {
        return new HtmlToken(TokenType.DOCTYPE, null, null, null);
    }

    public static HtmlToken eof() {
        return new HtmlToken(TokenType.EOF, null, null, null);
    }

    /* ---- accessors ---- */

    public TokenType getType()              { return type; }
    public String getTagName()              { return tagName; }
    public String getText()                 { return text; }
    public Map<String, String> getAttributes() { return attributes; }
    public String getAttribute(String key)  { return attributes.getOrDefault(key.toLowerCase(), null); }

    @Override
    public String toString() {
        return switch (type) {
            case OPEN_TAG       -> "<" + tagName + " " + attributes + ">";
            case CLOSE_TAG      -> "</" + tagName + ">";
            case SELF_CLOSING_TAG -> "<" + tagName + " " + attributes + "/>";
            case TEXT           -> "TEXT(\"" + truncate(text, 40) + "\")";
            case COMMENT        -> "<!-- " + truncate(text, 30) + " -->";
            case DOCTYPE        -> "<!DOCTYPE html>";
            case EOF            -> "EOF";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}