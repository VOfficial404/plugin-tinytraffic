package io.github.vofficial404.tinytraffic.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import io.github.vofficial404.tinytraffic.client.Connection;
import io.github.vofficial404.tinytraffic.client.ConnectionResolver;
import io.github.vofficial404.tinytraffic.client.StatsQuery;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient;
import io.github.vofficial404.tinytraffic.client.TinytrafficException;
import io.github.vofficial404.tinytraffic.client.model.AggregateResult;
import io.github.vofficial404.tinytraffic.client.model.BreakdownResult;
import io.github.vofficial404.tinytraffic.client.model.Realtime;
import io.github.vofficial404.tinytraffic.client.model.TimeseriesResult;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Console API that reads the stats of the configured site from tinytraffic. The API key stays on the server; the
 * Console only ever sees the numbers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsEndpoint implements CustomEndpoint {

    private static final String TAG = "TinytrafficStatsV1alpha1Console";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 1000;

    private final ConnectionResolver connections;
    private final TinytrafficClient tinytraffic;

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.tinytraffic.halo.run", "v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET(
                        "/stats/site",
                        this::site,
                        builder -> builder.operationId("GetTinytrafficSite")
                                .description("Whether the plugin is configured, and the site whose stats it shows.")
                                .tag(TAG)
                                .response(responseBuilder().implementation(SiteStatus.class)))
                .GET("/stats/aggregate", this::aggregate, builder -> {
                    builder.operationId("GetTinytrafficAggregate")
                            .description("Totals of a period, and of the period before it.")
                            .tag(TAG)
                            .response(responseBuilder().implementation(AggregateResult.class));
                    queryParameters(builder);
                })
                .GET("/stats/timeseries", this::timeseries, builder -> {
                    builder.operationId("GetTinytrafficTimeseries")
                            .description("The metrics of a period per time bucket.")
                            .tag(TAG)
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("interval")
                                    .description("hour, day or month. Chosen from the period by default.")
                                    .implementation(String.class))
                            .response(responseBuilder().implementation(TimeseriesResult.class));
                    queryParameters(builder);
                })
                .GET("/stats/breakdown", this::breakdown, builder -> {
                    builder.operationId("GetTinytrafficBreakdown")
                            .description("The top values of a dimension, sorted by visitors.")
                            .tag(TAG)
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("dimension")
                                    .description("page, entry_page, exit_page, source, referrer, referrer_url,"
                                            + " utm_source, utm_medium, utm_campaign, utm_content, utm_term, country,"
                                            + " region, city, browser, os, device or event.")
                                    .required(true)
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("limit")
                                    .description("Number of rows, 1 to 1000. Defaults to 10.")
                                    .implementation(Integer.class))
                            .response(responseBuilder().implementation(BreakdownResult.class));
                    queryParameters(builder);
                })
                .GET(
                        "/stats/realtime",
                        this::realtime,
                        builder -> builder.operationId("GetTinytrafficRealtime")
                                .description("Visitors seen in the last five minutes.")
                                .tag(TAG)
                                .response(responseBuilder().implementation(Realtime.class)))
                .build();
    }

    private static void queryParameters(Builder builder) {
        builder.parameter(parameterBuilder()
                        .in(ParameterIn.QUERY)
                        .name("period")
                        .description("today, yesterday, 24h, 7d, 30d, 90d, month, lastmonth, 12mo, all or custom."
                                + " Defaults to 30d.")
                        .implementation(String.class))
                .parameter(parameterBuilder()
                        .in(ParameterIn.QUERY)
                        .name("from")
                        .description("First day of a custom period, YYYY-MM-DD.")
                        .implementation(String.class))
                .parameter(parameterBuilder()
                        .in(ParameterIn.QUERY)
                        .name("to")
                        .description("Last day of a custom period, YYYY-MM-DD.")
                        .implementation(String.class))
                .parameter(parameterBuilder()
                        .in(ParameterIn.QUERY)
                        .name("filter")
                        .description("A filter written dimension:value, e.g. page:/archives/*. An empty value means"
                                + " none. Repeat it for several filters, which must all match.")
                        .implementationArray(String.class));
    }

    private Mono<ServerResponse> site(ServerRequest request) {
        return respond(connections
                .resolve()
                .flatMap(connection -> connection.missing().isEmpty()
                        ? tinytraffic.site(connection).map(site -> SiteStatus.of(connection, site))
                        : Mono.just(SiteStatus.notConfigured(connection))));
    }

    private Mono<ServerResponse> aggregate(ServerRequest request) {
        return respond(connection -> tinytraffic.aggregate(connection, StatsQuery.from(request.queryParams())));
    }

    private Mono<ServerResponse> timeseries(ServerRequest request) {
        return respond(connection -> tinytraffic.timeseries(connection, StatsQuery.from(request.queryParams())));
    }

    private Mono<ServerResponse> breakdown(ServerRequest request) {
        return respond(connection -> {
            var dimension = request.queryParam("dimension").orElse("");
            if (!StatsQuery.DIMENSIONS.contains(dimension)) {
                throw new ServerWebInputException("不支持的维度：" + StringUtils.abbreviate(dimension, 40));
            }
            var query = StatsQuery.from(request.queryParams());
            return tinytraffic.breakdown(connection, dimension, query, limit(request));
        });
    }

    private Mono<ServerResponse> realtime(ServerRequest request) {
        return respond(tinytraffic::realtime);
    }

    private static int limit(ServerRequest request) {
        var raw = request.queryParam("limit").map(String::trim).orElse("");
        if (raw.isEmpty()) {
            return DEFAULT_LIMIT;
        }
        try {
            var limit = Integer.parseInt(raw);
            if (limit >= 1 && limit <= MAX_LIMIT) {
                return limit;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new ServerWebInputException("limit 应为 1 到 " + MAX_LIMIT + " 之间的整数。");
    }

    private <T> Mono<ServerResponse> respond(Function<Connection, Mono<T>> call) {
        return respond(connections.resolve().map(Connection::requireComplete).flatMap(call));
    }

    private static <T> Mono<ServerResponse> respond(Mono<T> body) {
        return body.onErrorMap(TinytrafficException.class, StatsEndpoint::toResponseStatus)
                .flatMap(value -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(value));
    }

    /**
     * A failure of tinytraffic is not a failure of the signed-in user. In particular it is never reported as 401, which
     * the Console takes for an expired login.
     */
    private static ResponseStatusException toResponseStatus(TinytrafficException e) {
        var status =
                switch (e.getKind()) {
                    case NOT_CONFIGURED, BLOCKED_ADDRESS, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                    case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                    case UNAUTHORIZED, UNREACHABLE, BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
                };
        log.debug("tinytraffic request failed: {}", e.getMessage(), e.getCause());
        return new ResponseStatusException(status, e.getMessage(), e);
    }
}
