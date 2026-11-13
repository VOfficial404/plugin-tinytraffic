package io.github.vofficial404.tinytraffic.feed;

import io.github.vofficial404.tinytraffic.client.ConnectionResolver;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Registers the RSS telemetry recorder when the RSS plugin (PluginFeed) is installed. That plugin is an optional
 * dependency, so nothing outside this package may refer to its classes.
 */
@Configuration
@ConditionalOnClass(name = "run.halo.feed.TelemetryRecorder")
public class RssTelemetryConfiguration {

    @Bean
    RssTelemetryRecorder rssTelemetryRecorder(
            ReactiveSettingFetcher settingFetcher,
            ConnectionResolver connections,
            TinytrafficClient tinytraffic,
            ExternalUrlSupplier externalUrl) {
        return new RssTelemetryRecorder(settingFetcher, connections, tinytraffic, externalUrl);
    }
}
