package com.jammingdino.web_lib.html;

import java.util.*;

/**
 * Converts a flat list of {@link HtmlToken}s into a {@link DomNode} tree.
 *
 * Follows a simplified version of the HTML5 tree-construction algorithm:
 *   - Maintains an open-element stack
 *   - Auto-closes mismatched tags (best-effort)
 *   - Ignores script/style content (stored as raw text children)
 *   - Merges consecutive text nodes
 */
public class HtmlParser {

    /** Elements whose raw text content should NOT be parsed as child tags. */
    private static final Set<String> RAW_TEXT_ELEMENTS = Set.of("script", "style", "textarea", "pre");

    private final List<HtmlToken> tokens;
    private int cursor;

    public HtmlParser(List<HtmlToken> tokens) {
        this.tokens = tokens;
        this.cursor  = 0;
    }

    /* ─────────────────── public entry point ─────────────────── */

    /**
     * Parse the token stream and return the synthetic document root.
     * Callers typically want {@code document.getChildren().get(0)} to get {@code <html>}.
     */
    public DomNode parse() {
        DomNode document = DomNode.document();
        Deque<DomNode> stack = new ArrayDeque<>();
        stack.push(document);

        while (cursor < tokens.size()) {
            HtmlToken token = tokens.get(cursor++);

            switch (token.getType()) {
                case DOCTYPE, COMMENT -> { /* discard */ }

                case OPEN_TAG -> {
                    DomNode node = DomNode.element(token.getTagName(), token.getAttributes());
                    // Apply inline style attribute immediately
                    node.applyInlineStyle(token.getAttribute("style"));

                    currentNode(stack).appendChild(node);

                    // For raw-text elements, consume everything until the matching close tag
                    if (RAW_TEXT_ELEMENTS.contains(token.getTagName())) {
                        String raw = consumeRawText(token.getTagName());
                        if (!raw.isEmpty()) node.appendChild(DomNode.text(raw));
                    } else {
                        stack.push(node);
                    }
                }

                case SELF_CLOSING_TAG -> {
                    DomNode node = DomNode.element(token.getTagName(), token.getAttributes());
                    node.applyInlineStyle(token.getAttribute("style"));
                    currentNode(stack).appendChild(node);
                    // Do NOT push onto the stack – no children expected
                }

                case CLOSE_TAG -> {
                    String closing = token.getTagName();
                    // Walk the stack to find the matching open element
                    Iterator<DomNode> it = stack.iterator();
                    boolean found = false;
                    while (it.hasNext()) {
                        DomNode n = it.next();
                        if (closing.equals(n.getTagName())) { found = true; break; }
                    }
                    if (found) {
                        // Pop until and including the matching element
                        while (!stack.isEmpty()) {
                            DomNode popped = stack.pop();
                            if (closing.equals(popped.getTagName())) break;
                        }
                    }
                    // If not found – ignore the stray close tag
                }

                case TEXT -> {
                    String text = token.getText();
                    // Skip pure-whitespace text between block elements at the top level
                    if (!text.isBlank() || couldHaveInlineContent(currentNode(stack))) {
                        // Try to merge with a preceding sibling text node
                        DomNode current = currentNode(stack);
                        List<DomNode> siblings = current.getChildren();
                        if (!siblings.isEmpty() && siblings.get(siblings.size() - 1).isText()) {
                            DomNode last = siblings.get(siblings.size() - 1);
                            last.setTextContent(last.getTextContent() + text);
                        } else {
                            current.appendChild(DomNode.text(text));
                        }
                    }
                }

                case EOF -> { return document; }
            }
        }

        return document;
    }

    /* ─────────────────── helpers ─────────────────── */

    private DomNode currentNode(Deque<DomNode> stack) {
        return stack.isEmpty() ? DomNode.document() : stack.peek();
    }

    /**
     * Consumes tokens until the matching close tag, returning the raw text seen.
     * Used for script/style/pre/textarea elements.
     */
    private String consumeRawText(String openTag) {
        StringBuilder sb = new StringBuilder();
        while (cursor < tokens.size()) {
            HtmlToken t = tokens.get(cursor);
            if (t.getType() == TokenType.CLOSE_TAG && openTag.equals(t.getTagName())) {
                cursor++; // consume the close tag
                break;
            }
            if (t.getType() == TokenType.TEXT) sb.append(t.getText());
            cursor++;
        }
        return sb.toString();
    }

    /** Returns true if the given node is an element that can contain inline (text) content. */
    private static boolean couldHaveInlineContent(DomNode node) {
        if (node == null || !node.isElement()) return false;
        return switch (node.getTagName()) {
            case "html","head","body","ul","ol","table","thead","tbody","tfoot","tr" -> false;
            default -> true;
        };
    }

    /* ─────────────────── convenience factory ─────────────────── */

    /** One-shot: lex + parse a raw HTML string and return the document root. */
    public static DomNode parseHtml(String html) {
        List<HtmlToken> tokens = new HtmlLexer(html).tokenize();
        return new HtmlParser(tokens).parse();
    }
}
