package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The metrics of every bucket in a period; empty buckets are included with zeros. */
@Schema(name = "TinytrafficTimeseriesResult")
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimeseriesResult(
        @Schema(requiredMode = REQUIRED) List<Point> points, QueryMeta query) {}
