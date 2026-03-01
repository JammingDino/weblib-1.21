package com.jammingdino.web_lib.screen;

import com.jammingdino.web_lib.Config;
import com.jammingdino.web_lib.WebLib;
import com.jammingdino.web_lib.html.HtmlRenderer;
import com.jammingdino.web_lib.script.ScriptBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

/**
 * The web_lib mod configuration screen, rendered entirely by web_lib itself.
 *
 * Registered as the config screen factory in {@link com.jammingdino.web_lib.WebLib}
 * so NeoForge / Mod Menu shows it when the player clicks "Config" on web_lib.
 *
 * All config mutations go through the {@code weblib.*} script functions registered
 * in the constructor, which write directly to the live {@link ModConfigSpec} values.
 * "Save" persists them; "Cancel" rolls back any in-flight changes.
 */
public class WebLibConfigScreen extends Screen {

    /* Snapshot of values at open time – used for Cancel rollback */
    private final int     snapshot_defaultFontSize;
    private final boolean snapshot_logCssWarnings;
    private final boolean snapshot_logScriptCalls;
    private final int     snapshot_maxHistory;
    private final double  snapshot_scrollSpeed;

    /* Live working copies (mutated by script callbacks before Save) */
    private int     draft_defaultFontSize;
    private boolean draft_logCssWarnings;
    private boolean draft_logScriptCalls;
    private int     draft_maxHistory;
    private double  draft_scrollSpeed;

    /* The parent screen to return to on close */
    private final Screen parent;

    /* web_lib rendering objects */
    private WebPage page;
    private final HtmlRenderer renderer = new HtmlRenderer();

    /* ─────────────── constructor ─────────────── */

    public WebLibConfigScreen(Screen parent) {
        super(Component.literal("web_lib Config"));
        this.parent = parent;

        // Snapshot current values
        this.snapshot_defaultFontSize = Config.DEFAULT_FONT_SIZE.getAsInt();
        this.snapshot_logCssWarnings  = Config.LOG_CSS_WARNINGS.getAsBoolean();
        this.snapshot_logScriptCalls  = Config.LOG_SCRIPT_CALLS.getAsBoolean();
        this.snapshot_maxHistory      = Config.MAX_HISTORY.getAsInt();
        this.snapshot_scrollSpeed = Config.SCROLL_SPEED.get();

        // Drafts start from snapshots
        this.draft_defaultFontSize = snapshot_defaultFontSize;
        this.draft_logCssWarnings  = snapshot_logCssWarnings;
        this.draft_logScriptCalls  = snapshot_logScriptCalls;
        this.draft_maxHistory      = snapshot_maxHistory;
        this.draft_scrollSpeed    = snapshot_scrollSpeed;
    }

    /* ─────────────── screen lifecycle ─────────────── */

    @Override
    protected void init() {
        buildPage();
    }

    @Override
    public void onClose() {
        // Escape key = cancel (don't save)
        rollback();
        Minecraft.getInstance().setScreen(parent);
    }

    /* ─────────────── page construction ─────────────── */

    private void buildPage() {
        String html = ConfigHtml.build(
                draft_defaultFontSize,
                draft_logCssWarnings,
                draft_logScriptCalls,
                draft_maxHistory,
                draft_scrollSpeed
        );

        page = new WebPage(html);
        page.setSystemEventHandler((event, data) -> { /* config screen handles navigation via script functions */ });

        registerScriptFunctions();
        page.layout(width, height);
    }

