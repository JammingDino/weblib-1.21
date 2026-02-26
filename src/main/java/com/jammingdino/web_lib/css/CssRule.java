package com.jammingdino.web_lib.css;

import java.util.*;

/**
 * Data model for a single parsed CSS rule.
 *
 * Example:
 *   .my-class, #id { color: red; font-size: 14px }
 *
 * is represented as one CssRule with two selectors and two declarations.
 */
public class CssRule {

    private final List<String> selectors;          // already split and trimmed
    private final Map<String, String> declarations; // property -> value (lowercase property)
    private final int specificity;                  // highest specificity of all selectors (for cascade)

    public CssRule(List<String> selectors, Map<String, String> declarations) {
        this.selectors    = Collections.unmodifiableList(new ArrayList<>(selectors));
        this.declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
        this.specificity  = selectors.stream()
                .mapToInt(CssSpecificity::compute)
                .max().orElse(0);
    }

    public List<String> getSelectors()              { return selectors; }
    public Map<String, String> getDeclarations()    { return declarations; }
    public String getDeclaration(String prop)       { return declarations.get(prop.toLowerCase()); }
    public int getSpecificity()                     { return specificity; }

    @Override
    public String toString() {
        return selectors + " { " + declarations + " }";
    }
}