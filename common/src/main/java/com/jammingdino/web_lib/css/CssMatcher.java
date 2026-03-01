package com.jammingdino.web_lib.css;

import com.jammingdino.web_lib.html.DomNode;

import java.util.*;

/**
 * Determines whether a given selector string matches a {@link DomNode}.
 *
 * Supported selectors:
 *   - Type          p, div, span
 *   - Universal     *
 *   - Class         .foo
 *   - ID            #bar
 *   - Attribute     [href]  [type="text"]  [class~="foo"]  [src^="https"]
 *   - Pseudo-class  :first-child  :last-child  :nth-child(n)  :not(sel)
 *                   :hover  :active  :focus  :checked  :disabled  :enabled
 *                   (state-based pseudo-classes always return false unless
 *                    the node has the corresponding data flag set)
 *   - Combinators   " " (descendant)  ">" (child)  "+" (adjacent)  "~" (sibling)
 *   - Grouped       .a.b  (compound – all must match)
 */
public class CssMatcher {

    private CssMatcher() {}

    /* ─────────────────── public entry point ─────────────────── */

    public static boolean matches(DomNode node, String selector) {
        if (node == null || selector == null || selector.isBlank()) return false;
        return matchComplexSelector(node, selector.trim());
    }

    /* ─────────────────── complex selector (with combinators) ─── */

    /**
     * Splits the selector on combinators and checks the full ancestry chain.
     * Works right-to-left: the rightmost part must match the node, then each
     * combinator-linked part must match some ancestor.
     */
    private static boolean matchComplexSelector(DomNode node, String selector) {
        // Tokenize into (combinator, simple-selector) pairs
        List<SelectorSegment> segments = parseSegments(selector);
        if (segments.isEmpty()) return false;

        // The last segment must match the node itself
        SelectorSegment last = segments.get(segments.size() - 1);
        if (!matchSimple(node, last.simple)) return false;
        if (segments.size() == 1) return true;

        // Walk up the chain
        return matchAncestors(node, segments, segments.size() - 2);
    }

    private static boolean matchAncestors(DomNode node, List<SelectorSegment> segs, int idx) {
        if (idx < 0) return true; // all segments matched
        SelectorSegment seg = segs.get(idx);
        switch (seg.combinator) {
            case DESCENDANT -> {
                DomNode anc = node.getParent();
                while (anc != null) {
                    if (matchSimple(anc, seg.simple) && matchAncestors(anc, segs, idx - 1)) return true;
                    anc = anc.getParent();
                }
                return false;
            }
            case CHILD -> {
                DomNode parent = node.getParent();
                return parent != null && matchSimple(parent, seg.simple) && matchAncestors(parent, segs, idx - 1);
            }
            case ADJACENT -> {
                DomNode prev = previousElementSibling(node);
                return prev != null && matchSimple(prev, seg.simple) && matchAncestors(prev, segs, idx - 1);
            }
            case SIBLING -> {
                DomNode prev = previousElementSibling(node);
                while (prev != null) {
                    if (matchSimple(prev, seg.simple) && matchAncestors(prev, segs, idx - 1)) return true;
                    prev = previousElementSibling(prev);
                }
                return false;
            }
        }
        return false;
    }

    /* ─────────────────── simple (compound) selector matching ─── */

    /** Matches a compound selector like  div.foo#bar[attr]:pseudo  against a node. */
    private static boolean matchSimple(DomNode node, String simple) {
        if (node == null || !node.isElement()) return false;
        if (simple.equals("*")) return true;

        // Parse the compound selector token by token
        int i = 0;
        while (i < simple.length()) {
            char c = simple.charAt(i);

            if (c == '#') {
                // ID selector
                int end = nextSpecialChar(simple, i + 1);
                String id = simple.substring(i + 1, end);
                if (!id.equals(node.getId())) return false;
                i = end;

            } else if (c == '.') {
                // Class selector
                int end = nextSpecialChar(simple, i + 1);
                String cls = simple.substring(i + 1, end);
                if (!node.getClasses().contains(cls)) return false;
                i = end;

            } else if (c == '[') {
                // Attribute selector
                int close = simple.indexOf(']', i);
                String attrExpr = simple.substring(i + 1, close);
                if (!matchAttribute(node, attrExpr)) return false;
                i = close + 1;

            } else if (c == ':') {
                // Pseudo-class (or ::pseudo-element)
                int start = i + (i + 1 < simple.length() && simple.charAt(i + 1) == ':' ? 2 : 1);
                // Check for parens
                int parenOpen = simple.indexOf('(', start);
                int end;
                String pseudo;
                String arg = null;
                if (parenOpen > 0 && parenOpen < simple.length()) {
                    int parenClose = simple.indexOf(')', parenOpen);
                    end = parenClose + 1;
                    pseudo = simple.substring(start, parenOpen).toLowerCase();
                    arg = simple.substring(parenOpen + 1, parenClose);
                } else {
                    end = nextSpecialChar(simple, start);
                    pseudo = simple.substring(start, end).toLowerCase();
                }
                if (!matchPseudo(node, pseudo, arg)) return false;
                i = end;

            } else if (c == '*') {
                i++;

            } else {
                // Type selector
                int end = nextSpecialChar(simple, i);
                String tag = simple.substring(i, end).toLowerCase();
                if (!tag.equals(node.getTagName())) return false;
                i = end;
            }
        }
        return true;
    }

