package com.jammingdino.web_lib.api;

/**
 * Resolves and loads text resources (CSS, JS, HTML) by path.
 *
 * <p>Implement this interface to control how external files referenced in HTML
 * (e.g. {@code <link rel="stylesheet" href="...">} or {@code <script src="...">})
 * are loaded.  A default classpath-based implementation is available via
 * {@link com.jammingdino.web_lib.screen.ScreenHtmlLoader#defaultLoader()}.
 *
 * <p>Paths may be supplied in two formats:
 * <ul>
 *   <li>{@code namespace:path} – resolved to {@code assets/namespace/path} on the classpath.</li>
 *   <li>A raw classpath path such as {@code assets/mymod/styles/main.css}.</li>
 * </ul>
 */
@FunctionalInterface
public interface ResourceLoader {
    /**
     * Load the text content of a resource.
     *
     * @param path The resource path as found in the HTML attribute value.
     * @return The text content of the resource, or {@code null} if it cannot be loaded.
     */
    String load(String path);
}
