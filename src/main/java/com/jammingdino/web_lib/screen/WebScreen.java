package com.jammingdino.web_lib.screen;

import com.jammingdino.web_lib.html.HtmlRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * A Minecraft {@link Screen} that renders a {@link WebPage}.
 *
 * Features:
 *  - Back/forward navigation history
 *  - Address bar display (title bar)
 *  - Optional browser chrome (back/forward buttons, title bar)
 *  - Mouse, scroll, and keyboard forwarding to the WebPage
 *  - System event handling (navigate, reload, alert)
 *
 * Usage from a mod:
 * <pre>{@code
 *   // Simple one-shot:
 *   Minecraft.getInstance().setScreen(new WebScreen("My Page", myHtmlString));
 *
 *   // With a page loader (for navigation):
 *   Minecraft.getInstance().setScreen(new WebScreen("Home", homeHtml, url -> loadPage(url)));
 * }</pre>
 */
public class WebScreen extends Screen {

    /* ── chrome dimensions ── */
    private static final int CHROME_HEIGHT  = 16; // address bar height
    private static final int CHROME_BG      = 0xFF2D2D2D;
    private static final int CHROME_TEXT    = 0xFFCCCCCC;
    private static final int CHROME_BUTTON  = 0xFF444444;
    private static final int CHROME_BUTTON_HOVER = 0xFF666666;

    /* ── config ── */
    private boolean showChrome = true;

    /* ── page state ── */
    private WebPage currentPage;
    private final Deque<WebPage> backStack   = new ArrayDeque<>();
    private final Deque<WebPage> forwardStack = new ArrayDeque<>();

    /* ── page loading ── */
    private final PageLoader pageLoader;

    /* ── renderer ── */
    private final HtmlRenderer renderer = new HtmlRenderer();

    /* ── chrome interaction ── */
    private boolean backHovered    = false;
    private boolean forwardHovered = false;
    private boolean reloadHovered  = false;

    /* ─────────────── constructors ─────────────── */

    /** Minimal constructor – no navigation support. */
    public WebScreen(String title, String html) {
        this(title, html, null);
    }

    /**
     * Full constructor.
     *
     * @param title      Screen title (used in Minecraft's escape menu)
     * @param html       Initial HTML content
     * @param pageLoader Called when a navigation event fires (href click, navigate() call).
     *                   Returns new HTML for the target URL, or null to cancel.
     */
    public WebScreen(String title, String html, PageLoader pageLoader) {
        super(Component.literal(title));
        this.pageLoader = pageLoader;
        this.currentPage = buildPage(html, title);
    }

    /** Construct from an existing WebPage object. */
    public WebScreen(WebPage page, PageLoader pageLoader) {
        super(Component.literal(page.getTitle().isEmpty() ? "WebLib" : page.getTitle()));
        this.pageLoader  = pageLoader;
        this.currentPage = page;
        attachSystemHandler(page);
    }

    /* ─────────────── screen lifecycle ─────────────── */