    private void buildCreditsPage() {
        String modVersion = net.neoforged.fml.ModList.get()
                .getModContainerById(WebLib.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
        String mcVersion = net.minecraft.SharedConstants.getCurrentVersion().getName();
        String html = ScreenHtmlLoader.load("assets/web_lib/screens/credits.html",
                java.util.Map.of("modVersion", modVersion, "mcVersion", mcVersion));
        page = new WebPage(html);
        page.setSystemEventHandler((event, data) -> { /* handled via weblib.backToConfig */ });
        page.layout(width, height);
    }

    /**
     * Register all {@code weblib.*} functions that HTML buttons/inputs call.
     * These run on the render thread so Minecraft.getInstance() is safe.
     */
    private void registerScriptFunctions() {
        ScriptBridge bridge = page.getScriptBridge();

        // ── Scroll Speed ──
        ScriptBridge.register("weblib.setScrollSpeed", (args, ctx) -> {
            if (args.isEmpty()) return null;
            try {
                double val = Double.parseDouble(args.get(0).toString().trim());
                if (val >= 1.0 && val <= 50.0) {
                    draft_scrollSpeed = val;
                    hideDomElement(ctx, "scrollSpeedError");
                } else {
                    showDomElement(ctx, "scrollSpeedError");
                }
            } catch (NumberFormatException ignored) {
                showDomElement(ctx, "scrollSpeedError");
            }
            return null;
        });

        // ── Font size ──
        bridge.addListener(null, "__init__", ""); // no-op
        ScriptBridge.register("weblib.setFontSize", (args, ctx) -> {
            if (args.isEmpty()) return null;
            try {
                int val = Integer.parseInt(args.get(0).toString().trim());
                if (val >= 6 && val <= 32) {
                    draft_defaultFontSize = val;
                    hideDomElement(ctx, "fontSizeError");
                } else {
                    showDomElement(ctx, "fontSizeError");
                }
            } catch (NumberFormatException ignored) {
                showDomElement(ctx, "fontSizeError");
            }
            return null;
        });

        // ── Log CSS warnings toggle ──
        ScriptBridge.register("weblib.toggleLogCssWarnings", (args, ctx) -> {
            draft_logCssWarnings = !draft_logCssWarnings;
            updateToggleButton(ctx, "logCssWarningsBtn", draft_logCssWarnings);
            return null;
        });

        // ── Log script calls toggle ──
        ScriptBridge.register("weblib.toggleLogScriptCalls", (args, ctx) -> {
            draft_logScriptCalls = !draft_logScriptCalls;
            updateToggleButton(ctx, "logScriptCallsBtn", draft_logScriptCalls);
            return null;
        });

        // ── Max history ──
        ScriptBridge.register("weblib.setMaxHistory", (args, ctx) -> {
            if (args.isEmpty()) return null;
            try {
                int val = Integer.parseInt(args.get(0).toString().trim());
                if (val >= 1 && val <= 100) {
                    draft_maxHistory = val;
                    hideDomElement(ctx, "maxHistoryError");
                } else {
                    showDomElement(ctx, "maxHistoryError");
                }
            } catch (NumberFormatException ignored) {
                showDomElement(ctx, "maxHistoryError");
            }
            return null;
        });

        // ── Save ──
        ScriptBridge.register("weblib.save", (args, ctx) -> {
            persist();
            Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(parent));
            return null;
        });

        // ── Cancel ──
        ScriptBridge.register("weblib.cancel", (args, ctx) -> {
            rollback();
            Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(parent));
            return null;
        });

        // ── Reset to Defaults ──
        ScriptBridge.register("weblib.resetDefaults", (args, ctx) -> {
            draft_defaultFontSize = 8;
            draft_logCssWarnings  = false;
            draft_logScriptCalls  = false;
            draft_maxHistory      = 20;
            draft_scrollSpeed     = 10.0;
            Minecraft.getInstance().tell(this::buildPage);
            return null;
        });

        // ── Open Credits ──
        ScriptBridge.register("weblib.openCredits", (args, ctx) -> {
            Minecraft.getInstance().tell(this::buildCreditsPage);
            return null;
        });

