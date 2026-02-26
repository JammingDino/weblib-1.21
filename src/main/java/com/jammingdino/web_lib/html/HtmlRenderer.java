package com.jammingdino.web_lib.html;

import com.jammingdino.web_lib.css.CssEngine;
import com.jammingdino.web_lib.html.DomNode;
import com.jammingdino.web_lib.layout.DisplayType;
import com.jammingdino.web_lib.layout.LayoutBox;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders a {@link LayoutBox} tree to the screen using Minecraft's {@link GuiGraphics}.
 *
 * Rendering pipeline per box:
 *   1. Background color / image
 *   2. Border (solid, 1-pixel lines for each side)
 *   3. Content (text, img src, list bullet)
 *   4. Children (depth-first, sorted by z-index)
 *
 * Clipping is approximated by push/pop of a scissor rectangle for overflow:hidden boxes.
 */
public class HtmlRenderer {

    private final Font font;
    private final Minecraft mc;

    /* Scissor stack – each entry is (x, y, w, h) in screen pixels */
    private record ScissorRect(int x, int y, int w, int h) {}
    private final List<ScissorRect> scissorStack = new ArrayList<>();

    /* Current scroll offsets accumulated from parent boxes */
    private int scrollOffsetX = 0;
    private int scrollOffsetY = 0;

    public HtmlRenderer() {
        this.mc   = Minecraft.getInstance();
        this.font = mc.font;
    }

    /* ─────────────────── public API ─────────────────── */

    /**
     * Render the entire layout tree.
     *
     * @param graphics   Minecraft's GuiGraphics for the current frame
     * @param root       Root LayoutBox (typically the document box)
     * @param clipX      Clip region X (usually the WebScreen's origin)
     * @param clipY      Clip region Y
     * @param clipW      Clip region width
     * @param clipH      Clip region height
     */
    public void render(GuiGraphics graphics, LayoutBox root, int clipX, int clipY, int clipW, int clipH) {
        scissorStack.clear();
        scrollOffsetX = 0;
        scrollOffsetY = 0;

        // Only push a top-level scissor if we actually need to clip
        // (i.e. the clip region is smaller than the full window).
        // Pushing a full-screen scissor causes Y-flip issues in some GL states.
        boolean needsClip = clipX > 0 || clipY > 0
                || clipW < mc.getWindow().getGuiScaledWidth()
                || clipH < mc.getWindow().getGuiScaledHeight();
        if (needsClip) pushScissor(clipX, clipY, clipW, clipH);
        renderBox(graphics, root);
        if (needsClip) popScissor();

        // Draw scrollbar if content is taller than the clip region
        renderScrollbar(graphics, root, clipX, clipY, clipW, clipH);
    }

    private void renderScrollbar(GuiGraphics graphics, LayoutBox root, int clipX, int clipY, int clipW, int clipH) {
        int contentHeight = root.height;
        if (contentHeight <= clipH) return; // no scroll needed

        int scrollY = root.scrollTop;
        int trackX  = clipX + clipW - 4;
        int trackH  = clipH;

        // Track
        graphics.fill(trackX, clipY, trackX + 4, clipY + trackH, 0xFF1e1e1e);

        // Thumb - proportional size, clamped to minimum 12px
        int thumbH  = Math.max(12, (int)((float) clipH / contentHeight * trackH));
        int maxScroll = contentHeight - clipH;
        int thumbY  = clipY + (maxScroll > 0 ? (int)((float) scrollY / maxScroll * (trackH - thumbH)) : 0);

        graphics.fill(trackX + 1, thumbY, trackX + 3, thumbY + thumbH, 0xFF7a5cb8);
    }

    /* ─────────────────── recursive renderer ─────────────────── */

