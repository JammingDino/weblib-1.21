package com.jammingdino.web_lib.layout;

import com.jammingdino.web_lib.css.CssEngine;
import com.jammingdino.web_lib.html.DomNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the styled DOM tree into a tree of positioned {@link LayoutBox}es.
 *
 * Supports:
 *   - Block, inline, inline-block, flex (row/column), list-item, none
 *   - Box model: margin / border / padding / content
 *   - Width: px, %, auto
 *   - Height: px, %, auto (shrink-to-fit)
 *   - Overflow: hidden / scroll
 *   - Flex: flex-direction, justify-content, align-items, flex-wrap, gap
 *   - position: relative (offset only; absolute/fixed are approximated)
 *
 * Text wrapping is handled at a character level for now (no proper shaping).
 * A real mod should use Minecraft's font renderer for line breaking.
 */
public class LayoutEngine {

    private static final int DEFAULT_FONT_SIZE = 8; // Minecraft default font height in px

    /* Viewport dimensions */
    private final int vpWidth;
    private final int vpHeight;

    public LayoutEngine(int viewportWidth, int viewportHeight) {
        this.vpWidth  = viewportWidth;
        this.vpHeight = viewportHeight;
    }

    /* ─────────────────── public entry point ─────────────────── */

    /**
     * Lay out the DOM subtree rooted at {@code root} into the given available area.
     * Returns the root LayoutBox (whose children mirror the DOM).
     */
    public LayoutBox layout(DomNode root, int x, int y, int availableWidth) {
        LayoutBox box = new LayoutBox(root);
        if (root.isDocument()) {
            box.displayType = DisplayType.BLOCK;
            box.x = x; box.y = y;
            box.width = availableWidth;
            box.contentX = x; box.contentY = y;
            box.contentWidth = availableWidth;
            int usedHeight = layoutChildren(box, root, x, y, availableWidth);
            box.contentHeight = usedHeight;
            box.height = usedHeight;
            return box;
        }
        return layoutNode(root, x, y, availableWidth, null);
    }

    /* ─────────────────── single node layout ─────────────────── */

