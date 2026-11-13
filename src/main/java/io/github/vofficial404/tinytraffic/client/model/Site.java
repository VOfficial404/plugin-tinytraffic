package io.github.vofficial404.tinytraffic.client.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A site as listed by {@code GET /api/v1/sites}. */
@Schema(name = "TinytrafficSite")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Site(
        @Schema(requiredMode = REQUIRED) int id,
        @Schema(requiredMode = REQUIRED) String domain,

        @Schema(description = "Reporting timezone, an IANA name")
        String timezone,

        @Schema(description = "Old domains that still record to this site")
        List<String> aliases,

        @Schema(description = "Whether the dashboard can be viewed without signing in") @JsonProperty("public")
        Boolean publicDashboard,

        @Schema(description = "Unique visitors in the last 24 hours") @JsonAlias("visitors_24h")
        Long visitors24h,

        @Schema(description = "Hourly visitors of the last 24 hours, oldest first")
        List<Long> sparkline) {

    /** @param ref a site ID or a normalized domain */
    public boolean matches(String ref) {
        return String.valueOf(id).equals(ref)
                || ref.equalsIgnoreCase(domain)
                || (aliases != null && aliases.stream().anyMatch(ref::equalsIgnoreCase));
    }
}
