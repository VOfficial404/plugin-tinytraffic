package io.github.vofficial404.tinytraffic.client;

import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Where and how to reach tinytraffic, as far as the plugin settings define it.
 *
 * @param baseUri address of the tinytraffic server, {@code null} when missing or invalid
 * @param site domain of the site whose stats are read, {@code null} when it can't be determined
 * @param apiKey API key, {@code null} when no usable key is configured
 * @param allowPrivateNetwork whether the address may resolve to a private or loopback address
 */
public record Connection(URI baseUri, String site, String apiKey, boolean allowPrivateNetwork) {

    public static final String MISSING_ENDPOINT = "endpoint";
    public static final String MISSING_DOMAIN = "domain";
    public static final String MISSING_API_KEY = "apiKey";

    /** Names of the settings that still have to be filled in before stats can be read. */
    public List<String> missing() {
        var missing = new ArrayList<String>(3);
        if (baseUri == null) {
            missing.add(MISSING_ENDPOINT);
        }
        if (site == null) {
            missing.add(MISSING_DOMAIN);
        }
        if (apiKey == null) {
            missing.add(MISSING_API_KEY);
        }
        return missing;
    }

    /** @throws TinytrafficException when a setting needed for reading stats is missing */
    public Connection requireComplete() {
        if (!missing().isEmpty()) {
            throw new TinytrafficException(Kind.NOT_CONFIGURED, "插件尚未完成配置，请先填写 tinytraffic 地址和 API 密钥。");
        }
        return this;
    }

    // The default toString() would print the API key.
    @Override
    public String toString() {
        return "Connection[baseUri=" + baseUri + ", site=" + site + ", apiKey=" + (apiKey == null ? "null" : "***")
                + ", allowPrivateNetwork=" + allowPrivateNetwork + "]";
    }
}
