package com.jammingdino.web_lib.html;

import java.util.*;

/**
 * A node in the in-memory DOM tree.
 *
 * Node types:
 *   ELEMENT  – an HTML tag with attributes and children
 *   TEXT     – a text node (no tag name, no attributes)
 *   DOCUMENT – the synthetic root wrapping the whole tree
 */
public class DomNode {

    public enum NodeType { DOCUMENT, ELEMENT, TEXT }

    /* ── identity ── */
    private final NodeType nodeType;
    private final String tagName;           // lowercase; null for TEXT/DOCUMENT
    private final Map<String, String> attributes;

    /* ── content ── */
    private String textContent;             // only for TEXT nodes

    /* ── tree links ── */
    private DomNode parent;
    private final List<DomNode> children = new ArrayList<>();

    /* ── computed style (filled in by CssEngine) ── */
    private final Map<String, String> computedStyle = new LinkedHashMap<>();

    /* ── scripting payload (arbitrary key/value store for JS bridge) ── */
    private final Map<String, Object> dataset = new LinkedHashMap<>();

    /* ─────────────────── constructors ─────────────────── */

    private DomNode(NodeType type, String tagName, Map<String, String> attrs) {
        this.nodeType   = type;
        this.tagName    = tagName;
        this.attributes = attrs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attrs);
    }

    public static DomNode document() {
        return new DomNode(NodeType.DOCUMENT, "#document", null);
    }

    public static DomNode element(String tag, Map<String, String> attrs) {
        return new DomNode(NodeType.ELEMENT, tag.toLowerCase(), attrs);
    }

    public static DomNode text(String content) {
        DomNode n = new DomNode(NodeType.TEXT, "#text", null);
        n.textContent = content;
        return n;
    }

    /* ─────────────────── tree manipulation ─────────────── */

    public void appendChild(DomNode child) {
        child.parent = this;
        children.add(child);
    }

    public void removeChild(DomNode child) {
        children.remove(child);
        if (child.parent == this) child.parent = null;
    }

    /* ─────────────────── attribute helpers ─────────────── */

    public String getAttribute(String key) {
        return attributes.getOrDefault(key.toLowerCase(), null);
    }

    public void setAttribute(String key, String value) {
        attributes.put(key.toLowerCase(), value);
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key.toLowerCase());
    }

    /** Returns the 'id' attribute, or empty string. */
    public String getId() { return attributes.getOrDefault("id", ""); }

    /** Returns all classes from the 'class' attribute as a Set. */
    public Set<String> getClasses() {
        String cls = attributes.getOrDefault("class", "");
        if (cls.isBlank()) return Collections.emptySet();
        return new LinkedHashSet<>(Arrays.asList(cls.trim().split("\\s+")));
    }

    /* ─────────────────── computed style ─────────────────── */

    public void setComputedStyle(String property, String value) {
        computedStyle.put(property, value);
    }

    public String getComputedStyle(String property) {
        return computedStyle.getOrDefault(property, null);
    }

    public String getComputedStyle(String property, String fallback) {
        return computedStyle.getOrDefault(property, fallback);
    }

    public Map<String, String> getComputedStyleMap() {
        return Collections.unmodifiableMap(computedStyle);
    }

    /** Merge an inline style string (e.g. "color:red;font-size:12px") into computed style. */
    public void applyInlineStyle(String inline) {
        if (inline == null || inline.isBlank()) return;
        for (String decl : inline.split(";")) {
            int colon = decl.indexOf(':');
            if (colon < 0) continue;
            String prop = decl.substring(0, colon).trim().toLowerCase();
            String val  = decl.substring(colon + 1).trim();
            if (!prop.isEmpty()) computedStyle.put(prop, val);
        }
    }

    /* ─────────────────── dataset (JS bridge) ─────────────── */

    public void setData(String key, Object value) { dataset.put(key, value); }
    public Object getData(String key)              { return dataset.get(key); }

    /* ─────────────────── query helpers ─────────────────── */

    /** Returns the first descendant matching the given id, or null. */
    public DomNode getElementById(String id) {
        for (DomNode child : children) {
            if (id.equals(child.getId())) return child;
            DomNode found = child.getElementById(id);
            if (found != null) return found;
        }
        return null;
    }

    /** Returns all descendants matching the given tag name. */
    public List<DomNode> getElementsByTagName(String tag) {
        List<DomNode> result = new ArrayList<>();
        collectByTag(tag.toLowerCase(), result);
        return result;
    }

    private void collectByTag(String tag, List<DomNode> out) {
        for (DomNode child : children) {
            if (tag.equals(child.tagName)) out.add(child);
            child.collectByTag(tag, out);
        }
    }

    /** Returns all descendants that have the given CSS class. */
    public List<DomNode> getElementsByClassName(String cls) {
        List<DomNode> result = new ArrayList<>();
        collectByClass(cls, result);
        return result;
    }

    private void collectByClass(String cls, List<DomNode> out) {
        for (DomNode child : children) {
            if (child.getClasses().contains(cls)) out.add(child);
            child.collectByClass(cls, out);
        }
    }

    /* ─────────────────── accessors ─────────────────── */

    public NodeType getNodeType()             { return nodeType; }
    public String   getTagName()              { return tagName; }
    public String   getTextContent()          { return textContent; }
    public void     setTextContent(String t)  { textContent = t; }
    public DomNode  getParent()               { return parent; }
    public List<DomNode> getChildren()        { return Collections.unmodifiableList(children); }
    public Map<String, String> getAttributes(){ return Collections.unmodifiableMap(attributes); }

    public boolean isElement()  { return nodeType == NodeType.ELEMENT; }
    public boolean isText()     { return nodeType == NodeType.TEXT; }
    public boolean isDocument() { return nodeType == NodeType.DOCUMENT; }

    /* ─────────────────── debug ─────────────────── */

    @Override
    public String toString() {
        return switch (nodeType) {
            case TEXT     -> "\"" + (textContent == null ? "" : textContent.replace("\n", "\\n")) + "\"";
            case DOCUMENT -> "#document";
            case ELEMENT  -> "<" + tagName + (attributes.isEmpty() ? "" : " " + attributes) + ">";
        };
    }

    /** Pretty-print the subtree (for debugging). */
    public String toTreeString() {
        StringBuilder sb = new StringBuilder();
        buildTree(sb, 0);
        return sb.toString();
    }

    private void buildTree(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth)).append(this).append("\n");
        for (DomNode c : children) c.buildTree(sb, depth + 1);
    }
}
