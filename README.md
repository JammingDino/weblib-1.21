# WebLib
___

#### `web_lib` lets you write Minecraft GUI screens using **HTML, CSS, and a lightweight JavaScript bridge** that calls back into your Java mod code.

---

## Architecture

```
Your HTML string
      │
      ▼
┌─────────────┐    ┌──────────────┐    ┌───────────────┐
│  HtmlLexer  │───▶│  HtmlParser  │───▶│    DomNode    │  (DOM tree)
└─────────────┘    └──────────────┘    └───────────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │      CssEngine       │  (cascade + inheritance)
                                    │  CssParser           │
                                    │  CssMatcher          │
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │    LayoutEngine      │  (block / inline / flex)
                                    └──────────┬──────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │    HtmlRenderer      │  (GuiGraphics)
                                    └─────────────────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │    ScriptBridge      │  (Java callbacks from HTML)
                                    └─────────────────────┘
```

---

## Quick Start

### 1. Add web_lib as a dependency

In your `neoforge.mods.toml`:
```toml
[[dependencies.yourmod]]
    modId = "web_lib"
    type = "required"
    versionRange = "[1.0,)"
    ordering = "AFTER"
    side = "CLIENT"
```

### 2. Open a page

```java
// Anywhere on the client thread:
WebLibApi.openPage("""
    <html>
    <head>
      <style>
        body { background: #1a1a2e; color: #eee; font-size: 10px; padding: 16px; }
        h1   { color: #e94560; }
        .btn { background: #16213e; border: 1px solid #e94560;
               padding: 6px 12px; cursor: pointer; }
        .btn:hover { background: #e94560; }
      </style>
    </head>
    <body>
      <h1>My Mod Menu</h1>
      <p>Choose an action:</p>
      <button class="btn" onclick="myMod.openShop()">Open Shop</button>
      <button class="btn" onclick="myMod.openSettings()">Settings</button>
    </body>
    </html>
""");
```

### 3. Register Java callbacks

```java
// In your mod's client setup or init:
WebLibApi.registerFunction("myMod.openShop", (args, ctx) -> {
    Minecraft.getInstance().tell(() ->
        Minecraft.getInstance().setScreen(new MyShopScreen()));
    return null;
});

WebLibApi.registerFunction("myMod.setVolume", (args, ctx) -> {
    if (!args.isEmpty()) {
        float vol = Float.parseFloat(args.get(0).toString());
        MyMod.MUSIC_VOLUME = vol;
    }
    return null;
});
```

---

## Supported HTML Elements

| Element | Notes |
|---------|-------|
| `div`, `section`, `article`, `header`, `footer`, `main`, `nav`, `aside` | Block containers |
| `p`, `h1`–`h6`, `pre`, `hr` | Text elements |
| `span`, `a`, `strong`, `b`, `em`, `i`, `s`, `u`, `code` | Inline elements |
| `ul`, `ol`, `li` | Lists (bullets rendered) |
| `img` | `src` must be a `namespace:path` ResourceLocation |
| `button` | Clickable, hover/active states |
| `input` | `type`: text, password, number, checkbox, radio |
| `select` | Dropdown (display only; value via `selectedValue` data) |
| `script` | Inline JS-like expressions processed |
| `style` | CSS parsed and applied |

---

## Supported CSS

### Selectors
- Type (`div`), Class (`.foo`), ID (`#bar`), Universal (`*`)
- Attribute (`[type="text"]`, `[class~=foo]`, `[src^=https]`)
- Pseudo-class: `:hover`, `:active`, `:focus`, `:first-child`, `:last-child`, `:nth-child(n)`, `:not(sel)`, `:checked`, `:disabled`
- Combinators: descendant (` `), child (`>`), adjacent (`+`), sibling (`~`)
- Compound: `.a.b`, `div.foo#bar`

### Properties
- **Box model**: `margin`, `padding`, `border`, `width`, `height` (px, %, em, rem, vw, vh)
- **Display**: `block`, `inline`, `inline-block`, `flex`, `none`
- **Flex**: `flex-direction`, `justify-content`, `align-items`, `flex-wrap`, `gap`
- **Colors**: named, `#RGB`, `#RRGGBB`, `#RRGGBBAA`, `rgb()`, `rgba()`
- **Text**: `color`, `font-size`, `font-weight`, `font-style`, `text-align`, `text-decoration`, `line-height`
- **Background**: `background-color`, `background-image: url(namespace:path)`
- **Overflow**: `hidden`, `scroll`, `auto`
- **Position**: `relative` (with `top`/`left`)
- **Z-index**