    /* ─────────────────── attribute matching ─────────────────── */

    private static boolean matchAttribute(DomNode node, String expr) {
        // [attr]  [attr=val]  [attr~=val]  [attr^=val]  [attr$=val]  [attr*=val]
        if (expr.contains("~=")) {
            String[] p = expr.split("~=", 2);
            String val = node.getAttribute(p[0].trim());
            return val != null && Arrays.asList(val.split("\\s+")).contains(stripQuotes(p[1].trim()));
        } else if (expr.contains("^=")) {
            String[] p = expr.split("\\^=", 2);
            String val = node.getAttribute(p[0].trim());
            return val != null && val.startsWith(stripQuotes(p[1].trim()));
        } else if (expr.contains("$=")) {
            String[] p = expr.split("\\$=", 2);
            String val = node.getAttribute(p[0].trim());
            return val != null && val.endsWith(stripQuotes(p[1].trim()));
        } else if (expr.contains("*=")) {
            String[] p = expr.split("\\*=", 2);
            String val = node.getAttribute(p[0].trim());
            return val != null && val.contains(stripQuotes(p[1].trim()));
        } else if (expr.contains("=")) {
            String[] p = expr.split("=", 2);
            String val = node.getAttribute(p[0].trim());
            return val != null && val.equals(stripQuotes(p[1].trim()));
        } else {
            // Presence only
            return node.hasAttribute(expr.trim());
        }
    }

