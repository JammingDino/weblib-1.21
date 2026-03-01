package com.jammingdino.web_lib.screen;

import com.jammingdino.web_lib.WebLib;
import com.jammingdino.web_lib.api.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads HTML template files from mod resources and applies named placeholder
 * substitutions. Placeholders use the syntax {@code {{key}}} in the template.
 */
public class ScreenHtmlLoader {

    /**
     * Returns a {@link ResourceLoader} that reads text files from the classpath.
     *
     * <p>The path may use either of the following formats:
     * <ul>
     *   <li>{@code namespace:path} – resolved to {@code assets/namespace/path}.</li>
     *   <li>A raw classpath path such as {@code assets/mymod/styles/main.css}.</li>
     * </ul>
     *
     * @return A classpath-backed {@link ResourceLoader}.
     */
    public static ResourceLoader defaultLoader() {
        return path -> {
            if (path == null || path.isBlank()) return null;
            String resolved = path.contains(":") && !path.startsWith("assets/")
                    ? "assets/" + path.replace(":", "/")
                    : path;
            InputStream stream = ScreenHtmlLoader.class.getClassLoader().getResourceAsStream(resolved);
            if (stream == null) return null;
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        };
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * Loads an HTML resource file from the classpath and replaces all
     * {@code {{key}}} placeholders with the corresponding values from the map.
     * Unresolved placeholders are left unchanged and a warning is logged.
     *
     * @param resourcePath classpath path to the HTML template (e.g.
     *                     {@code "assets/web_lib/screens/config.html"})
     * @param values       map of placeholder names to replacement strings
     * @return the fully resolved HTML string
     * @throws RuntimeException if the resource cannot be found or read
     */
    public static String load(String resourcePath, Map<String, String> values) {
        InputStream stream = ScreenHtmlLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new RuntimeException("HTML resource not found: " + resourcePath);
        }
        String template;
        try (stream) {
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read HTML resource: " + resourcePath, e);
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            if (replacement == null) {
                WebLib.LOGGER.warn("[web_lib] Unresolved placeholder '{{{}}}' in {}", key, resourcePath);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