    private void renderBox(GuiGraphics graphics, LayoutBox box) {
        if (box.displayType == DisplayType.NONE) return;

        int ox = scrollOffsetX;
        int oy = scrollOffsetY;

        DomNode node = box.getNode();

        if (node.isText()) {
            renderTextBox(graphics, box);
            return;
        }
        if (!node.isElement() && !node.isDocument()) return;

        int bx = box.x - ox;
        int by = box.y - oy;
        int bw = box.width;
        int bh = box.height;

        // 1. Background
        renderBackground(graphics, node, bx, by, bw, bh);

        // 2. Border
        renderBorder(graphics, node, box, bx, by, bw, bh);

        // 3. Content (images, hr, etc.)
        renderContent(graphics, node, box, bx, by);

        // 4. Children (sorted by z-index)
        boolean clipping = box.overflowHidden;
        if (clipping) {
            pushScissor(bx + box.borderLeft + box.paddingLeft,
                    by + box.borderTop  + box.paddingTop,
                    box.contentWidth,
                    box.contentHeight);
        }

        // Accumulate scroll
        scrollOffsetX += box.scrollLeft;
        scrollOffsetY += box.scrollTop;

        List<LayoutBox> sorted = new ArrayList<>(box.getChildren());
        sorted.sort(Comparator.comparingInt(b -> b.zIndex));
        for (LayoutBox child : sorted) {
            renderBox(graphics, child);
        }

        scrollOffsetX -= box.scrollLeft;
        scrollOffsetY -= box.scrollTop;

        if (clipping) popScissor();
    }

    /* ─────────────────── background ─────────────────── */

