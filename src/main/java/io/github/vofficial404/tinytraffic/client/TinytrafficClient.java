package io.github.vofficial404.tinytraffic.client;

import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import io.github.vofficial404.tinytraffic.client.model.AggregateResult;
import io.github.vofficial404.tinytraffic.client.model.BreakdownResult;
import io.github.vofficial404.tinytraffic.client.model.Realtime;
import io.github.vofficial404.tinytraffic.client.model.Site;
import io.github.vofficial404.tinytraffic.client.model.TimeseriesResult;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.app.infra.utils.HttpSecurityUtils;

/**
 * Client for the <a href="https://github.com/egoist/tinytraffic/blob/main/docs/api.md">tinytraffic API</a>.
 *
 * <p>The server address comes from the plugin settings, so every request is restricted: redirects are never followed,
 * responses are limited in time and size, and unless the administrator allows it, the address may not lead to a private
 * or local network. The API key is only attached to a request that passed these checks.
 */
@Component
public class TinytrafficClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(15);
    private static final long MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_UPSTREAM_MESSAGE_LENGTH = 200;

    private static final ParameterizedTypeReference<List<Site>> SITES = new ParameterizedTypeReference<>() {};

    /** Refuses addresses that resolve to a private, local or special-use address. */
    private final WebClient guarded = webClient(HttpSecurityUtils.secureHttpClient());

    /** For administrators who run tinytraffic inside their own network. */
    private final WebClient unguarded = webClient(HttpClient.create()
            .followRedirect(false)
            .responseTimeout(RESPONSE_TIMEOUT)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis()));

    /** The body of an error answer: {@code {"error": "site not found"}}. */
    record UpstreamError(String error) {}

    /**
     * An event in Plausible's format, which tinytraffic accepts.
     *
     * @param name {@code pageview} or the name of a custom event
     * @param url absolute URL of the page
     * @param domain domain of the site to record to
     * @param referrer referrer of the visit, may be {@code null}
     */
    public record Event(String name, String url, String domain, String referrer) {}

    private static WebClient webClient(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(HttpSecurityUtils.maxResponseSizeFilter(MAX_RESPONSE_BYTES))
                .build();
    }

    /** Finds the configured site among the sites the API key can see. */
    public Mono<Site> site(Connection connection) {
        return get(connection, "/api/v1/sites", null, SITES)
                .flatMap(sites -> Mono.justOrEmpty(sites.stream()
                        .filter(site -> site.matches(connection.site()))
                        .findFirst()))
                .switchIfEmpty(Mono.error(() -> siteNotFound(connection)));
    }

    public Mono<AggregateResult> aggregate(Connection connection, StatsQuery query) {
        return get(connection, sitePath(connection, "/aggregate"), query.toQueryString(), type(AggregateResult.class));
    }

    public Mono<TimeseriesResult> timeseries(Connection connection, StatsQuery query) {
        return get(
                connection, sitePath(connection, "/timeseries"), query.toQueryString(), type(TimeseriesResult.class));
    }

    public Mono<BreakdownResult> breakdown(Connection connection, String dimension, StatsQuery query, int limit) {
        var path = sitePath(connection, "/breakdown/" + UriUtils.encodePathSegment(dimension, "UTF-8"));
        return get(connection, path, query.toQueryString() + "&limit=" + limit, type(BreakdownResult.class));
    }

    public Mono<Realtime> realtime(Connection connection) {
        return get(connection, sitePath(connection, "/realtime"), null, type(Realtime.class));
    }

    /**
     * Records an event on behalf of a visitor. tinytraffic identifies a visitor by user agent and IP address; it only
     * trusts the forwarded IP when the request carries an API key with write access, and otherwise sees every event as
     * coming from this server.
     *
     * @param userAgent user agent of the visitor
     * @param visitorIp IP address of the visitor, may be {@code null}
     */
    public Mono<Void> sendEvent(Connection connection, Event event, String userAgent, String visitorIp) {
        var uri = uri(connection, "/api/event", null);
        var body = new LinkedHashMap<String, String>();
        body.put("name", event.name());
        body.put("url", event.url());
        body.put("domain", event.domain());
        if (StringUtils.isNotBlank(event.referrer())) {
            body.put("referrer", event.referrer());
        }
        return Mono.defer(() -> {
                    OutboundTargets.check(uri, connection.allowPrivateNetwork());
                    return webClient(connection)
                            .post()
                            .uri(uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .headers(headers -> {
                                headers.set(HttpHeaders.USER_AGENT, userAgent);
                                if (connection.apiKey() != null) {
                                    headers.setBearerAuth(connection.apiKey());
                                    if (StringUtils.isNotBlank(visitorIp)) {
                                        headers.set("X-Forwarded-For", visitorIp);
                                    }
                                }
                            })
                            .bodyValue(body)
                            .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                                    ? response.releaseBody()
                                    : failure(response, connection));
                })
                .timeout(TOTAL_TIMEOUT)
                .onErrorMap(e -> !(e instanceof TinytrafficException), TinytrafficClient::unreachable);
    }

    private <T> Mono<T> get(Connection connection, String path, String query, ParameterizedTypeReference<T> type) {
        var uri = uri(connection, path, query);
        return Mono.defer(() -> {
                    OutboundTargets.check(uri, connection.allowPrivateNetwork());
                    return webClient(connection)
                            .get()
                            .uri(uri)
                            .accept(MediaType.APPLICATION_JSON)
                            .headers(headers -> headers.setBearerAuth(connection.apiKey()))
                            .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                                    ? response.bodyToMono(type)
                                            .onErrorMap(e -> new TinytrafficException(
                                                    Kind.BAD_RESPONSE,
                                                    "无法解析 tinytraffic 返回的数据，请确认地址指向的是 tinytraffic。",
                                                    e))
                                    : failure(response, connection));
                })
                .timeout(TOTAL_TIMEOUT)
                .onErrorMap(e -> !(e instanceof TinytrafficException), TinytrafficClient::unreachable);
    }

    private WebClient webClient(Connection connection) {
        return connection.allowPrivateNetwork() ? unguarded : guarded;
    }

    private static URI uri(Connection connection, String path, String query) {
        return URI.create(connection.baseUri() + path + (StringUtils.isEmpty(query) ? "" : "?" + query));
    }

    private static String sitePath(Connection connection, String suffix) {
        return "/api/v1/sites/" + UriUtils.encodePathSegment(connection.site(), "UTF-8") + suffix;
    }

    private static <T> ParameterizedTypeReference<T> type(Class<T> type) {
        return ParameterizedTypeReference.forType(type);
    }

    /** Turns an error answer into an exception that says what the administrator can do about it. */
    private static <T> Mono<T> failure(ClientResponse response, Connection connection) {
        int status = response.statusCode().value();
        return response.bodyToMono(UpstreamError.class)
                .mapNotNull(UpstreamError::error)
                // Anything that is not tinytraffic's JSON error, e.g. the error page of a reverse proxy.
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty("")
                .flatMap(message -> Mono.error(failure(status, message, connection)));
    }

    private static TinytrafficException failure(int status, String upstreamMessage, Connection connection) {
        var detail = StringUtils.isBlank(upstreamMessage)
                ? ""
                : "：" + StringUtils.abbreviate(upstreamMessage.strip(), MAX_UPSTREAM_MESSAGE_LENGTH);
        return switch (status) {
            case 400 -> new TinytrafficException(Kind.INVALID_REQUEST, "tinytraffic 不接受该请求" + detail);
            case 401 -> new TinytrafficException(Kind.UNAUTHORIZED, "tinytraffic 拒绝了 API 密钥，请检查密钥是否填写正确、是否已被撤销。");
            case 403 -> new TinytrafficException(Kind.UNAUTHORIZED, "API 密钥的权限不足" + detail);
            case 404 ->
                upstreamMessage.contains("site")
                        ? siteNotFound(connection)
                        : new TinytrafficException(
                                Kind.NOT_FOUND, "在该地址上没有找到 tinytraffic 的接口（HTTP 404），请检查 tinytraffic 地址。");
            case 429 -> new TinytrafficException(Kind.RATE_LIMITED, "API 密钥的请求过于频繁，请稍后再试。");
            default ->
                status >= 300 && status < 400
                        ? new TinytrafficException(
                                Kind.BAD_RESPONSE,
                                "tinytraffic 地址返回了重定向（HTTP " + status + "），请填写最终地址，例如把 http 改为 https。")
                        : new TinytrafficException(
                                Kind.BAD_RESPONSE, "tinytraffic 返回了错误（HTTP " + status + "）" + detail);
        };
    }

    private static TinytrafficException siteNotFound(Connection connection) {
        return new TinytrafficException(
                Kind.NOT_FOUND,
                "tinytraffic 中没有域名为 " + connection.site() + " 的站点，请先在 tinytraffic 中添加，或在插件设置中填写正确的站点域名。");
    }

    private static TinytrafficException unreachable(Throwable e) {
        if (e instanceof TimeoutException || e.getCause() instanceof TimeoutException) {
            return new TinytrafficException(Kind.TIMEOUT, "连接 tinytraffic 超时，请稍后再试。", e);
        }
        return new TinytrafficException(
                Kind.UNREACHABLE, "无法连接到 tinytraffic，请检查地址是否正确、服务是否正常运行。如果它部署在内网，还需要在插件设置中开启「允许访问内网地址」。", e);
    }
}
