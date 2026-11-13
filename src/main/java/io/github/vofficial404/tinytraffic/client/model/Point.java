package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** The metrics of one time bucket. */
@Schema(name = "TinytrafficPoint")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Point(
        @Schema(requiredMode = REQUIRED, description = "Start of the bucket in the timezone of the site, no offset")
        String date,

        @Schema(requiredMode = REQUIRED) long visitors,
        @Schema(requiredMode = REQUIRED) long visits,
        @Schema(requiredMode = REQUIRED) long pageviews,

        @Schema(requiredMode = REQUIRED) @JsonAlias("views_per_visit")
        double viewsPerVisit,

        @Schema(requiredMode = REQUIRED) @JsonAlias("bounce_rate")
        double bounceRate,

        @Schema(requiredMode = REQUIRED) @JsonAlias("visit_duration")
        double visitDuration) {}
