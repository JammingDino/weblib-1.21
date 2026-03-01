package com.jammingdino.web_lib.script;

import com.jammingdino.web_lib.api.ResourceLoader;
import com.jammingdino.web_lib.html.DomNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal JavaScript-like scripting bridge.
 *
 * Instead of embedding a full JS engine, this bridge provides:
 *
 *  1. Event listener registration (onclick, onchange, …) parsed from HTML attributes
 *  2. A function registry where Java code registers named callbacks
 *  3. A simple expression evaluator for inline attribute values like:
 *       onclick="myMod.openInventory()"
 *       onclick="alert('Hello!')"
 *       onchange="setVolume(this.value)"
 *  4. A variable scope (key → Object) shared across all scripts in one page
 *
 * Custom syntax for calling back into Java mods:
 *   - Register a Java function: ScriptBridge.registerFunction("myMod.doThing", (args, ctx) -> { ... })
 *   - In HTML: onclick="myMod.doThing('arg1', 42)"
 *
 * Built-in functions:
 *   alert(msg)          – logs to chat/console
 *   console.log(msg)    – logs to mod logger
 *   navigate(url)       – fires a NavigationEvent
 *   reload()            – reloads the current page
 *   setVar(name, val)   – sets a page-scope variable
 *   getVar(name)        – retrieves a page-scope variable
 */