        // ── Back to Config (from Credits) ──
        ScriptBridge.register("weblib.backToConfig", (args, ctx) -> {
            Minecraft.getInstance().tell(this::buildPage);
            return null;
        });
    }

    /* ─────────────── config persistence ─────────────── */

    private void persist() {
        Config.DEFAULT_FONT_SIZE.set(draft_defaultFontSize);
        Config.LOG_CSS_WARNINGS.set(draft_logCssWarnings);
        Config.LOG_SCRIPT_CALLS.set(draft_logScriptCalls);
        Config.MAX_HISTORY.set(draft_maxHistory);
        Config.SCROLL_SPEED.set(draft_scrollSpeed);
        // Force write to disk
        Config.save();
        WebLib.LOGGER.info("[web_lib] Config saved: fontsize={}, logCss={}, logScript={}, history={}",
                draft_defaultFontSize, draft_logCssWarnings, draft_logScriptCalls, draft_maxHistory);
    }

    private void rollback() {
        Config.DEFAULT_FONT_SIZE.set(snapshot_defaultFontSize);
        Config.LOG_CSS_WARNINGS.set(snapshot_logCssWarnings);
        Config.LOG_SCRIPT_CALLS.set(snapshot_logScriptCalls);
        Config.MAX_HISTORY.set(snapshot_maxHistory);
        Config.SCROLL_SPEED.set(snapshot_scrollSpeed);
    }

    /* ─────────────── DOM helpers (called from script callbacks) ─────────────── */

    private void showDomElement(com.jammingdino.web_lib.script.ScriptContext ctx, String id) {
        if (ctx.getDocument() == null) return;
        com.jammingdino.web_lib.html.DomNode el = ctx.getDocument().getElementById(id);
        if (el != null) {
            el.setComputedStyle("display", "block");
            invalidate();
        }
    }

    private void hideDomElement(com.jammingdino.web_lib.script.ScriptContext ctx, String id) {
        if (ctx.getDocument() == null) return;
        com.jammingdino.web_lib.html.DomNode el = ctx.getDocument().getElementById(id);
        if (el != null) {
            el.setComputedStyle("display", "none");
            invalidate();
        }
    }

    private void updateToggleButton(com.jammingdino.web_lib.script.ScriptContext ctx, String id, boolean on) {
        if (ctx.getDocument() == null) return;
        com.jammingdino.web_lib.html.DomNode el = ctx.getDocument().getElementById(id);
        if (el != null) {
            // Update the CSS class set so :hover and .on styles apply correctly
            el.setAttribute("class", on ? "toggle-btn on" : "toggle-btn");
            // Update the text label
            el.getChildren().stream()
                    .filter(c -> c.isText())
                    .forEach(c -> c.setTextContent(on ? "ON" : "OFF"));
            invalidate();
        }
    }

    /** Re-layout after a DOM mutation so the renderer sees updated boxes. */
    private void invalidate() {
        if (page != null) page.invalidateLayout();
    }

    /* ─────────────── rendering ─────────────── */

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fill with a solid opaque colour - do NOT call renderBackground() as that
        // applies Minecraft's background blur which bleeds through the web page.
        graphics.fill(0, 0, width, height, 0xFF1a1a1a);

        if (page != null && page.getRootBox() != null) {
            renderer.render(graphics, page.getRootBox(), 0, 0, width, height);
        }

        // super.render draws widgets/tooltips on top but does not re-apply the blur
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /* ─────────────── input forwarding ─────────────── */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page != null) page.mouseClicked((int) mouseX, (int) mouseY, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (page != null) page.mouseReleased((int) mouseX, (int) mouseY, button);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (page != null) page.mouseMoved((int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page != null) return page.mouseScrolled((int) mouseX, (int) mouseY, scrollY);
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (page != null) return page.charTyped(codePoint);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page != null && page.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int newWidth, int newHeight) {
        super.resize(minecraft, newWidth, newHeight);
        if (page != null) page.layout(newWidth, newHeight);
    }

    /* ─────────────── misc ─────────────── */

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}