    private void renderBackground(GuiGraphics graphics, DomNode node, int x, int y, int w, int h) {
        String bgColor = node.getComputedStyle("background-color");
        if (bgColor == null) bgColor = node.getComputedStyle("background");
        if (bgColor != null && !bgColor.equals("transparent") && !bgColor.equals("none")) {
            int argb = CssEngine.resolveColor(bgColor);
            if ((argb >>> 24) != 0) {
                graphics.fill(x, y, x + w, y + h, argb);
            }
        }

        // Background image (ResourceLocation only – no external URLs at render time)
        String bgImage = node.getComputedStyle("background-image");
        if (bgImage != null && bgImage.startsWith("url(")) {
            String url = bgImage.replaceAll("url\\(['\"]?|['\"]?\\)", "").trim();
            if (!url.isBlank()) {
                try {
                    ResourceLocation rl = ResourceLocation.tryParse(url);
                    if (rl != null) {
                        // blit(ResourceLocation, x, y, uOffset, vOffset, width, height, texWidth, texHeight)
                        graphics.blit(rl, x, y, 0, 0, w, h, w, h);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /* ─────────────────── border ─────────────────── */

    private void renderBorder(GuiGraphics graphics, DomNode node, LayoutBox box, int x, int y, int w, int h) {
        String borderColor = node.getComputedStyle("border-color", "#000000");
        // Individual side colors
        String topColor    = node.getComputedStyle("border-top-color",    borderColor);
        String rightColor  = node.getComputedStyle("border-right-color",  borderColor);
        String bottomColor = node.getComputedStyle("border-bottom-color", borderColor);
        String leftColor   = node.getComputedStyle("border-left-color",   borderColor);

        // Check border-style isn't 'none'
        String borderStyle = node.getComputedStyle("border-style", "solid");
        if ("none".equals(borderStyle) || "hidden".equals(borderStyle)) return;

        if (box.borderTop    > 0) graphics.fill(x,             y,             x + w,          y + box.borderTop,    CssEngine.resolveColor(topColor));
        if (box.borderBottom > 0) graphics.fill(x,             y + h - box.borderBottom, x + w, y + h,             CssEngine.resolveColor(bottomColor));
        if (box.borderLeft   > 0) graphics.fill(x,             y,             x + box.borderLeft,   y + h,         CssEngine.resolveColor(leftColor));
        if (box.borderRight  > 0) graphics.fill(x + w - box.borderRight, y,   x + w,          y + h,               CssEngine.resolveColor(rightColor));
    }

    /* ─────────────────── content ─────────────────── */

    private void renderContent(GuiGraphics graphics, DomNode node, LayoutBox box, int bx, int by) {
        String tag = node.getTagName();
        int cx = box.contentX - scrollOffsetX;
        int cy = box.contentY - scrollOffsetY;

        switch (tag) {
            case "img" -> {
                String src = node.getAttribute("src");
                if (src != null && !src.isBlank()) {
                    ResourceLocation rl = ResourceLocation.tryParse(src);
                    if (rl != null) {
                        try {
                            graphics.blit(rl, cx, cy, 0, 0,
                                    box.contentWidth, box.contentHeight,
                                    box.contentWidth, box.contentHeight);
                        } catch (Exception ignored) {}
                    }
                }
            }
            case "hr" -> {
                int color = CssEngine.resolveColor(node.getComputedStyle("border-color", "#888888"));
                graphics.fill(cx, cy + box.contentHeight / 2,
                        cx + box.contentWidth, cy + box.contentHeight / 2 + 1, color);
            }
            case "li" -> {
                // Render bullet / number before children
                String listStyle = node.getComputedStyle("list-style-type", "disc");
                int color = CssEngine.resolveColor(node.getComputedStyle("color", "#000000"));
                if (listStyle.equals("disc") || listStyle.equals("circle") || listStyle.equals("square")) {
                    String bullet = listStyle.equals("square") ? "■" : listStyle.equals("circle") ? "○" : "•";
                    graphics.drawString(font, bullet, cx - 12, cy, color, false);
                }
                // Numbered lists: the number would need to be tracked by parent – deferred
            }
            case "input" -> renderInput(graphics, node, box, cx, cy);
            case "button" -> renderButton(graphics, node, box, cx, cy);
            case "select" -> renderSelect(graphics, node, box, cx, cy);
        }
    }

    /* ─────────────────── form element rendering ─────────────────── */

    private void renderInput(GuiGraphics graphics, DomNode node, LayoutBox box, int cx, int cy) {
        String type  = node.getAttribute("type") == null ? "text" : node.getAttribute("type");
        String value = node.getAttribute("value") == null ? "" : node.getAttribute("value");

        int bgColor     = CssEngine.resolveColor(node.getComputedStyle("background-color", "#FFFFFF"));
        int borderColor = CssEngine.resolveColor(node.getComputedStyle("border-color", "#888888"));
        int textColor   = CssEngine.resolveColor(node.getComputedStyle("color", "#000000"));

        // Background + border drawn by renderBackground/renderBorder already
        // Just render the text value
        if (type.equals("checkbox")) {
            boolean checked = "checked".equals(node.getAttribute("checked")) || Boolean.TRUE.equals(node.getData("checked"));
            graphics.fill(cx, cy, cx + box.contentWidth, cy + box.contentHeight, bgColor);
            graphics.fill(cx, cy, cx + box.contentWidth, cy + box.contentHeight, 0x00000000); // outline done by border
            if (checked) graphics.drawString(font, "✔", cx + 1, cy, textColor, false);
        } else if (type.equals("radio")) {
            boolean checked = "checked".equals(node.getAttribute("checked")) || Boolean.TRUE.equals(node.getData("checked"));
            graphics.fill(cx, cy, cx + box.contentWidth, cy + box.contentHeight, bgColor);
            if (checked) graphics.drawString(font, "●", cx + 1, cy, textColor, false);
        } else {
            // Text / password / number / email etc.
            String display = type.equals("password") ? "*".repeat(value.length()) : value;
            String placeholder = node.getAttribute("placeholder");
            if (display.isBlank() && placeholder != null) {
                graphics.drawString(font, placeholder, cx + 2, cy, 0xAA888888, false);
            } else {
                graphics.drawString(font, display, cx + 2, cy, textColor, false);
            }
            // Cursor if focused
            if (Boolean.TRUE.equals(node.getData("focus"))) {
                int cursorX = cx + 2 + font.width(display);
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    graphics.fill(cursorX, cy, cursorX + 1, cy + font.lineHeight, textColor);
                }
            }
        }
    }

    private void renderButton(GuiGraphics graphics, DomNode node, LayoutBox box, int cx, int cy) {
        // Button text (children text nodes merged)
        String label = gatherText(node);
        int textColor = CssEngine.resolveColor(node.getComputedStyle("color", "#000000"));
        boolean hover  = Boolean.TRUE.equals(node.getData("hover"));
        boolean active = Boolean.TRUE.equals(node.getData("active"));

        // Highlight tint
        if (active) {
            graphics.fill(box.contentX - scrollOffsetX, box.contentY - scrollOffsetY,
                    box.contentX - scrollOffsetX + box.contentWidth,
                    box.contentY - scrollOffsetY + box.contentHeight,
                    0x33000000);
        } else if (hover) {
            graphics.fill(box.contentX - scrollOffsetX, box.contentY - scrollOffsetY,
                    box.contentX - scrollOffsetX + box.contentWidth,
                    box.contentY - scrollOffsetY + box.contentHeight,
                    0x22FFFFFF);
        }

        int textW = font.width(label);
        int textX = cx + (box.contentWidth - textW) / 2;
        int textY = cy + (box.contentHeight - font.lineHeight) / 2;
        graphics.drawString(font, label, textX, textY, textColor, false);
    }

    private void renderSelect(GuiGraphics graphics, DomNode node, LayoutBox box, int cx, int cy) {
        String selected = (String) node.getData("selectedValue");
        if (selected == null) selected = "";
        int textColor = CssEngine.resolveColor(node.getComputedStyle("color", "#000000"));
        graphics.drawString(font, selected + " ▼", cx + 2, cy, textColor, false);
    }

    /* ─────────────────── text rendering ─────────────────── */

    private void renderTextBox(GuiGraphics graphics, LayoutBox box) {
        DomNode node   = box.getNode();
        String text    = box.textRun != null ? box.textRun : (node.getTextContent() == null ? "" : node.getTextContent());
        if (text.isBlank()) return;

        DomNode parent = node.getParent();
        int color = parent != null
                ? CssEngine.resolveColor(parent.getComputedStyle("color", "#000000"))
                : 0xFF000000;

        boolean shadow = "true".equals(parent != null ? parent.getComputedStyle("text-shadow") : null);
        boolean bold   = "bold".equals(parent != null ? parent.getComputedStyle("font-weight") : null)
                || "700".equals(parent != null ? parent.getComputedStyle("font-weight") : null);

        String decoration = parent != null ? parent.getComputedStyle("text-decoration", "") : "";

        int x = box.contentX - scrollOffsetX;
        int y = box.contentY - scrollOffsetY;

        // Wrap text across lines
        int availW = box.contentWidth > 0 ? box.contentWidth : 200;
        List<String> lines = wrapText(text, availW);
        int lineH = font.lineHeight + 1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (bold) line = "§l" + line;
            graphics.drawString(font, line, x, y + i * lineH, color, shadow);
        }

        // Text decoration (underline / line-through)
        if (decoration.contains("underline")) {
            int tw = font.width(text);
            graphics.fill(x, y + font.lineHeight, x + Math.min(tw, availW), y + font.lineHeight + 1, color);
        }
        if (decoration.contains("line-through")) {
            int tw = font.width(text);
            int mid = y + font.lineHeight / 2;
            graphics.fill(x, mid, x + Math.min(tw, availW), mid + 1, color);
        }
    }

    /* ─────────────────── text utilities ─────────────────── */

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("(?<=\\s)|(?=\\s)");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (font.width(current + word) <= maxWidth) {
                current.append(word);
            } else {
                if (!current.isEmpty()) lines.add(current.toString().stripTrailing());
                current = new StringBuilder(word.stripLeading());
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private String gatherText(DomNode node) {
        StringBuilder sb = new StringBuilder();
        for (DomNode child : node.getChildren()) {
            if (child.isText() && child.getTextContent() != null) sb.append(child.getTextContent());
            else if (child.isElement()) sb.append(gatherText(child));
        }
        return sb.toString().trim();
    }

    /* ─────────────────── scissor / clip ─────────────────── */

    private void pushScissor(int x, int y, int w, int h) {
        if (!scissorStack.isEmpty()) {
            ScissorRect parent = scissorStack.get(scissorStack.size() - 1);
            // Intersect with parent
            int nx = Math.max(x, parent.x());
            int ny = Math.max(y, parent.y());
            int nw = Math.min(x + w, parent.x() + parent.w()) - nx;
            int nh = Math.min(y + h, parent.y() + parent.h()) - ny;
            x = nx; y = ny; w = Math.max(0, nw); h = Math.max(0, nh);
        }
        scissorStack.add(new ScissorRect(x, y, w, h));
        applyScissor(x, y, w, h);
    }

    private void popScissor() {
        if (!scissorStack.isEmpty()) scissorStack.remove(scissorStack.size() - 1);
        if (!scissorStack.isEmpty()) {
            ScissorRect s = scissorStack.get(scissorStack.size() - 1);
            applyScissor(s.x(), s.y(), s.w(), s.h());
        } else {
            RenderSystem.disableScissor();
        }
    }

    private void applyScissor(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            RenderSystem.enableScissor(0, 0, 0, 0);
            return;
        }
        // Convert GUI-space coords to framebuffer pixels.
        // GuiScale converts logical px → framebuffer px.
        // OpenGL scissor origin is bottom-left, so Y must be flipped using the
        // actual framebuffer height (getWindow().getHeight()), not the scaled height.
        double scale = mc.getWindow().getGuiScale();
        int fbHeight = mc.getWindow().getHeight(); // real framebuffer height in px
        int sx = (int)Math.round(x * scale);
        int sy = (int)Math.round(fbHeight - (y + h) * scale);
        int sw = (int)Math.round(w * scale);
        int sh = (int)Math.round(h * scale);
        RenderSystem.enableScissor(sx, Math.max(0, sy), Math.max(0, sw), Math.max(0, sh));
    }
}