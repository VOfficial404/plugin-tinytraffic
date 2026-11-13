package io.github.vofficial404.tinytraffic.feed;

import io.github.vofficial404.tinytraffic.client.Connection;
import io.github.vofficial404.tinytraffic.client.ConnectionResolver;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient.Event;
import io.github.vofficial404.tinytraffic.setting.TrackerSetting;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.feed.TelemetryEventInfo;
import run.halo.feed.TelemetryRecorder;

/**
 * Reports content views from RSS readers to tinytraffic. The RSS plugin puts a one-pixel image into every feed item and
 * calls this recorder when a reader loads it.
 *
 * <p>Each view is sent as a pageview of the post with the source {@code RSS}, so it shows up next to the visits of the
 * site itself and can be told apart in the Sources panel.
 */
@Slf4j
@RequiredArgsConstructor
public class RssTelemetryRecorder implements TelemetryRecorder {

    static final String SOURCE = "RSS";

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ReactiveSettingFetcher settingFetcher;
    private final ConnectionResolver connections;
    private final TinytrafficClient tinytraffic;
    private final ExternalUrlSupplier externalUrl;

    /** Called by the RSS plugin on a worker thread of its own, so blocking here is fine. */
    @Override
    public void record(TelemetryEventInfo eventInfo) {
        try {
            send(eventInfo).block(TIMEOUT);
        } catch (RuntimeException e) {
            // A lost view must not disturb the RSS plugin.
            log.debug("Failed to report an RSS view to tinytraffic: {}", e.getMessage());
        }
    }

    Mono<Void> send(TelemetryEventInfo eventInfo) {
        return TrackerSetting.fetch(settingFetcher)
                .filter(TrackerSetting::isRssTelemetry)
                .flatMap(tracker -> connections.resolve())
                .filter(connection -> connection.baseUri() != null && connection.site() != null)
                .flatMap(connection -> send(connection, eventInfo));
    }

    private Mono<Void> send(Connection connection, TelemetryEventInfo eventInfo) {
        var pageUrl = pageUrl(eventInfo.getPageUrl(), externalUrl.getRaw());
        // tinytraffic drops requests without a browser-like user agent, so there is nothing to report without one.
        if (pageUrl == null || StringUtils.isBlank(eventInfo.getUserAgent())) {
            return Mono.empty();
        }
        var event = new Event("pageview", pageUrl, connection.site(), eventInfo.getReferrer());
        return tinytraffic.sendEvent(connection, event, eventInfo.getUserAgent(), eventInfo.getIp());
    }

    /**
     * The RSS plugin reports the path of the post. tinytraffic needs an absolute URL, and reads the source of a visit
     * from its {@code utm_source} parameter.
     *
     * @return the absolute URL of the page tagged with the source {@link #SOURCE}, or {@code null} when it cannot be
     *     built
     */
    static String pageUrl(String page, URL siteUrl) {
        if (StringUtils.isBlank(page)) {
            return null;
        }
        try {
            var uri = new URI(page.trim());
            if (!uri.isAbsolute()) {
                if (siteUrl == null) {
                    return null;
                }
                uri = siteUrl.toURI().resolve(uri);
            }
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }
            var builder = UriComponentsBuilder.fromUri(uri);
            if (!builder.build().getQueryParams().containsKey("utm_source")) {
                builder.queryParam("utm_source", SOURCE);
            }
            return builder.build(true).toUriString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }
}
