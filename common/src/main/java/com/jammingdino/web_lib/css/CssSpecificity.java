package com.jammingdino.web_lib.css;

/**
 * Computes a simplified CSS specificity integer from a single selector string.
 *
 * Specificity is packed as:
 *   (inline * 1_000_000) + (ids * 10_000) + (classes/attrs/pseudos * 100) + (elements * 1)
 *
 * This is a best-effort approximation – it handles the vast majority of
 * practical selectors without implementing the full CSS4 algorithm.
 */
public final class CssSpecificity {

    private CssSpecificity() {}

    public static int compute(String selector) {
        if (selector == null || selector.isBlank()) return 0;

        int ids      = 0;
        int classes  = 0;
        int elements = 0;

        // Strip pseudo-element ::foo (counts as element, handled below)
        // Strip :not(...) parens – the contents count, the :not itself doesn't
        String s = selector.trim();

        // Split on combinators to look at individual simple selectors
        // Combinators: space  >  ~  +
        String[] parts = s.split("[\\s>~+]+");

        for (String part : parts) {
            if (part.isBlank()) continue;

            // Walk character by character within a simple selector
            int i = 0;
            while (i < part.length()) {
                char c = part.charAt(i);

                if (c == '#') {
                    ids++;
                    i = skipIdentifier(part, i + 1);
                } else if (c == '.') {
                    classes++;
                    i = skipIdentifier(part, i + 1);
                } else if (c == '[') {
                    classes++; // attribute selector
                    int close = part.indexOf(']', i);
                    i = close == -1 ? part.length() : close + 1;
                } else if (c == ':') {
                    if (i + 1 < part.length() && part.charAt(i + 1) == ':') {
                        // ::pseudo-element
                        elements++;
                        i = skipIdentifier(part, i + 2);
                    } else {
                        // :pseudo-class
                        classes++;
                        i = skipIdentifier(part, i + 1);
                        // skip parens for :not(), :nth-child(), etc.
                        if (i < part.length() && part.charAt(i) == '(') {
                            int close = part.indexOf(')', i);
                            i = close == -1 ? part.length() : close + 1;
                        }
                    }
                } else if (c == '*') {
                    // Universal selector – no contribution
                    i++;
                } else if (Character.isLetter(c) || c == '_' || c == '-') {
                    elements++;
                    i = skipIdentifier(part, i);
                } else {
                    i++;
                }
            }
        }

        return ids * 10_000 + classes * 100 + elements;
    }

    private static int skipIdentifier(String s, int start) {
        int i = start;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') i++;
            else break;
        }
        return i;
    }
}