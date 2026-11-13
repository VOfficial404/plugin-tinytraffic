package io.github.vofficial404.tinytraffic.endpoint;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.vofficial404.tinytraffic.client.Connection;
import io.github.vofficial404.tinytraffic.client.model.Site;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Whether the plugin is ready to show stats, and for which site. */
@Schema(name = "TinytrafficSiteStatus")
public record SiteStatus(
        @Schema(requiredMode = REQUIRED, description = "Whether the settings needed for reading stats are complete")
        boolean configured,

        @ArraySchema(
                arraySchema = @Schema(requiredMode = REQUIRED, description = "Settings that are still missing"),
                schema = @Schema(allowableValues = {"endpoint", "domain", "apiKey"}))
        List<String> missing,

        @Schema(description = "Address of the tinytraffic server")
        String endpoint,

        @Schema(description = "Domain of the site whose stats are shown")
        String domain,

        @Schema(description = "Address of the site's dashboard in tinytraffic")
        String dashboardUrl,

        @Schema(description = "The site in tinytraffic; absent while the plugin is not configured")
        Site site) {

    static SiteStatus notConfigured(Connection connection) {
        return new SiteStatus(false, connection.missing(), endpoint(connection), connection.site(), null, null);
    }

    static SiteStatus of(Connection connection, Site site) {
        var endpoint = endpoint(connection);
        return new SiteStatus(true, List.of(), endpoint, site.domain(), endpoint + "/sites/" + site.id(), site);
    }

    private static String endpoint(Connection connection) {
        return connection.baseUri() == null ? null : connection.baseUri().toString();
    }
}
