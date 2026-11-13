package io.github.vofficial404.tinytraffic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import io.github.vofficial404.tinytraffic.setting.BasicSetting;
import io.github.vofficial404.tinytraffic.setting.StatsSetting;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.plugin.ReactiveSettingFetcher;

@ExtendWith(MockitoExtension.class)
class ConnectionResolverTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ExternalUrlSupplier externalUrl;

    @InjectMocks
    ConnectionResolver resolver;

    @BeforeEach
    void noSettingsSaved() {
        lenient().when(settingFetcher.fetch(any(), any())).thenReturn(Mono.empty());
    }

    private void basic(String endpoint, String domain) {
        when(settingFetcher.fetch(BasicSetting.GROUP, BasicSetting.class))
                .thenReturn(Mono.just(new BasicSetting(endpoint, domain)));
    }

    private void stats(String secretName, Boolean allowPrivateNetwork) {
        when(settingFetcher.fetch(StatsSetting.GROUP, StatsSetting.class))
                .thenReturn(Mono.just(new StatsSetting(secretName, allowPrivateNetwork)));
    }

    private static Secret secret(Map<String, String> stringData, Map<String, byte[]> data) {
        var secret = new Secret();
        secret.setStringData(stringData);
        secret.setData(data);
        return secret;
    }

    @Test
    void resolvesACompleteConnection() {
        basic("https://stats.example.com/", "Example.com");
        stats("tt-key", true);
        when(client.fetch(Secret.class, "tt-key")).thenReturn(Mono.just(secret(Map.of("apiKey", " tt_abc "), null)));

        var connection = resolver.resolve().block();

        assertThat(connection)
                .isEqualTo(new Connection(URI.create("https://stats.example.com"), "example.com", "tt_abc", true));
        assertThat(connection.missing()).isEmpty();
        assertThat(connection.requireComplete()).isSameAs(connection);
    }

    @Test
    void reportsEverythingThatIsMissingBeforeAnythingIsSaved() throws Exception {
        when(externalUrl.getRaw()).thenReturn(null);

        var connection = resolver.resolve().block();

        assertThat(connection).isNotNull();
        assertThat(connection.missing()).containsExactly("endpoint", "domain", "apiKey");
        assertThat(connection.allowPrivateNetwork()).isFalse();
        assertThatThrownBy(connection::requireComplete)
                .isInstanceOfSatisfying(
                        TinytrafficException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.NOT_CONFIGURED));
        verify(client, never()).fetch(eq(Secret.class), any());
    }

    @Test
    void fallsBackToTheAddressOfHaloForTheSite() throws Exception {
        basic("https://stats.example.com", "  ");
        when(externalUrl.getRaw())
                .thenReturn(URI.create("https://www.myblog.example/").toURL());

        var connection = resolver.resolve().block();

        assertThat(connection).isNotNull();
        assertThat(connection.site()).isEqualTo("myblog.example");
        assertThat(connection.missing()).containsExactly("apiKey");
    }

    @Test
    void treatsAMissingSecretAsAMissingKey() {
        basic("https://stats.example.com", "example.com");
        stats("gone", null);
        when(client.fetch(Secret.class, "gone")).thenReturn(Mono.empty());

        var connection = resolver.resolve().block();

        assertThat(connection).isNotNull();
        assertThat(connection.apiKey()).isNull();
        assertThat(connection.missing()).containsExactly("apiKey");
    }

    @Test
    void treatsAnInvalidAddressAsMissing() {
        basic("stats.example.com", "example.com");

        var connection = resolver.resolve().block();

        assertThat(connection).isNotNull();
        assertThat(connection.baseUri()).isNull();
        assertThat(connection.missing()).contains("endpoint");
    }

    @Test
    void readsTheKeyFromEitherFormOfSecretData() {
        assertThat(ConnectionResolver.apiKey(secret(Map.of("apiKey", "tt_string"), null)))
                .isEqualTo("tt_string");
        assertThat(ConnectionResolver.apiKey(
                        secret(null, Map.of("apiKey", "tt_bytes\n".getBytes(StandardCharsets.UTF_8)))))
                .isEqualTo("tt_bytes");
        assertThat(ConnectionResolver.apiKey(
                        secret(Map.of("apiKey", " "), Map.of("apiKey", "tt_bytes".getBytes(StandardCharsets.UTF_8)))))
                .isEqualTo("tt_bytes");
    }

    @Test
    void rejectsAKeyThatIsAbsentOrCannotBeAHeaderValue() {
        assertThat(ConnectionResolver.apiKey(secret(null, null))).isNull();
        assertThat(ConnectionResolver.apiKey(secret(Map.of("other", "tt_abc"), null)))
                .isNull();
        assertThat(ConnectionResolver.apiKey(secret(Map.of("apiKey", "  "), null)))
                .isNull();
        assertThat(ConnectionResolver.apiKey(secret(Map.of("apiKey", "tt_a\r\nX-Injected: 1"), null)))
                .isNull();
        assertThat(ConnectionResolver.apiKey(secret(Map.of("apiKey", "tt a"), null)))
                .isNull();
        assertThat(ConnectionResolver.apiKey(secret(Map.of("apiKey", "密钥"), null)))
                .isNull();
    }

    @Test
    void neverPrintsTheKey() {
        var connection = new Connection(URI.create("https://stats.example.com"), "example.com", "tt_secret", false);

        assertThat(connection.toString()).doesNotContain("tt_secret").contains("stats.example.com");
    }
}
