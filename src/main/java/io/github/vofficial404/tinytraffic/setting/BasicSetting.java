package io.github.vofficial404.tinytraffic.setting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Values of the {@code basic} setting group.
 *
 * @param endpoint public address of the tinytraffic server, e.g. {@code https://stats.example.com}
 * @param domain domain of the site in tinytraffic; blank means "the host the visitor is on"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BasicSetting(String endpoint, String domain) {

    public static final String GROUP = "basic";

    private static final BasicSetting EMPTY = new BasicSetting(null, null);

    public static Mono<BasicSetting> fetch(ReactiveSettingFetcher settingFetcher) {
        return settingFetcher.fetch(GROUP, BasicSetting.class).defaultIfEmpty(EMPTY);
    }
}
