package io.github.vofficial404.tinytraffic.setting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Values of the {@code tracker} setting group. A field is {@code null} when the group was saved before the field
 * existed, so read them through the {@code is…} methods, which apply the defaults of the setting form.
 *
 * @param enabled inject the tracker script into theme pages
 * @param excludeLoggedIn skip the script for signed-in users
 * @param hashMode count {@code #/routes} as separate pages ({@code data-hash})
 * @param outbound track outbound link clicks ({@code data-outbound})
 * @param scriptUrl optional replacement for the script address
 * @param apiEndpoint optional replacement for the event endpoint ({@code data-api})
 * @param rssTelemetry forward content views reported by the RSS plugin
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrackerSetting(
        Boolean enabled,
        Boolean excludeLoggedIn,
        Boolean hashMode,
        Boolean outbound,
        String scriptUrl,
        String apiEndpoint,
        Boolean rssTelemetry) {

    public static final String GROUP = "tracker";

    private static final TrackerSetting EMPTY = new TrackerSetting(null, null, null, null, null, null, null);

    public static Mono<TrackerSetting> fetch(ReactiveSettingFetcher settingFetcher) {
        return settingFetcher.fetch(GROUP, TrackerSetting.class).defaultIfEmpty(EMPTY);
    }

    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }

    public boolean isExcludeLoggedIn() {
        return Boolean.TRUE.equals(excludeLoggedIn);
    }

    public boolean isHashMode() {
        return Boolean.TRUE.equals(hashMode);
    }

    public boolean isOutbound() {
        return !Boolean.FALSE.equals(outbound);
    }

    public boolean isRssTelemetry() {
        return !Boolean.FALSE.equals(rssTelemetry);
    }
}