    private LayoutBox layoutNode(DomNode node, int x, int y, int availableWidth, LayoutBox parentBox) {
        LayoutBox box = new LayoutBox(node);

        if (node.isText()) {
            box.displayType = DisplayType.INLINE;
            String text = node.getTextContent() == null ? "" : node.getTextContent();
            // Determine font size from parent
            int fontSize = parentBox != null
                    ? resolveLength(parentBox.getNode().getComputedStyle("font-size"), DEFAULT_FONT_SIZE, DEFAULT_FONT_SIZE)
                    : DEFAULT_FONT_SIZE;
            box.textRun = text;
            box.x = x; box.y = y;
            box.contentX = x; box.contentY = y;
            // Approximate: each character is ~(fontSize * 0.6) wide; height = fontSize
            int lineW = (int) (fontSize * 0.6 * text.length());
            box.contentWidth  = Math.min(lineW, availableWidth);
            box.contentHeight = fontSize;
            box.width  = box.contentWidth;
            box.height = box.contentHeight;
            return box;
        }

        if (!node.isElement()) return box;

        // --- Display type ---
        String displayStr = node.getComputedStyle("display", "block");
        box.displayType = parseDisplay(displayStr);
        if (box.displayType == DisplayType.NONE) {
            box.x = x; box.y = y; box.width = 0; box.height = 0;
            return box;
        }

        // --- Box model: margin, border, padding ---
        int fontSize = resolveLength(node.getComputedStyle("font-size"), DEFAULT_FONT_SIZE, DEFAULT_FONT_SIZE);

        int marginTop    = resolveSide(node, "margin-top",    fontSize, availableWidth);
        int marginRight  = resolveSide(node, "margin-right",  fontSize, availableWidth);
        int marginBottom = resolveSide(node, "margin-bottom", fontSize, availableWidth);
        int marginLeft   = resolveSide(node, "margin-left",   fontSize, availableWidth);

        int borderTop    = resolveBorder(node, "border-top-width",    node.getComputedStyle("border-top"));
        int borderRight  = resolveBorder(node, "border-right-width",  node.getComputedStyle("border-right"));
        int borderBottom = resolveBorder(node, "border-bottom-width", node.getComputedStyle("border-bottom"));
        int borderLeft   = resolveBorder(node, "border-left-width",   node.getComputedStyle("border-left"));

        // Shorthand border (all sides)
        String borderShort = node.getComputedStyle("border-width");
        if (borderShort != null && borderTop == 0 && borderRight == 0) {
            int b = resolveLength(borderShort, fontSize, fontSize);
            borderTop = borderRight = borderBottom = borderLeft = b;
        }

        int padTop    = resolveSide(node, "padding-top",    fontSize, availableWidth);
        int padRight  = resolveSide(node, "padding-right",  fontSize, availableWidth);
        int padBottom = resolveSide(node, "padding-bottom", fontSize, availableWidth);
        int padLeft   = resolveSide(node, "padding-left",   fontSize, availableWidth);

        box.marginTop    = marginTop;   box.marginRight  = marginRight;
        box.marginBottom = marginBottom; box.marginLeft = marginLeft;
        box.borderTop    = borderTop;   box.borderRight  = borderRight;
        box.borderBottom = borderBottom; box.borderLeft  = borderLeft;
        box.paddingTop   = padTop;      box.paddingRight = padRight;
        box.paddingBottom = padBottom;  box.paddingLeft  = padLeft;

        // --- Width ---
        int horizontal = padLeft + padRight + borderLeft + borderRight;
        String widthStyle = node.getComputedStyle("width");
        int contentWidth;
        if (widthStyle == null || widthStyle.equals("auto")) {
            contentWidth = availableWidth - marginLeft - marginRight - horizontal;
        } else {
            contentWidth = resolveLength(widthStyle, availableWidth, fontSize) ;
        }
        contentWidth = Math.max(0, contentWidth);

        // --- Position in border box ---
        int borderBoxX = x + marginLeft;
        int borderBoxY = y + marginTop;

        box.x = borderBoxX;
        box.y = borderBoxY;
        box.paddingTop = padTop; box.paddingLeft = padLeft;

        box.contentX = borderBoxX + borderLeft + padLeft;
        box.contentY = borderBoxY + borderTop  + padTop;
        box.contentWidth  = contentWidth;

        // --- Overflow / scroll ---
        String overflow = node.getComputedStyle("overflow", "visible");
        box.overflowHidden = overflow.equals("hidden") || overflow.equals("scroll") || overflow.equals("auto");
        box.scrollable     = overflow.equals("scroll") || overflow.equals("auto");

        // --- z-index ---
        String zStr = node.getComputedStyle("z-index");
        if (zStr != null) { try { box.zIndex = Integer.parseInt(zStr); } catch (NumberFormatException ignored) {} }

        // --- Lay out children ---
        int usedHeight;
        if (box.displayType == DisplayType.FLEX) {
            usedHeight = layoutFlex(box, node, box.contentX, box.contentY, contentWidth, fontSize);
        } else {
            usedHeight = layoutChildren(box, node, box.contentX, box.contentY, contentWidth);
        }

        // --- Height ---
        String heightStyle = node.getComputedStyle("height");
        int contentHeight;
        if (heightStyle == null || heightStyle.equals("auto")) {
            contentHeight = usedHeight;
        } else {
            contentHeight = resolveLength(heightStyle, vpHeight, fontSize);
        }
        contentHeight = Math.max(0, contentHeight);
        box.contentHeight = contentHeight;

        int vertical  = padTop + padBottom + borderTop + borderBottom;
        box.width  = contentWidth  + horizontal;
        box.height = contentHeight + vertical;

        // --- Position offset (position: relative) ---
        String pos = node.getComputedStyle("position");
        if ("relative".equals(pos)) {
            String top  = node.getComputedStyle("top");
            String left = node.getComputedStyle("left");
            if (top  != null) { int off = resolveLength(top,  fontSize, fontSize); box.y += off; box.contentY += off; }
            if (left != null) { int off = resolveLength(left, fontSize, fontSize); box.x += off; box.contentX += off; }
        }

        return box;
    }

    /* ─────────────────── block / inline children ─────────────────── */

    /**
     * Lay out children of a block container.
     * Returns the total height consumed by children.
     */
    private int layoutChildren(LayoutBox parentBox, DomNode parent, int startX, int startY, int availW) {
        int cursorY = startY;
        int inlineX = startX;
        int inlineRowH = 0;

        List<LayoutBox> inlineBoxes = new ArrayList<>();

        for (DomNode child : parent.getChildren()) {
            if (child.isText()) {
                // Flush any pending inline context if empty
                LayoutBox tb = layoutNode(child, inlineX, cursorY, availW - (inlineX - startX), parentBox);
                parentBox.addChild(tb);
                inlineBoxes.add(tb);
                inlineX += tb.width;
                inlineRowH = Math.max(inlineRowH, tb.height);
                continue;
            }
            if (!child.isElement()) continue;

            String display = child.getComputedStyle("display", "block");
            DisplayType dt = parseDisplay(display);

            if (dt == DisplayType.NONE) continue;

            if (dt == DisplayType.INLINE || dt == DisplayType.INLINE_BLOCK) {
                // Simple inline: place next to previous inline
                int childAvailW = Math.max(1, availW - (inlineX - startX));
                LayoutBox cb = layoutNode(child, inlineX, cursorY, childAvailW, parentBox);
                parentBox.addChild(cb);
                inlineBoxes.add(cb);
                inlineX += cb.width + cb.marginRight;
                inlineRowH = Math.max(inlineRowH, cb.height + cb.marginTop + cb.marginBottom);
                // Word-wrap: if we've exceeded available width, start a new row
                if (inlineX - startX > availW && !inlineBoxes.isEmpty()) {
                    cursorY += inlineRowH;
                    inlineX = startX;
                    inlineRowH = 0;
                    inlineBoxes.clear();
                }
            } else {
                // Block-level: finish any open inline row
                if (inlineRowH > 0) {
                    cursorY += inlineRowH;
                    inlineX = startX;
                    inlineRowH = 0;
                    inlineBoxes.clear();
                }
                LayoutBox cb = layoutNode(child, startX, cursorY, availW, parentBox);
                parentBox.addChild(cb);
                cursorY = cb.y + cb.height + cb.marginBottom;
                inlineX = startX;
            }
        }

        // Finish last inline row
        if (inlineRowH > 0) cursorY += inlineRowH;

        return cursorY - startY;
    }

