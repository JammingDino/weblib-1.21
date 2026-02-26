package com.jammingdino.web_lib.layout;

import com.jammingdino.web_lib.html.DomNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A rectangular layout box produced by the layout engine for one {@link DomNode}.
 *
 * Coordinate system: top-left origin, y increases downward (same as Minecraft GUI).
 *
 * Content area  = (contentX, contentY, contentWidth, contentHeight)
 * Padding box   = content + padding
 * Border box    = padding + border
 * Margin box    = border + margin (used for positioning siblings)
 */
public class LayoutBox {

    /* ── source ── */
    private final DomNode node;

    /* ── box model dimensions ── */
    public int x, y;                     // top-left of border box
    public int width, height;            // border box size

    public int paddingTop, paddingRight, paddingBottom, paddingLeft;
    public int borderTop, borderRight, borderBottom, borderLeft;
    public int marginTop, marginRight, marginBottom, marginLeft;

    /* ── content area (inside padding) ── */
    public int contentX, contentY;       // absolute position
    public int contentWidth, contentHeight;

    /* ── display type ── */
    public DisplayType displayType = DisplayType.BLOCK;

    /* ── children boxes ── */
    private final List<LayoutBox> children = new ArrayList<>();

    /* ── text run (for inline/text nodes) ── */
    public String textRun;               // resolved text for TEXT nodes
    public int baseline;                 // y-offset of text baseline within content area

    /* ── scroll ── */
    public int scrollTop  = 0;
    public int scrollLeft = 0;
    public boolean overflowHidden = false;
    public boolean scrollable = false;

    /* ── z-index ── */
    public int zIndex = 0;

    public LayoutBox(DomNode node) {
        this.node = node;
    }

    /* ─────────────── accessors ─────────────── */

    public DomNode getNode()              { return node; }
    public List<LayoutBox> getChildren()  { return children; }
    public void addChild(LayoutBox child) { children.add(child); }

    /** Inner width available for child content (content box width). */
    public int innerWidth()  { return Math.max(0, contentWidth); }
    /** Inner height available for child content (content box height). */
    public int innerHeight() { return Math.max(0, contentHeight); }

    /** Bottom edge of the margin box (used to position next sibling). */
    public int marginBoxBottom() { return y + height + marginBottom; }
    /** Right edge of the margin box. */
    public int marginBoxRight()  { return x + width + marginRight; }

    /* ─────────────── geometry helpers ─────────────── */

    /** Returns true if the point (px, py) falls inside the border box. */
    public boolean containsPoint(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** Returns true if the point is inside the content area. */
    public boolean contentContains(int px, int py) {
        return px >= contentX && px < contentX + contentWidth
                && py >= contentY && py < contentY + contentHeight;
    }

    /**
     * Hit-test: returns the deepest LayoutBox under (px, py), or null.
     * Searches children in reverse order (last-painted on top).
     */
    public LayoutBox hitTest(int px, int py) {
        if (!containsPoint(px, py)) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            LayoutBox hit = children.get(i).hitTest(px, py);
            if (hit != null) return hit;
        }
        return this;
    }

    @Override
    public String toString() {
        String tag = node == null ? "null" : node.isText() ? "\"" + node.getTextContent() + "\"" : node.getTagName();
        return "Box[" + tag + " " + x + "," + y + " " + width + "×" + height + " " + displayType + "]";
    }
}