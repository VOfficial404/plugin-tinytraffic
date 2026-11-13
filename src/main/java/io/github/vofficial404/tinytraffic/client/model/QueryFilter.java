package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** A filter tinytraffic applied to a query. */
@Schema(name = "TinytrafficQueryFilter")
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryFilter(
        @Schema(requiredMode = REQUIRED) String dim,
        @Schema(requiredMode = REQUIRED) String value) {}
