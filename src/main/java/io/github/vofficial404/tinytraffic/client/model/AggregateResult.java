package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** Totals of a period and of the period of equal length before it. */
@Schema(name = "TinytrafficAggregateResult")
@JsonIgnoreProperties(ignoreUnknown = true)
public record AggregateResult(
        @Schema(requiredMode = REQUIRED) Metrics current,

        @Schema(description = "Absent for the period \"all\"")
        Metrics previous,

        QueryMeta query) {}