    private static String stripQuotes(String s) {
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /* ─────────────────── pseudo-class matching ─────────────────── */

    private static boolean matchPseudo(DomNode node, String pseudo, String arg) {
        return switch (pseudo) {
            case "first-child"  -> isNthChild(node, 1);
            case "last-child"   -> isNthLastChild(node, 1);
            case "nth-child"    -> arg != null && matchNth(node, arg, false);
            case "nth-last-child" -> arg != null && matchNth(node, arg, true);
            case "only-child"   -> isOnlyChild(node);
            case "first-of-type"-> isNthOfType(node, 1);
            case "last-of-type" -> isNthLastOfType(node, 1);
            case "empty"        -> node.getChildren().isEmpty();
            case "not"          -> arg != null && !matchSimple(node, arg);
            // State-based – rely on node dataset flags set by the screen/event system
            case "hover"    -> Boolean.TRUE.equals(node.getData("hover"));
            case "active"   -> Boolean.TRUE.equals(node.getData("active"));
            case "focus"    -> Boolean.TRUE.equals(node.getData("focus"));
            case "checked"  -> Boolean.TRUE.equals(node.getData("checked")) || "checked".equals(node.getAttribute("checked"));
            case "disabled" -> node.hasAttribute("disabled") || Boolean.TRUE.equals(node.getData("disabled"));
            case "enabled"  -> !node.hasAttribute("disabled") && !Boolean.TRUE.equals(node.getData("disabled"));
            // Pseudo-elements – no special logic at match time
            case "before", "after", "first-line", "first-letter", "placeholder" -> false;
            default -> false;
        };
    }

    private static boolean isNthChild(DomNode node, int n) {
        return childIndex(node) == n;
    }

    private static boolean isNthLastChild(DomNode node, int n) {
        return childIndexFromEnd(node) == n;
    }

    private static boolean isOnlyChild(DomNode node) {
        DomNode parent = node.getParent();
        if (parent == null) return false;
        long count = parent.getChildren().stream().filter(DomNode::isElement).count();
        return count == 1;
    }

    private static boolean isNthOfType(DomNode node, int n) {
        return typeIndex(node) == n;
    }

    private static boolean isNthLastOfType(DomNode node, int n) {
        return typeIndexFromEnd(node) == n;
    }

    /** Handles an+b notation including 'even', 'odd'. */
    private static boolean matchNth(DomNode node, String expr, boolean fromEnd) {
        int idx = fromEnd ? childIndexFromEnd(node) : childIndex(node);
        expr = expr.trim().toLowerCase();
        if (expr.equals("odd"))  return idx % 2 == 1;
        if (expr.equals("even")) return idx > 0 && idx % 2 == 0;
        // an+b
        int n, b;
        if (!expr.contains("n")) {
            try { return idx == Integer.parseInt(expr); } catch (NumberFormatException e) { return false; }
        }
        String[] parts = expr.split("n", 2);
        n = parts[0].isBlank() ? 1 : parts[0].equals("-") ? -1 : Integer.parseInt(parts[0]);
        b = parts.length > 1 && !parts[1].isBlank() ? Integer.parseInt(parts[1]) : 0;
        if (n == 0) return idx == b;
        int rem = idx - b;
        return rem >= 0 && rem % n == 0;
    }

    private static int childIndex(DomNode node) {
        DomNode parent = node.getParent();
        if (parent == null) return 1;
        int idx = 0;
        for (DomNode c : parent.getChildren()) {
            if (c.isElement()) idx++;
            if (c == node) return idx;
        }
        return 1;
    }

    private static int childIndexFromEnd(DomNode node) {
        DomNode parent = node.getParent();
        if (parent == null) return 1;
        List<DomNode> children = parent.getChildren();
        int idx = 0;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).isElement()) idx++;
            if (children.get(i) == node) return idx;
        }
        return 1;
    }

    private static int typeIndex(DomNode node) {
        DomNode parent = node.getParent();
        if (parent == null) return 1;
        int idx = 0;
        for (DomNode c : parent.getChildren()) {
            if (c.isElement() && c.getTagName().equals(node.getTagName())) idx++;
            if (c == node) return idx;
        }
        return 1;
    }

    private static int typeIndexFromEnd(DomNode node) {
        DomNode parent = node.getParent();
        if (parent == null) return 1;
        List<DomNode> children = parent.getChildren();
        int idx = 0;
        for (int i = children.size() - 1; i >= 0; i--) {
            DomNode c = children.get(i);
            if (c.isElement() && c.getTagName().equals(node.getTagName())) idx++;
            if (c == node) return idx;
        }
        return 1;
    }

    /* ─────────────────── segment parsing ─────────────────── */

    private enum Combinator { DESCENDANT, CHILD, ADJACENT, SIBLING }

    private record SelectorSegment(Combinator combinator, String simple) {}

    /** Parses "div > .foo + span" into ordered segments. */
    private static List<SelectorSegment> parseSegments(String selector) {
        List<SelectorSegment> segments = new ArrayList<>();
        // Split on combinators, keeping the combinator character
        // Strategy: walk char by char
        StringBuilder current = new StringBuilder();
        Combinator pendingCombinator = Combinator.DESCENDANT;
        int depth = 0;

        for (int i = 0; i < selector.length(); i++) {
            char c = selector.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '(' ) depth++;
            else if (c == ')') depth--;

            if (depth == 0) {
                if (c == '>') {
                    if (current.length() > 0) {
                        segments.add(new SelectorSegment(pendingCombinator, current.toString().trim()));
                        current.setLength(0);
                    }
                    pendingCombinator = Combinator.CHILD;
                    continue;
                } else if (c == '+') {
                    if (current.length() > 0) {
                        segments.add(new SelectorSegment(pendingCombinator, current.toString().trim()));
                        current.setLength(0);
                    }
                    pendingCombinator = Combinator.ADJACENT;
                    continue;
                } else if (c == '~') {
                    if (current.length() > 0) {
                        segments.add(new SelectorSegment(pendingCombinator, current.toString().trim()));
                        current.setLength(0);
                    }
                    pendingCombinator = Combinator.SIBLING;
                    continue;
                } else if (c == ' ' && !current.isEmpty()) {
                    // Could be a descendant combinator or just whitespace between parts
                    // Peek ahead – if next non-ws char is not another combinator, it's a combinator
                    int j = i + 1;
                    while (j < selector.length() && selector.charAt(j) == ' ') j++;
                    if (j < selector.length() && selector.charAt(j) != '>' && selector.charAt(j) != '+' && selector.charAt(j) != '~') {
                        segments.add(new SelectorSegment(pendingCombinator, current.toString().trim()));
                        current.setLength(0);
                        pendingCombinator = Combinator.DESCENDANT;
                    }
                    continue;
                }
            }
            current.append(c);
        }
        if (!current.isEmpty()) segments.add(new SelectorSegment(pendingCombinator, current.toString().trim()));
        return segments;
    }

    /* ─────────────────── helper ─────────────────── */

    private static int nextSpecialChar(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || c == '.' || c == '[' || c == ':') return i;
        }
        return s.length();
    }

    private static DomNode previousElementSibling(DomNode node) {
        DomNode parent = node.getParent();
        if (parent == null) return null;
        DomNode prev = null;
        for (DomNode c : parent.getChildren()) {
            if (c == node) return prev;
            if (c.isElement()) prev = c;
        }
        return null;
    }
}
