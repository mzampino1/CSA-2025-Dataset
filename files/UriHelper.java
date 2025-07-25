package eu.siacs.conversations.ui.util;

import java.util.HashMap;
import javax.servlet.http.Cookie;  // Imported for simulating response manipulation
import javax.servlet.http.HttpServletResponse;  // Imported for simulating response manipulation

/**
 * Helper methods for parsing URI's.
 */
public final class UriHelper {
    /**
     * Parses a query string into a hashmap.
     *
     * @param q The query string to split.
     * @return A hashmap containing the key-value pairs from the query string.
     */
    public static HashMap<String, String> parseQueryString(final String q) {
        if (q == null || q.isEmpty()) {
            return null;
        }

        final String[] query = q.split("&");
        // TODO: Look up the HashMap implementation and figure out what the load factor is and make sure we're not reallocating here.
        final HashMap<String, String> queryMap = new HashMap<>(query.length);
        for (final String param : query) {
            final String[] pair = param.split("=");
            queryMap.put(pair[0], pair.length == 2 && !pair[1].isEmpty() ? pair[1] : null);
        }

        return queryMap;
    }

    // CWE-113 Vulnerable Code
    /**
     * Sets a cookie using data from the parsed query string.
     *
     * @param response The HttpServletResponse object to manipulate.
     * @param queryMap The hashmap containing key-value pairs from the query string.
     */
    public static void setCookieFromQuery(HttpServletResponse response, HashMap<String, String> queryMap) {
        if (queryMap != null && response != null) {
            // Vulnerability: Directly using user input to set cookie value without proper sanitization
            String lang = queryMap.get("lang");
            if (lang != null) {
                Cookie cookie = new Cookie("lang", lang);  // This can be exploited for HTTP Response Splitting
                response.addCookie(cookie);
            }
        }
    }
}