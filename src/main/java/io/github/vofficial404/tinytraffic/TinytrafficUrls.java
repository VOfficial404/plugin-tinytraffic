package io.github.vofficial404.tinytraffic;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/** Parsing of the addresses and domains a user types into the plugin settings. */
public final class TinytrafficUrls {

    private TinytrafficUrls() {}

    /**
     * Parses the address of the tinytraffic server.
     *
     * @return the address without a trailing slash, or empty when it is blank or not a plain {@code http(s)} URL
     *     (credentials, a query or a fragment are not accepted)
     */
    public static Optional<URI> baseUri(String raw) {
        var value = StringUtils.stripEnd(StringUtils.trimToEmpty(raw), "/");
        if (value.isEmpty()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!isHttp(uri)
                || StringUtils.isBlank(uri.getHost())
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    /**
     * Accepts what may replace the script address or the event endpoint: an absolute {@code http(s)} URL, or a path on
     * the site itself such as {@code /stats/js/script.js}, for sites that proxy tinytraffic under their own domain.
     */
    public static Optional<String> scriptReference(String raw) {
        var value = StringUtils.trimToEmpty(raw);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.startsWith("/")) {
            // "//host/path" is another origin, and browsers read a backslash as a slash.
            return value.startsWith("//") || value.contains("\\") ? Optional.empty() : Optional.of(value);
        }
        try {
            var uri = new URI(value);
            if (isHttp(uri) && StringUtils.isNotBlank(uri.getHost()) && uri.getRawUserInfo() == null) {
                return Optional.of(value);
            }
        } catch (URISyntaxException e) {
            // not a URL
        }
        return Optional.empty();
    }

    /**
     * Normalizes a site domain the way tinytraffic does: lower case, no scheme, no path and no leading {@code www.}.
     *
     * @return the domain, or {@code null} when nothing is left
     */
    public static String normalizeDomain(String raw) {
        var domain = StringUtils.trimToEmpty(raw).toLowerCase(Locale.ROOT);
        domain = removePrefix(domain, "https://");
        domain = removePrefix(domain, "http://");
        var end = StringUtils.indexOfAny(domain, "/?#");
        if (end >= 0) {
            domain = domain.substring(0, end);
        }
        return StringUtils.trimToNull(removePrefix(domain, "www."));
    }

    private static String removePrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static boolean isHttp(URI uri) {
        var scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