    /* ─────────────────── flexbox layout ─────────────────── */

    private int layoutFlex(LayoutBox parentBox, DomNode parent, int startX, int startY, int availW, int fontSize) {
        String direction  = parent.getComputedStyle("flex-direction", "row");
        String justify    = parent.getComputedStyle("justify-content", "flex-start");
        String alignItems = parent.getComputedStyle("align-items", "stretch");
        String wrap       = parent.getComputedStyle("flex-wrap", "nowrap");
        int gap           = resolveLength(parent.getComputedStyle("gap", "0"), fontSize, fontSize);
        int columnGap     = resolveLength(parent.getComputedStyle("column-gap", parent.getComputedStyle("gap", "0")), fontSize, fontSize);
        int rowGap        = resolveLength(parent.getComputedStyle("row-gap",    parent.getComputedStyle("gap", "0")), fontSize, fontSize);

        boolean isRow = direction.equals("row") || direction.equals("row-reverse");
        boolean reverse = direction.endsWith("-reverse");

        // Collect visible children
        List<DomNode> flexChildren = new ArrayList<>();
        for (DomNode c : parent.getChildren()) {
            if (!c.isText()) {
                String d = c.getComputedStyle("display", "block");
                if (!parseDisplay(d).equals(DisplayType.NONE)) flexChildren.add(c);
            }
        }

        // First pass: lay out each child at (0,0) to get natural sizes
        List<LayoutBox> childBoxes = new ArrayList<>();
        for (DomNode c : flexChildren) {
            LayoutBox cb = layoutNode(c, 0, 0, isRow ? availW : availW, parentBox);
            childBoxes.add(cb);
        }

        // Distribute along the main axis
        if (isRow) {
            return flexRow(parentBox, childBoxes, startX, startY, availW, justify, alignItems, columnGap, rowGap, reverse);
        } else {
            return flexColumn(parentBox, childBoxes, startX, startY, availW, justify, alignItems, rowGap, reverse);
        }
    }

    private int flexRow(LayoutBox parentBox, List<LayoutBox> childBoxes,
                        int startX, int startY, int availW,
                        String justify, String align, int colGap, int rowGap, boolean reverse) {
        int totalChildW = childBoxes.stream().mapToInt(b -> b.width + b.marginLeft + b.marginRight).sum()
                + colGap * Math.max(0, childBoxes.size() - 1);
        int freeSpace = availW - totalChildW;

        int[] xOffsets = distributeMainAxis(justify, childBoxes.size(), freeSpace, colGap, childBoxes);
        int rowH = childBoxes.stream().mapToInt(b -> b.height + b.marginTop + b.marginBottom).max().orElse(0);

        List<LayoutBox> ordered = reverse ? reverseList(childBoxes) : childBoxes;
        for (int i = 0; i < ordered.size(); i++) {
            LayoutBox cb = ordered.get(i);
            int cx = startX + xOffsets[i] + cb.marginLeft;
            int cy = startY + alignOffset(align, cb.height, rowH) + cb.marginTop;
            shift(cb, cx - cb.x, cy - cb.y);
            parentBox.addChild(cb);
        }
        return rowH;
    }

    private int flexColumn(LayoutBox parentBox, List<LayoutBox> childBoxes,
                           int startX, int startY, int availW,
                           String justify, String align, int rowGap, boolean reverse) {
        int totalH = childBoxes.stream().mapToInt(b -> b.height + b.marginTop + b.marginBottom).sum()
                + rowGap * Math.max(0, childBoxes.size() - 1);
        int maxW   = childBoxes.stream().mapToInt(b -> b.width).max().orElse(availW);
        int[] yOff = distributeMainAxis(justify, childBoxes.size(), 0, rowGap, childBoxes);
        // justify-content for column uses cumulative offsets
        int cursor = startY;
        List<LayoutBox> ordered = reverse ? reverseList(childBoxes) : childBoxes;
        for (LayoutBox cb : ordered) {
            int cx = startX + alignOffset(align, cb.width, maxW) + cb.marginLeft;
            int cy = cursor + cb.marginTop;
            shift(cb, cx - cb.x, cy - cb.y);
            parentBox.addChild(cb);
            cursor += cb.height + cb.marginTop + cb.marginBottom + rowGap;
        }
        return cursor - startY;
    }

