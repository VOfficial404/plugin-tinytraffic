package io.github.vofficial404.tinytraffic.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The range tinytraffic resolved a query to. */
@Schema(name = "TinytrafficQueryMeta")
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryMeta(
        String period,

        @Schema(description = "First day of the range, YYYY-MM-DD")
        String from,

        @Schema(description = "Last day of the range, YYYY-MM-DD")
        String to,

        @Schema(description = "hour, day or month") String interval,
        String timezone,
        List<QueryFilter> filters) {}