    @Override
    protected void init() {
        relayout();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    /* ─────────────── rendering ─────────────── */

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int contentY = showChrome ? CHROME_HEIGHT : 0;
        int contentH = height - contentY;

        // Page content
        if (currentPage != null && currentPage.getRootBox() != null) {
            renderer.render(graphics, currentPage.getRootBox(), 0, contentY, width, contentH);
        }

        // Chrome (drawn on top)
        if (showChrome) renderChrome(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderChrome(GuiGraphics graphics, int mouseX, int mouseY) {
        // Background bar
        graphics.fill(0, 0, width, CHROME_HEIGHT, CHROME_BG);

        // Back button (◀)
        backHovered = mouseX >= 2 && mouseX <= 12 && mouseY >= 2 && mouseY <= 14;
        int backBg  = !backStack.isEmpty() ? (backHovered ? CHROME_BUTTON_HOVER : CHROME_BUTTON) : 0xFF1A1A1A;
        graphics.fill(2, 2, 13, 14, backBg);
        graphics.drawString(font, "◀", 4, 4, !backStack.isEmpty() ? CHROME_TEXT : 0xFF555555, false);

        // Forward button (▶)
        forwardHovered = mouseX >= 15 && mouseX <= 25 && mouseY >= 2 && mouseY <= 14;
        int fwdBg  = !forwardStack.isEmpty() ? (forwardHovered ? CHROME_BUTTON_HOVER : CHROME_BUTTON) : 0xFF1A1A1A;
        graphics.fill(15, 2, 26, 14, fwdBg);
        graphics.drawString(font, "▶", 17, 4, !forwardStack.isEmpty() ? CHROME_TEXT : 0xFF555555, false);

        // Reload button (↺)
        reloadHovered = mouseX >= 28 && mouseX <= 38 && mouseY >= 2 && mouseY <= 14;
        graphics.fill(28, 2, 39, 14, reloadHovered ? CHROME_BUTTON_HOVER : CHROME_BUTTON);
        graphics.drawString(font, "↺", 30, 4, CHROME_TEXT, false);

        // Address bar
        graphics.fill(42, 2, width - 2, 14, 0xFF1A1A1A);
        String urlText = currentPage != null ? currentPage.getUrl() : "";
        String titleText = (currentPage != null && !currentPage.getTitle().isEmpty())
                ? currentPage.getTitle() + (urlText.isEmpty() ? "" : "  —  " + urlText)
                : urlText;
        if (!titleText.isBlank()) {
            graphics.drawString(font, titleText, 45, 4, CHROME_TEXT, false);
        }
    }

    /* ─────────────── mouse input ─────────────── */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (showChrome && my < CHROME_HEIGHT) {
            if (backHovered    && !backStack.isEmpty())    { navigateBack();    return true; }
            if (forwardHovered && !forwardStack.isEmpty()) { navigateForward(); return true; }
            if (reloadHovered)                             { reloadPage();      return true; }
            return false;
        }

        if (currentPage != null) {
            int contentY = showChrome ? CHROME_HEIGHT : 0;
            return currentPage.mouseClicked(mx, my - contentY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (currentPage != null) currentPage.mouseReleased((int) mouseX, (int) mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (currentPage != null) {
            int contentY = showChrome ? CHROME_HEIGHT : 0;
            currentPage.mouseMoved((int) mouseX, (int) mouseY - contentY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentPage != null) return currentPage.mouseScrolled((int)mouseX, (int)mouseY, scrollY);
        return false;
    }

    /* ─────────────── keyboard input ─────────────── */

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (currentPage != null) return currentPage.charTyped(codePoint);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentPage != null && currentPage.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /* ─────────────── resize ─────────────── */

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int newWidth, int newHeight) {
        super.resize(minecraft, newWidth, newHeight);
        relayout();
    }

    /* ─────────────── navigation ─────────────── */

    public void navigateTo(String url) {
        if (pageLoader == null) return;
        String html = pageLoader.load(url);
        if (html == null) return;

        if (currentPage != null) backStack.push(currentPage);
        forwardStack.clear();

        currentPage = buildPage(html, url);
        currentPage.setUrl(url);
        relayout();
    }

    public void navigateBack() {
        if (backStack.isEmpty()) return;
        if (currentPage != null) forwardStack.push(currentPage);
        currentPage = backStack.pop();
        relayout();
    }

    public void navigateForward() {
        if (forwardStack.isEmpty()) return;
        if (currentPage != null) backStack.push(currentPage);
        currentPage = forwardStack.pop();
        relayout();
    }

    public void reloadPage() {
        if (currentPage != null) {
            currentPage.reload();
            relayout();
        }
    }

    /* ─────────────── helpers ─────────────── */

    private WebPage buildPage(String html, String urlOrTitle) {
        WebPage page = new WebPage(html);
        page.setUrl(urlOrTitle);
        attachSystemHandler(page);
        return page;
    }

    private void attachSystemHandler(WebPage page) {
        page.setSystemEventHandler((eventType, data) -> {
            switch (eventType) {
                case "navigate" -> { if (minecraft != null) minecraft.tell(() -> navigateTo(data)); }
                case "reload"   -> { if (minecraft != null) minecraft.tell(this::reloadPage); }
                case "alert"    -> {
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(
                                Component.literal("[web_lib] " + data), false);
                    }
                }
            }
        });
    }

    private void relayout() {
        if (currentPage != null) {
            int contentH = showChrome ? height - CHROME_HEIGHT : height;
            currentPage.layout(width, contentH);
        }
    }

    /* ─────────────── configuration ─────────────── */

    public WebScreen withoutChrome() { this.showChrome = false; return this; }
    public WebScreen withChrome()    { this.showChrome = true;  return this; }

    public WebPage getCurrentPage() { return currentPage; }

    /* ─────────────── PageLoader interface ─────────────── */

    /**
     * Implement this to support navigation between pages.
     * Given a URL/href, return the HTML to render, or null to cancel navigation.
     */
    @FunctionalInterface
    public interface PageLoader {
        String load(String url);
    }
}