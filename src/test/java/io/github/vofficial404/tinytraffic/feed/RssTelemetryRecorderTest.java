package io.github.vofficial404.tinytraffic.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vofficial404.tinytraffic.client.Connection;
import io.github.vofficial404.tinytraffic.client.ConnectionResolver;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient.Event;
import io.github.vofficial404.tinytraffic.client.TinytrafficException;
import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import io.github.vofficial404.tinytraffic.setting.TrackerSetting;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.feed.TelemetryEventInfo;

@ExtendWith(MockitoExtension.class)
class RssTelemetryRecorderTest {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15";
    private static final Connection CONNECTION =
            new Connection(URI.create("https://stats.example.com"), "example.com", "tt_key", false);

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ConnectionResolver connections;

    @Mock
    TinytrafficClient tinytraffic;

    @Mock
    ExternalUrlSupplier externalUrl;

    @InjectMocks
    RssTelemetryRecorder recorder;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(settingFetcher.fetch(any(), any())).thenReturn(Mono.empty());
        lenient().when(connections.resolve()).thenReturn(Mono.just(CONNECTION));
        lenient()
                .when(externalUrl.getRaw())
                .thenReturn(URI.create("https://example.com").toURL());
        lenient().when(tinytraffic.sendEvent(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private static TelemetryEventInfo view() {
        return new TelemetryEventInfo()
                .setPageUrl("/archives/hello-halo")
                .setTitle("Hello Halo")
                .setUserAgent(USER_AGENT)
                .setIp("203.0.113.9")
                .setReferrer("https://reader.example/");
    }

    @Test
    void reportsAViewAsAPageviewFromRss() {
        recorder.record(view());

        verify(tinytraffic)
                .sendEvent(
                        CONNECTION,
                        new Event(
                                "pageview",
                                "https://example.com/archives/hello-halo?utm_source=RSS",
                                "example.com",
                                "https://reader.example/"),
                        USER_AGENT,
                        "203.0.113.9");
    }

    @Test
    void reportsNothingWhenSwitchedOff() {
        when(settingFetcher.fetch(TrackerSetting.GROUP, TrackerSetting.class))
                .thenReturn(Mono.just(new TrackerSetting(null, null, null, null, null, null, false)));

        recorder.record(view());

        verify(tinytraffic, never()).sendEvent(any(), any(), any(), any());
    }

    @Test
    void reportsNothingUntilTheServerAddressIsConfigured() {
        when(connections.resolve()).thenReturn(Mono.just(new Connection(null, "example.com", null, false)));

        recorder.record(view());

        verify(tinytraffic, never()).sendEvent(any(), any(), any(), any());
    }

    @Test
    void worksWithoutAnApiKey() {
        var withoutKey = new Connection(URI.create("https://stats.example.com"), "example.com", null, false);
        when(connections.resolve()).thenReturn(Mono.just(withoutKey));

        recorder.record(view());

        verify(tinytraffic).sendEvent(any(), any(), any(), any());
    }

    @Test
    void reportsNothingWithoutAUserAgentOrAPage() {
        recorder.record(view().setUserAgent(" "));
        recorder.record(view().setPageUrl(null));

        verify(tinytraffic, never()).sendEvent(any(), any(), any(), any());
    }

    @Test
    void doesNotLetAFailureReachTheRssPlugin() {
        when(tinytraffic.sendEvent(any(), any(), any(), any()))
                .thenReturn(Mono.error(new TinytrafficException(Kind.UNREACHABLE, "down")));

        recorder.record(view());

        verify(tinytraffic).sendEvent(any(), any(), any(), any());
    }

    @Test
    void buildsAnAbsoluteUrlTaggedWithTheSource() throws Exception {
        var site = URI.create("https://example.com").toURL();

        assertThat(RssTelemetryRecorder.pageUrl("/archives/hello", site))
                .isEqualTo("https://example.com/archives/hello?utm_source=RSS");
        assertThat(RssTelemetryRecorder.pageUrl("/archives/hello?page=2", site))
                .isEqualTo("https://example.com/archives/hello?page=2&utm_source=RSS");
        assertThat(RssTelemetryRecorder.pageUrl("/archives/%E4%BD%A0%E5%A5%BD", site))
                .isEqualTo("https://example.com/archives/%E4%BD%A0%E5%A5%BD?utm_source=RSS");
        assertThat(RssTelemetryRecorder.pageUrl("https://other.example/post", site))
                .isEqualTo("https://other.example/post?utm_source=RSS");
        assertThat(RssTelemetryRecorder.pageUrl(
                        "/archives/hello",
                        URI.create("https://example.com/blog/").toURL()))
                .isEqualTo("https://example.com/archives/hello?utm_source=RSS");
    }

    @Test
    void keepsASourceThatIsAlreadyThere() throws Exception {
        assertThat(RssTelemetryRecorder.pageUrl(
                        "/a?utm_source=newsletter",
                        URI.create("https://example.com").toURL()))
                .isEqualTo("https://example.com/a?utm_source=newsletter");
    }

    @Test
    void buildsNoUrlFromWhatIsNotAPage() throws Exception {
        var site = URI.create("https://example.com").toURL();

        assertThat(RssTelemetryRecorder.pageUrl(null, site)).isNull();
        assertThat(RssTelemetryRecorder.pageUrl("  ", site)).isNull();
        assertThat(RssTelemetryRecorder.pageUrl("/archives/hello", null)).isNull();
        assertThat(RssTelemetryRecorder.pageUrl("javascript:alert(1)", site)).isNull();
        assertThat(RssTelemetryRecorder.pageUrl("/a b", site)).isNull();
    }
}
