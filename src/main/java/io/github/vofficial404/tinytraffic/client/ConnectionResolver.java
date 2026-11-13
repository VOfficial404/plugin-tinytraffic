package io.github.vofficial404.tinytraffic.client;

import io.github.vofficial404.tinytraffic.TinytrafficUrls;
import io.github.vofficial404.tinytraffic.setting.BasicSetting;
import io.github.vofficial404.tinytraffic.setting.StatsSetting;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.plugin.ReactiveSettingFetcher;

/** Turns the plugin settings and the API key Secret into a {@link Connection}. */
@Component
@RequiredArgsConstructor
public class ConnectionResolver {

    private final ReactiveSettingFetcher settingFetcher;
    private final ReactiveExtensionClient client;
    private final ExternalUrlSupplier externalUrl;

    public Mono<Connection> resolve() {
        return Mono.zip(BasicSetting.fetch(settingFetcher), StatsSetting.fetch(settingFetcher))
                .flatMap(settings -> {
                    var basic = settings.getT1();
                    var stats = settings.getT2();
                    return apiKey(stats.apiKeySecret())
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .map(apiKey -> new Connection(
                                    TinytrafficUrls.baseUri(basic.endpoint()).orElse(null),
                                    site(basic.domain()),
                                    apiKey.orElse(null),
                                    stats.isAllowPrivateNetwork()));
                });
    }

    /** The configured domain, or else the host Halo itself is served from. */
    private String site(String configuredDomain) {
        var domain = TinytrafficUrls.normalizeDomain(configuredDomain);
        if (domain != null) {
            return domain;
        }
        var url = externalUrl.getRaw();
        return url == null ? null : TinytrafficUrls.normalizeDomain(url.getHost());
    }

    private Mono<String> apiKey(String secretName) {
        if (StringUtils.isBlank(secretName)) {
            return Mono.empty();
        }
        return client.fetch(Secret.class, secretName).mapNotNull(ConnectionResolver::apiKey);
    }

    /** @return the key stored in the Secret, or {@code null} when it is absent or can't be sent as a header value */
    static String apiKey(Secret secret) {
        String value = null;
        if (secret.getStringData() != null) {
            value = secret.getStringData().get(StatsSetting.SECRET_KEY);
        }
        if (StringUtils.isBlank(value) && secret.getData() != null) {
            var bytes = secret.getData().get(StatsSetting.SECRET_KEY);
            value = bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
        value = StringUtils.trimToNull(value);
        return value != null && value.chars().allMatch(c -> c > 0x20 && c < 0x7f) ? value : null;
    }
}
