package io.github.vofficial404.tinytraffic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.vofficial404.tinytraffic.client.TinytrafficClient.Event;
import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

/** Runs the client against a local stand-in that answers like tinytraffic's {@code /api/v1}. */
class TinytrafficClientTest {

    private static final String API_KEY = "tt_test_key";
    private static final Duration WAIT = Duration.ofSeconds(30);

    /** What the stand-in saw of a request. */
    record Seen(
            String method,
            String path,
            String query,
            String authorization,
            String userAgent,
            String forwardedFor,
            String body) {}

    private final List<Seen> requests = new CopyOnWriteArrayList<>();
    private final TinytrafficClient client = new TinytrafficClient();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void answer(String path, int status, String contentType, String body) {
        server.createContext(path, exchange -> {
            record(exchange);
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }

    private void answerJson(String path, int status, String json) {
        answer(path, status, "application/json", json);
    }

    private void record(HttpExchange exchange) throws IOException {
        var headers = exchange.getRequestHeaders();
        requests.add(new Seen(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(),
                headers.getFirst("Authorization"),
                headers.getFirst("User-Agent"),
                headers.getFirst("X-Forwarded-For"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    /** A connection to the stand-in, which listens on the loopback address. */
    private Connection connection(boolean allowPrivateNetwork) {
        return connection("127.0.0.1", allowPrivateNetwork);
    }

    private Connection connection(String host, boolean allowPrivateNetwork) {
        var base = URI.create("http://" + host + ":" + server.getAddress().getPort());
        return new Connection(base, "example.com", API_KEY, allowPrivateNetwork);
    }

    private static StatsQuery query(String... keyValues) {
        var params = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        return StatsQuery.from(params);
    }

    private static Kind kindOf(Throwable e) {
        assertThat(e).isInstanceOf(TinytrafficException.class);
        return ((TinytrafficException) e).getKind();
    }

    @Test
    void findsTheConfiguredSiteAmongAllSites() {
        answerJson("/api/v1/sites", 200, """
                [
                  {"id": 1, "domain": "other.com", "timezone": "UTC", "aliases": [], "public": false,
                   "created_at": "2026-11-10T10:20:57.79529Z", "visitors_24h": 3, "sparkline": [0, 1, 2]},
                  {"id": 7, "domain": "example.com", "timezone": "Asia/Shanghai", "aliases": ["old.example.com"],
                   "public": true, "created_at": "2026-11-10T10:20:57.79529Z", "visitors_24h": 1478,
                   "sparkline": [88, 92, 95]}
                ]
                """);

        var site = client.site(connection(true)).block(WAIT);

        assertThat(site).isNotNull();
        assertThat(site.id()).isEqualTo(7);
        assertThat(site.domain()).isEqualTo("example.com");
        assertThat(site.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(site.publicDashboard()).isTrue();
        assertThat(site.visitors24h()).isEqualTo(1478L);
        assertThat(site.sparkline()).containsExactly(88L, 92L, 95L);
        assertThat(requests).singleElement().satisfies(seen -> {
            assertThat(seen.method()).isEqualTo("GET");
            assertThat(seen.authorization()).isEqualTo("Bearer " + API_KEY);
        });
    }

    @Test
    void findsASiteByAnOldDomain() {
        answerJson("/api/v1/sites", 200, """
                [{"id": 7, "domain": "new.example.com", "aliases": ["example.com"], "public": false}]
                """);

        assertThat(client.site(connection(true)).block(WAIT))
                .satisfies(site -> assertThat(site.id()).isEqualTo(7));
    }

    @Test
    void reportsASiteThatTinytrafficDoesNotHave() {
        answerJson("/api/v1/sites", 200, "[{\"id\": 1, \"domain\": \"other.com\"}]");

        assertThatThrownBy(() -> client.site(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.NOT_FOUND))
                .hasMessageContaining("example.com");
    }

    @Test
    void readsAggregatesWithSnakeCaseFields() {
        answerJson("/api/v1/sites/example.com/aggregate", 200, """
                {
                  "query": {"period": "7d", "from": "2026-11-04", "to": "2026-11-10", "interval": "day",
                            "timezone": "Asia/Singapore", "filters": [{"dim": "page", "value": "/blog/*"}]},
                  "current": {"visitors": 53730, "visits": 53731, "pageviews": 137339, "views_per_visit": 2.556,
                              "bounce_rate": 44.28, "visit_duration": 202.06},
                  "previous": {"visitors": 48120, "visits": 48121, "pageviews": 120000, "views_per_visit": 2.4,
                               "bounce_rate": 50.5, "visit_duration": 180.5}
                }
                """);

        var result = client.aggregate(connection(true), query("period", "7d", "filter", "page:/blog/*"))
                .block(WAIT);

        assertThat(result).isNotNull();
        assertThat(result.current().visitors()).isEqualTo(53730);
        assertThat(result.current().visits()).isEqualTo(53731);
        assertThat(result.current().pageviews()).isEqualTo(137339);
        assertThat(result.current().viewsPerVisit()).isEqualTo(2.556);
        assertThat(result.current().bounceRate()).isEqualTo(44.28);
        assertThat(result.current().visitDuration()).isEqualTo(202.06);
        assertThat(result.previous().visitors()).isEqualTo(48120);
        assertThat(result.query().timezone()).isEqualTo("Asia/Singapore");
        assertThat(result.query().filters()).singleElement().satisfies(filter -> {
            assertThat(filter.dim()).isEqualTo("page");
            assertThat(filter.value()).isEqualTo("/blog/*");
        });
        assertThat(requests)
                .singleElement()
                .satisfies(seen -> assertThat(seen.query()).isEqualTo("period=7d&f=page%3A%2Fblog%2F*"));
    }

    @Test
    void acceptsAnAggregateWithoutAPreviousPeriod() {
        answerJson("/api/v1/sites/example.com/aggregate", 200, """
                {"current": {"visitors": 1, "visits": 1, "pageviews": 2, "views_per_visit": 2, "bounce_rate": 0,
                             "visit_duration": 10}, "previous": null, "query": {"period": "all"}}
                """);

        var result = client.aggregate(connection(true), query("period", "all")).block(WAIT);

        assertThat(result).isNotNull();
        assertThat(result.previous()).isNull();
    }

    @Test
    void readsTimeseries() {
        answerJson("/api/v1/sites/example.com/timeseries", 200, """
                {"points": [
                   {"date": "2026-11-04T00:00:00", "visitors": 3914, "visits": 3915, "pageviews": 9969,
                    "views_per_visit": 2.547, "bounce_rate": 44.58, "visit_duration": 201.77},
                   {"date": "2026-11-05T00:00:00", "visitors": 0, "visits": 0, "pageviews": 0,
                    "views_per_visit": 0, "bounce_rate": 0, "visit_duration": 0}],
                 "query": {"period": "7d", "interval": "day"}}
                """);

        var result = client.timeseries(connection(true), query("period", "7d", "interval", "day"))
                .block(WAIT);

        assertThat(result).isNotNull();
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0).date()).isEqualTo("2026-11-04T00:00:00");
        assertThat(result.points().get(0).visitors()).isEqualTo(3914);
        assertThat(result.points().get(0).viewsPerVisit()).isEqualTo(2.547);
        assertThat(result.points().get(0).visitDuration()).isEqualTo(201.77);
        assertThat(requests)
                .singleElement()
                .satisfies(seen -> assertThat(seen.query()).isEqualTo("period=7d&interval=day"));
    }

    @Test
    void readsBreakdownsAndPassesTheLimit() {
        answerJson("/api/v1/sites/example.com/breakdown/referrer", 200, """
                {"rows": [{"value": "", "visitors": 17173, "count": 43846},
                          {"value": "someblog.dev", "visitors": 11878, "count": 30634, "links": 4}],
                 "total_visitors": 53730}
                """);

        var result = client.breakdown(connection(true), "referrer", query("period", "30d"), 25)
                .block(WAIT);

        assertThat(result).isNotNull();
        assertThat(result.totalVisitors()).isEqualTo(53730);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).value()).isEmpty();
        assertThat(result.rows().get(0).links()).isNull();
        assertThat(result.rows().get(1).links()).isEqualTo(4);
        assertThat(requests)
                .singleElement()
                .satisfies(seen -> assertThat(seen.query()).isEqualTo("period=30d&limit=25"));
    }

    @Test
    void readsRealtimeVisitors() {
        answerJson("/api/v1/sites/example.com/realtime", 200, "{\"visitors\": 12}");

        assertThat(client.realtime(connection(true)).block(WAIT))
                .satisfies(realtime -> assertThat(realtime.visitors()).isEqualTo(12));
    }

    @Test
    void explainsARejectedApiKeyWithoutRepeatingIt() {
        answerJson("/api/v1/sites", 401, "{\"error\": \"missing or invalid API key\"}");

        assertThatThrownBy(() -> client.site(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.UNAUTHORIZED))
                .hasMessageContaining("API 密钥")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void tellsAnUnknownSiteFromAnAddressThatIsNotTinytraffic() {
        answerJson("/api/v1/sites/example.com/realtime", 404, "{\"error\": \"site not found\"}");
        answer("/api/v1/sites/example.com/aggregate", 404, "text/html", "<html><body>404 Not Found</body></html>");

        assertThatThrownBy(() -> client.realtime(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.NOT_FOUND))
                .hasMessageContaining("example.com");
        assertThatThrownBy(() -> client.aggregate(connection(true), query()).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.NOT_FOUND))
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void mapsTheOtherErrorAnswers() {
        answerJson("/api/v1/sites/example.com/aggregate", 400, "{\"error\": \"unknown period \\\"1y\\\"\"}");
        answerJson("/api/v1/sites/example.com/timeseries", 429, "{\"error\": \"rate limit exceeded\"}");
        answerJson("/api/v1/sites/example.com/realtime", 500, "{\"error\": \"internal error\"}");

        assertThatThrownBy(() -> client.aggregate(connection(true), query()).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.INVALID_REQUEST))
                .hasMessageContaining("unknown period");
        assertThatThrownBy(() -> client.timeseries(connection(true), query()).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.RATE_LIMITED));
        assertThatThrownBy(() -> client.realtime(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.BAD_RESPONSE))
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void rejectsAnAnswerThatIsNotTinytrafficJson() {
        answer("/api/v1/sites", 200, "text/html", "<html><body>Welcome to nginx</body></html>");
        answerJson("/api/v1/sites/example.com/realtime", 200, "{\"visitors\": \"many\"}");

        assertThatThrownBy(() -> client.site(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.BAD_RESPONSE));
        assertThatThrownBy(() -> client.realtime(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.BAD_RESPONSE));
    }

    @Test
    void neverFollowsARedirectSoTheKeyStaysWithTheConfiguredServer() {
        server.createContext("/api/v1/sites", exchange -> {
            record(exchange);
            exchange.getResponseHeaders()
                    .set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        answerJson("/elsewhere", 200, "[]");

        assertThatThrownBy(() -> client.site(connection(true)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.BAD_RESPONSE))
                .hasMessageContaining("重定向");
        assertThat(requests).extracting(Seen::path).containsExactly("/api/v1/sites");
    }

    @Test
    void reportsAServerThatIsNotThere() {
        var connection = connection(true);
        server.stop(0);

        assertThatThrownBy(() -> client.site(connection).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.UNREACHABLE));
    }

    @Test
    void refusesALoopbackLiteralUnlessPrivateNetworksAreAllowed() {
        answerJson("/api/v1/sites", 200, "[]");

        assertThatThrownBy(() -> client.site(connection("127.0.0.1", false)).block(WAIT))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(Kind.BLOCKED_ADDRESS));
        assertThat(requests).isEmpty();
    }

    @Test
    void refusesAHostNameThatResolvesToTheLoopbackAddress() {
        answerJson("/api/v1/sites", 200, "[]");

        // "localhost" is not an IP literal, so this is caught when the name is resolved.
        assertThatThrownBy(() -> client.site(connection("localhost", false)).block(WAIT))
                .isInstanceOf(TinytrafficException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void sendsAnEventOnBehalfOfAVisitor() {
        answer("/api/event", 202, "text/plain", "");
        var event = new Event(
                "pageview",
                "https://example.com/archives/hello?utm_source=RSS",
                "example.com",
                "https://reader.example/");

        client.sendEvent(connection(true), event, "Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0", "203.0.113.9")
                .block(WAIT);

        assertThat(requests).singleElement().satisfies(seen -> {
            assertThat(seen.method()).isEqualTo("POST");
            assertThat(seen.path()).isEqualTo("/api/event");
            assertThat(seen.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(seen.userAgent()).isEqualTo("Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0");
            assertThat(seen.forwardedFor()).isEqualTo("203.0.113.9");
            assertThat(seen.body())
                    .contains("\"name\":\"pageview\"")
                    .contains("\"url\":\"https://example.com/archives/hello?utm_source=RSS\"")
                    .contains("\"domain\":\"example.com\"")
                    .contains("\"referrer\":\"https://reader.example/\"");
        });
    }

    @Test
    void sendsAnEventWithoutAKeyAndThenForwardsNoVisitorAddress() {
        answer("/api/event", 202, "text/plain", "");
        var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var connection = new Connection(base, "example.com", null, true);
        var event = new Event("pageview", "https://example.com/", "example.com", null);

        client.sendEvent(connection, event, "Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0", "203.0.113.9")
                .block(WAIT);

        assertThat(requests).singleElement().satisfies(seen -> {
            assertThat(seen.authorization()).isNull();
            assertThat(seen.forwardedFor()).isNull();
            assertThat(seen.body()).doesNotContain("referrer");
        });
    }
}