public class ScriptBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("web_lib/script");

    /* ─────────────────── static function registry (shared across pages) ─── */

    /** Registered Java-backed functions. Key = function name (may contain dots). */
    private static final Map<String, ScriptFunction> REGISTRY = new LinkedHashMap<>();

    static {
        // Built-ins
        register("console.log", (args, ctx) -> {
            LOGGER.info("[web_lib/console] {}", String.join(" ", args.stream().map(Object::toString).toList()));
            return null;
        });
        register("alert", (args, ctx) -> {
            String msg = args.isEmpty() ? "" : args.get(0).toString();
            LOGGER.info("[web_lib/alert] {}", msg);
            // Chat message would require accessing player – done in WebScreen layer
            ctx.fireEvent("alert", msg);
            return null;
        });
        register("navigate", (args, ctx) -> {
            if (!args.isEmpty()) ctx.fireEvent("navigate", args.get(0).toString());
            return null;
        });
        register("reload", (args, ctx) -> {
            ctx.fireEvent("reload", "");
            return null;
        });
        register("setVar", (args, ctx) -> {
            if (args.size() >= 2) ctx.setVar(args.get(0).toString(), args.get(1));
            return null;
        });
        register("getVar", (args, ctx) -> args.isEmpty() ? null : ctx.getVar(args.get(0).toString()));
        register("getElementById", (args, ctx) -> {
            if (args.isEmpty() || ctx.getDocument() == null) return null;
            return ctx.getDocument().getElementById(args.get(0).toString());
        });
        register("setAttribute", (args, ctx) -> {
            if (args.size() >= 3 && ctx.getDocument() != null) {
                DomNode el = ctx.getDocument().getElementById(args.get(0).toString());
                if (el != null) el.setAttribute(args.get(1).toString(), args.get(2).toString());
            }
            return null;
        });
        register("setStyle", (args, ctx) -> {
            // setStyle('id', 'color', 'red')
            if (args.size() >= 3 && ctx.getDocument() != null) {
                DomNode el = ctx.getDocument().getElementById(args.get(0).toString());
                if (el != null) el.setComputedStyle(args.get(1).toString(), args.get(2).toString());
            }
            return null;
        });
        register("setInnerText", (args, ctx) -> {
            // setInnerText('id', 'new text')
            if (args.size() >= 2 && ctx.getDocument() != null) {
                DomNode el = ctx.getDocument().getElementById(args.get(0).toString());
                if (el != null) {
                    el.getChildren().forEach(c -> { if (c.isText()) c.setTextContent(args.get(1).toString()); });
                }
            }
            return null;
        });
        register("toggleClass", (args, ctx) -> {
            // toggleClass('id', 'className')
            if (args.size() >= 2 && ctx.getDocument() != null) {
                DomNode el = ctx.getDocument().getElementById(args.get(0).toString());
                if (el != null) {
                    String cls     = args.get(1).toString();
                    String current = el.getAttribute("class") == null ? "" : el.getAttribute("class");
                    Set<String> classes = new LinkedHashSet<>(Arrays.asList(current.trim().split("\\s+")));
                    if (classes.contains(cls)) classes.remove(cls); else classes.add(cls);
                    el.setAttribute("class", String.join(" ", classes));
                }
            }
            return null;
        });
    }

    /**
     * Register a Java-backed function accessible from HTML event attributes.
     *
     * @param name     Function name as used in HTML (e.g. "myMod.openGui")
     * @param function The Java handler
     */
    public static void register(String name, ScriptFunction function) {
        REGISTRY.put(name, function);
    }

    /* ─────────────────── per-page instance ─────────────────── */

    private final Map<String, Object> variables = new LinkedHashMap<>();
    private DomNode document;

    /** Listeners registered with addEventListener equivalent */
    private final Map<DomNode, Map<String, List<String>>> listeners = new IdentityHashMap<>();

    /** System-level event listener (alert, navigate, reload) */
    private BiConsumer<String, String> systemEventHandler;

    public ScriptBridge() {}

    public void setDocument(DomNode doc) { this.document = doc; }
    public DomNode getDocument()         { return document; }

    public void setSystemEventHandler(BiConsumer<String, String> handler) {
        this.systemEventHandler = handler;
    }

    /* ─────────────────── HTML attribute event scanning ─────────────────── */

    /**
     * Walk the DOM and register event handlers found in attributes like
     * onclick="...", onchange="...", onmouseover="..." etc.
     */
    public void scanDocument(DomNode root) {
        if (root == null) return;
        scanNode(root);
    }

    private void scanNode(DomNode node) {
        if (node.isElement()) {
            for (String attr : node.getAttributes().keySet()) {
                if (attr.startsWith("on")) {
                    String eventName = attr.substring(2); // "click", "change", …
                    String expression = node.getAttribute(attr);
                    addListener(node, eventName, expression);
                }
            }
        }
        for (DomNode child : node.getChildren()) scanNode(child);
    }

    /* ─────────────────── style block processing ─────────────────── */

    /**
     * Find {@code <script>} tags and process their content.
     * Currently extracts only top-level function definitions of the pattern:
     * {@code function name(...) { body }}
     * and registers them as interpreted functions.
     *
     * For anything more complex, mods should register Java functions directly.
     */
    public void processScriptTags(DomNode root) {
        processScriptTags(root, null);
    }

    /**
     * Find {@code <script>} tags and process their content.
     * Inline scripts are executed immediately; external scripts (with a {@code src}
     * attribute) are loaded via the supplied {@link ResourceLoader} when non-null.
     *
     * @param root           The root DOM node to search.
     * @param resourceLoader Loader used to fetch external script files, or {@code null}
     *                       to skip loading external scripts.
     */
    public void processScriptTags(DomNode root, ResourceLoader resourceLoader) {
        if (root == null) return;
        for (DomNode script : root.getElementsByTagName("script")) {
            String type = script.getAttribute("type");
            if (type == null || type.equals("text/javascript") || type.equals("module")) {
                String src = script.getAttribute("src");
                if (src == null) {
                    // Inline script – get the text child
                    for (DomNode child : script.getChildren()) {
                        if (child.isText()) executeScript(child.getTextContent(), this::makeContext);
                    }
                } else if (resourceLoader != null) {
                    // External script – load via the resource loader
                    String content = resourceLoader.load(src);
                    if (content != null) {
                        executeScript(content, this::makeContext);
                    } else {
                        LOGGER.warn("[web_lib/script] Could not load external script: {}", src);
                    }
                }
            }
        }
    }

    /* ─────────────────── event dispatch ─────────────────── */

    /**
     * Fire all registered listeners for a given event on a node.
     *
     * @param node      The DOM element that received the event
     * @param eventName e.g. "click", "change", "mouseover"
     * @param eventData Optional extra data (e.g. input value for "change")
     */
    public void dispatchEvent(DomNode node, String eventName, String eventData) {
        // 1. Node-level listeners registered via scanDocument
        Map<String, List<String>> nodeListeners = listeners.get(node);
        if (nodeListeners != null) {
            List<String> exprs = nodeListeners.getOrDefault(eventName, List.of());
            ScriptContext ctx = makeContext();
            ctx.setCurrentTarget(node);
            ctx.setEventData(eventData);
            for (String expr : exprs) evaluate(expr, ctx);
        }

        // 2. Bubble up to parent (simplified – only one level for now)
        DomNode parent = node.getParent();
        if (parent != null) {
            Map<String, List<String>> parentListeners = listeners.get(parent);
            if (parentListeners != null) {
                List<String> exprs = parentListeners.getOrDefault(eventName, List.of());
                if (!exprs.isEmpty()) {
                    ScriptContext ctx = makeContext();
                    ctx.setCurrentTarget(node);
                    ctx.setEventData(eventData);
                    for (String expr : exprs) evaluate(expr, ctx);
                }
            }
        }
    }

    public void addListener(DomNode node, String eventName, String expression) {
        listeners.computeIfAbsent(node, k -> new LinkedHashMap<>())
                 .computeIfAbsent(eventName, k -> new ArrayList<>())
                 .add(expression);
    }

    /* ─────────────────── expression evaluation ─────────────────── */

    /**
     * Execute a simple script expression.
     * Handles:
     *   - Single function calls:  foo.bar('arg1', 42, true)
     *   - Chained semicolons:     foo(); bar()
     *   - this.value reference:   replaced with eventData
     */
    public Object evaluate(String expression, ScriptContext ctx) {
        if (expression == null || expression.isBlank()) return null;
        // Handle multiple statements
        Object last = null;
        for (String stmt : expression.split(";")) {
            stmt = stmt.trim();
            if (!stmt.isEmpty()) last = evaluateStatement(stmt, ctx);
        }
        return last;
    }

    private Object evaluateStatement(String stmt, ScriptContext ctx) {
        // Replace this.value with the event data value
        stmt = stmt.replace("this.value", ctx.getEventData() == null ? "''" : "'" + ctx.getEventData() + "'");
        stmt = stmt.replace("event.value", stmt); // no-op fallback

        // Match: functionName(args)
        Matcher m = CALL_PATTERN.matcher(stmt.trim());
        if (m.matches()) {
            String fnName = m.group(1);
            String argStr = m.group(2);
            List<Object> args = parseArgList(argStr, ctx);
            return callFunction(fnName, args, ctx);
        }
        LOGGER.debug("[web_lib/script] Could not evaluate: {}", stmt);
        return null;
    }

    private static final Pattern CALL_PATTERN =
            Pattern.compile("([\\w.]+)\\s*\\((.*)\\)\\s*", Pattern.DOTALL);

    private List<Object> parseArgList(String argStr, ScriptContext ctx) {
        List<Object> args = new ArrayList<>();
        if (argStr == null || argStr.isBlank()) return args;
        // Tokenize on commas, respecting strings
        List<String> tokens = splitArgTokens(argStr);
        for (String token : tokens) {
            args.add(coerceValue(token.trim(), ctx));
        }
        return args;
    }

    private Object coerceValue(String token, ScriptContext ctx) {
        if (token.startsWith("'") && token.endsWith("'"))   return token.substring(1, token.length() - 1);
        if (token.startsWith("\"") && token.endsWith("\"")) return token.substring(1, token.length() - 1);
        if (token.equalsIgnoreCase("true"))  return Boolean.TRUE;
        if (token.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (token.equalsIgnoreCase("null"))  return null;
        try { return Integer.parseInt(token); } catch (NumberFormatException e) {}
        try { return Double.parseDouble(token); } catch (NumberFormatException e) {}
        // Could be a variable reference
        Object var = ctx.getVar(token);
        if (var != null) return var;
        return token; // treat as raw string
    }

    private List<String> splitArgTokens(String s) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0; char inStr = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr != 0) {
                cur.append(c);
                if (c == inStr) inStr = 0;
            } else if (c == '\'' || c == '"') {
                cur.append(c); inStr = c;
            } else if (c == '(' || c == '[') {
                cur.append(c); depth++;
            } else if (c == ')' || c == ']') {
                cur.append(c); depth--;
            } else if (c == ',' && depth == 0) {
                tokens.add(cur.toString()); cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) tokens.add(cur.toString());
        return tokens;
    }

    private Object callFunction(String name, List<Object> args, ScriptContext ctx) {
        ScriptFunction fn = REGISTRY.get(name);
        if (fn != null) {
            try { return fn.call(args, ctx); }
            catch (Exception e) { LOGGER.warn("[web_lib/script] Error in '{}': {}", name, e.getMessage()); }
        } else {
            LOGGER.debug("[web_lib/script] Unknown function: {}", name);
        }
        return null;
    }

    /* ─────────────────── inline script execution ─────────────────── */

    @FunctionalInterface
    interface ContextSupplier { ScriptContext get(); }

    private void executeScript(String script, ContextSupplier ctxSupplier) {
        if (script == null || script.isBlank()) return;
        ScriptContext ctx = ctxSupplier.get();
        // Very basic: execute each semicolon-delimited statement
        for (String stmt : script.split(";")) {
            stmt = stmt.trim();
            if (!stmt.isEmpty() && !stmt.startsWith("//") && !stmt.startsWith("function")) {
                evaluateStatement(stmt, ctx);
            }
        }
    }

    /* ─────────────────── context factory & variable scope ─────────────────── */

    private ScriptContext makeContext() {
        return new ScriptContext(this);
    }

    public void setVar(String name, Object value) { variables.put(name, value); }
    public Object getVar(String name)              { return variables.get(name); }

    public void fireEvent(String eventType, String data) {
        if (systemEventHandler != null) systemEventHandler.accept(eventType, data);
    }

    /* ─────────────────── functional interface ─────────────────── */

    @FunctionalInterface
    public interface ScriptFunction {
        Object call(List<Object> args, ScriptContext ctx);
    }
}
