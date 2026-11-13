package io.github.vofficial404.tinytraffic.setting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Values of the {@code stats} setting group.
 *
 * @param apiKeySecret name of the Halo Secret that holds the tinytraffic API key under {@link #SECRET_KEY}
 * @param allowPrivateNetwork let the server call a tinytraffic that resolves to a private or loopback address
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatsSetting(String apiKeySecret, Boolean allowPrivateNetwork) {

    public static final String GROUP = "stats";

    /** Key inside the Secret that holds the API key. */
    public static final String SECRET_KEY = "apiKey";

    private static final StatsSetting EMPTY = new StatsSetting(null, null);

    public static Mono<StatsSetting> fetch(ReactiveSettingFetcher settingFetcher) {
        return settingFetcher.fetch(GROUP, StatsSetting.class).defaultIfEmpty(EMPTY);
    }

    public boolean isAllowPrivateNetwork() {
        return Boolean.TRUE.equals(allowPrivateNetwork);
    }
}
