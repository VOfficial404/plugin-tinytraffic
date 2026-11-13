package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The top values of a dimension, sorted by visitors. */
@Schema(name = "TinytrafficBreakdownResult")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakdownResult(
        @Schema(requiredMode = REQUIRED) List<BreakdownRow> rows,

        @Schema(requiredMode = REQUIRED, description = "Visitors of the same period and filters, for computing shares")
        @JsonAlias("total_visitors")
        long totalVisitors) {}