    private int[] distributeMainAxis(String justify, int count, int freeSpace, int gap, List<LayoutBox> boxes) {
        int[] offsets = new int[count];
        if (count == 0) return offsets;
        switch (justify) {
            case "flex-end", "end" -> {
                int x = freeSpace;
                for (int i = 0; i < count; i++) {
                    offsets[i] = x;
                    x += boxes.get(i).width + boxes.get(i).marginLeft + boxes.get(i).marginRight + gap;
                }
            }
            case "center" -> {
                int x = freeSpace / 2;
                for (int i = 0; i < count; i++) {
                    offsets[i] = x;
                    x += boxes.get(i).width + boxes.get(i).marginLeft + boxes.get(i).marginRight + gap;
                }
            }
            case "space-between" -> {
                int between = count > 1 ? freeSpace / (count - 1) : 0;
                int x = 0;
                for (int i = 0; i < count; i++) {
                    offsets[i] = x;
                    x += boxes.get(i).width + boxes.get(i).marginLeft + boxes.get(i).marginRight + between + gap;
                }
            }
            case "space-around" -> {
                int around = freeSpace / count;
                int x = around / 2;
                for (int i = 0; i < count; i++) {
                    offsets[i] = x;
                    x += boxes.get(i).width + boxes.get(i).marginLeft + boxes.get(i).marginRight + around + gap;
                }
            }
            case "space-evenly" -> {
                int between = freeSpace / (count + 1);
                int x = between;
                for (int i = 0; i < count; i++) {
                    offsets[i] = x;
                    x += boxes.get(i).width + boxes.get(i).marginLeft + boxes.get(i).marginRight + between + gap;
                }
            }
            default -> { // flex-start
                int x = 0;
                for (int i = 0; i < count; i++) {
                    offsets[i] = x;
                    x += boxes.get(i).width + boxes.get(i).marginLeft + boxes.get(i).marginRight + gap;
                }
            }
        }
        return offsets;
    }

    private int alignOffset(String align, int childSize, int containerSize) {
        return switch (align) {
            case "flex-end", "end"    -> containerSize - childSize;
            case "center"             -> (containerSize - childSize) / 2;
            default                   -> 0; // flex-start / stretch
        };
    }

    /* ─────────────────── utilities ─────────────────── */

    private static void shift(LayoutBox box, int dx, int dy) {
        box.x += dx; box.y += dy;
        box.contentX += dx; box.contentY += dy;
        for (LayoutBox child : box.getChildren()) shift(child, dx, dy);
    }

    private static <T> List<T> reverseList(List<T> list) {
        List<T> rev = new ArrayList<>(list);
        java.util.Collections.reverse(rev);
        return rev;
    }

    private int resolveSide(DomNode node, String prop, int fontSize, int availW) {
        String val = node.getComputedStyle(prop);
        if (val == null || val.equals("auto")) return 0;
        return CssEngine.resolveLength(val, availW, fontSize, vpWidth);
    }

    private int resolveBorder(DomNode node, String widthProp, String shorthand) {
        String val = node.getComputedStyle(widthProp);
        if (val == null && shorthand != null) {
            // Parse "1px solid red" → take the first token as width
            String[] parts = shorthand.trim().split("\\s+");
            val = parts.length > 0 ? parts[0] : null;
        }
        if (val == null) return 0;
        return CssEngine.resolveLength(val, 1, 1, vpWidth);
    }

    private int resolveLength(String val, int parentPx, int fontPx) {
        return CssEngine.resolveLength(val, parentPx, fontPx, vpWidth);
    }

    private static DisplayType parseDisplay(String value) {
        if (value == null) return DisplayType.BLOCK;
        return switch (value.trim().toLowerCase()) {
            case "none"         -> DisplayType.NONE;
            case "inline"       -> DisplayType.INLINE;
            case "inline-block" -> DisplayType.INLINE_BLOCK;
            case "flex","inline-flex" -> DisplayType.FLEX;
            case "list-item"    -> DisplayType.LIST_ITEM;
            case "table"        -> DisplayType.TABLE;
            case "table-row"    -> DisplayType.TABLE_ROW;
            case "table-cell"   -> DisplayType.TABLE_CELL;
            default             -> DisplayType.BLOCK;
        };
    }
}