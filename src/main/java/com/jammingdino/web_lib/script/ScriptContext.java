package com.jammingdino.web_lib.script;

import com.jammingdino.web_lib.html.DomNode;

/**
 * Execution context passed to every {@link ScriptBridge.ScriptFunction}.
 * Holds the current event target, event data, and a reference to the bridge
 * so functions can read/write page-scope variables.
 */
public class ScriptContext {

    private final ScriptBridge bridge;
    private DomNode currentTarget;
    private String  eventData = "";

    ScriptContext(ScriptBridge bridge) {
        this.bridge = bridge;
    }

    /* ── accessors ── */

    public DomNode getDocument()                    { return bridge.getDocument(); }
    public DomNode getCurrentTarget()               { return currentTarget; }
    public void    setCurrentTarget(DomNode node)   { this.currentTarget = node; }
    public String  getEventData()                   { return eventData; }
    public void    setEventData(String data)        { this.eventData = data == null ? "" : data; }

    /* ── variable scope ── */

    public void   setVar(String name, Object value) { bridge.setVar(name, value); }
    public Object getVar(String name)               { return bridge.getVar(name); }

    /* ── system events ── */

    public void fireEvent(String type, String data) { bridge.fireEvent(type, data); }
}