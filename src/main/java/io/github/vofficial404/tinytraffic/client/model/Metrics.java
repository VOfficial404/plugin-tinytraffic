package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** Totals of a period. */
@Schema(name = "TinytrafficMetrics")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Metrics(
        @Schema(requiredMode = REQUIRED, description = "Unique visitors")
        long visitors,

        @Schema(requiredMode = REQUIRED, description = "Sessions")
        long visits,

        @Schema(requiredMode = REQUIRED) long pageviews,

        @Schema(requiredMode = REQUIRED) @JsonAlias("views_per_visit")
        double viewsPerVisit,

        @Schema(requiredMode = REQUIRED, description = "Percentage of visits with a single event")
        @JsonAlias("bounce_rate")
        double bounceRate,

        @Schema(requiredMode = REQUIRED, description = "Average visit length in seconds") @JsonAlias("visit_duration")
        double visitDuration) {}
