package io.github.vofficial404.tinytraffic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebInputException;

class StatsQueryTest {

    private static MultiValueMap<String, String> params(String... keyValues) {
        var params = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    @Test
    void defaultsToThirtyDays() {
        var query = StatsQuery.from(params());

        assertThat(query.period()).isEqualTo("30d");
        assertThat(query.toQueryString()).isEqualTo("period=30d");
    }

    @Test
    void acceptsEveryPeriodOfTinytraffic() {
        for (var period :
                List.of("today", "yesterday", "24h", "7d", "30d", "90d", "month", "lastmonth", "12mo", "all")) {
            assertThat(StatsQuery.from(params("period", period)).toQueryString())
                    .isEqualTo("period=" + period);
        }
    }

    @Test
    void passesACustomRangeAndInterval() {
        var query = StatsQuery.from(
                params("period", "custom", "from", "2026-10-01", "to", "2026-10-31", "interval", "day"));

        assertThat(query.toQueryString()).isEqualTo("period=custom&from=2026-10-01&to=2026-10-31&interval=day");
    }

    @Test
    void dropsDatesOutsideACustomPeriod() {
        var query = StatsQuery.from(params("period", "7d", "from", "2026-10-01", "to", "not a date"));

        assertThat(query.toQueryString()).isEqualTo("period=7d");
    }

    @Test
    void encodesFiltersSoThatTheyArriveUnchanged() {
        var query = StatsQuery.from(params(
                "period",
                "7d",
                "filter",
                "page:/blog/*",
                "filter",
                "source:Hacker News",
                "filter",
                "utm_term:a+b&c=d#e"));

        assertThat(query.filters())
                .containsExactly(
                        new StatsQuery.Filter("page", "/blog/*"),
                        new StatsQuery.Filter("source", "Hacker News"),
                        new StatsQuery.Filter("utm_term", "a+b&c=d#e"));
        assertThat(query.toQueryString())
                .isEqualTo("period=7d&f=page%3A%2Fblog%2F*&f=source%3AHacker+News&f=utm_term%3Aa%2Bb%26c%3Dd%23e");
    }

    @Test
    void keepsAnEmptyFilterValueWhichMeansNone() {
        var query = StatsQuery.from(params("filter", "source:"));

        assertThat(query.filters()).containsExactly(new StatsQuery.Filter("source", ""));
        assertThat(query.toQueryString()).isEqualTo("period=30d&f=source%3A");
    }

    @Test
    void keepsColonsInsideAFilterValue() {
        var query = StatsQuery.from(params("filter", "referrer_url:example.com/a:b"));

        assertThat(query.filters()).containsExactly(new StatsQuery.Filter("referrer_url", "example.com/a:b"));
    }

    @Test
    void rejectsWhatTinytrafficWouldNotUnderstand() {
        assertThatThrownBy(() -> StatsQuery.from(params("period", "1y"))).isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("interval", "minute")))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("filter", "nonsense:1")))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("filter", "no-separator")))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("filter", "page:" + "x".repeat(501))))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void rejectsAnIncompleteOrReversedCustomRange() {
        assertThatThrownBy(() -> StatsQuery.from(params("period", "custom")))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("period", "custom", "from", "2026-10-01")))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("period", "custom", "from", "2026-13-01", "to", "2026-10-31")))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> StatsQuery.from(params("period", "custom", "from", "2026-10-31", "to", "2026-10-01")))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void rejectsTooManyFilters() {
        var params = new LinkedMultiValueMap<String, String>();
        params.put("filter", Collections.nCopies(11, "page:/"));

        assertThatThrownBy(() -> StatsQuery.from(params)).isInstanceOf(ServerWebInputException.class);
    }
}