---

## JavaScript Bridge

### Built-in functions (no registration needed)

```html
<button onclick="alert('Hello!')">Alert</button>
<button onclick="navigate('/page2')">Go to Page 2</button>
<button onclick="reload()">Reload</button>
<button onclick="console.log('debug')">Log</button>
<button onclick="setStyle('myDiv', 'color', 'red')">Red Text</button>
<button onclick="setInnerText('counter', '42')">Set Text</button>
<button onclick="toggleClass('panel', 'hidden')">Toggle</button>
<input onchange="setVar('volume', this.value)">
```

### Registering mod functions

```java
// Simple action
WebLibApi.registerFunction("myMod.heal", (args, ctx) -> {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) mc.player.heal(20f);
    return null;
});

// With arguments from HTML
WebLibApi.registerFunction("myMod.giveItem", (args, ctx) -> {
    String itemId = args.size() > 0 ? args.get(0).toString() : "minecraft:stone";
    int count     = args.size() > 1 ? ((Number)args.get(1)).intValue() : 1;
    // ... give item logic
    return null;
});

// Accessing the DOM from Java
WebLibApi.registerFunction("myMod.updateScore", (args, ctx) -> {
    DomNode scoreEl = ctx.getDocument().getElementById("score");
    if (scoreEl != null) {
        scoreEl.getChildren().stream()
            .filter(DomNode::isText)
            .forEach(t -> t.setTextContent(String.valueOf(MyMod.score)));
    }
    return null;
});
```

### In HTML
```html
<button onclick="myMod.heal()">Heal Player</button>
<button onclick="myMod.giveItem('minecraft:diamond', 64)">Get Diamonds</button>
<span id="score">0</span>
<button onclick="myMod.updateScore()">Refresh Score</button>
```

---

## Navigation (multi-page)

```java
WebLibApi.openPage(homeHtml, url -> switch (url) {
    case "/home"     -> homeHtml;
    case "/shop"     -> shopHtml;
    case "/settings" -> settingsHtml;
    default          -> null; // cancel navigation
});
```

The browser chrome (back ◀ / forward ▶ / reload ↺) is shown by default.  
Use `WebLibApi.openPageHeadless(html)` to hide it.

---

## WebPage API

For advanced use, build and control pages directly:

```java
WebPage page = WebLibApi.buildPage(html);
page.injectCss(".theme-dark { background: #111; color: #eee; }");
page.layout(screenWidth, screenHeight);

// Later, after DOM mutation:
page.invalidateLayout();

// Open the pre-built page:
WebLibApi.openPage(page, myLoader);
```

---

## File layout

```
com.jammingdino.web_lib/
├── html/
│   ├── HtmlLexer.java        – tokenizer
│   ├── HtmlParser.java       – DOM builder
│   ├── HtmlToken.java        – token model
│   ├── TokenType.java        – token type enum
│   └── DomNode.java          – DOM node
├── css/
│   ├── CssParser.java        – rule parser
│   ├── CssRule.java          – rule model
│   ├── CssEngine.java        – cascade + color/length utils
│   ├── CssMatcher.java       – selector matching
│   └── CssSpecificity.java   – specificity calculator
├── layout/
│   ├── LayoutEngine.java     – box model + flex layout
│   ├── LayoutBox.java        – positioned box
│   └── DisplayType.java      – display enum
├── render/
│   └── HtmlRenderer.java     – GuiGraphics renderer
├── script/
│   ├── ScriptBridge.java     – JS function registry + evaluator
│   └── ScriptContext.java    – per-call context
├── screen/
│   ├── WebPage.java          – parsed page + interaction
│   └── WebScreen.java        – Minecraft Screen wrapper
├── api/
│   └── WebLibApi.java        – public API for other mods
├── WebLib.java               – mod entrypoint
└── Config.java               – mod config
```
