package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** One value of a dimension. */
@Schema(name = "TinytrafficBreakdownRow")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakdownRow(
        @Schema(requiredMode = REQUIRED, description = "Empty means none or unknown, e.g. direct traffic")
        String value,

        @Schema(requiredMode = REQUIRED) long visitors,

        @Schema(
                requiredMode = REQUIRED,
                description = "Visits for entry and exit pages, completions for events, pageviews otherwise")
        long count,

        @Schema(description = "For referrer domains, the number of distinct links under the domain")
        Long links) {}
