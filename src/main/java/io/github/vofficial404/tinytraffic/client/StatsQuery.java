package io.github.vofficial404.tinytraffic.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebInputException;

/**
 * The period and filters of a stats request, validated against what tinytraffic accepts so that nothing else is passed
 * on to it.
 *
 * @param period one of {@link #PERIODS}
 * @param from first day of a custom period, {@code YYYY-MM-DD}
 * @param to last day of a custom period, {@code YYYY-MM-DD}
 * @param interval one of {@link #INTERVALS}, or {@code null} to let tinytraffic pick one
 * @param filters filters that must all match
 */
public record StatsQuery(String period, String from, String to, String interval, List<Filter> filters) {

    public static final String DEFAULT_PERIOD = "30d";

    public static final Set<String> PERIODS =
            Set.of("today", "yesterday", "24h", "7d", "30d", "90d", "month", "lastmonth", "12mo", "all", "custom");

    public static final Set<String> INTERVALS = Set.of("hour", "day", "month");

    /** Dimensions a breakdown can be requested for. */
    public static final Set<String> DIMENSIONS = Set.of(
            "page",
            "entry_page",
            "exit_page",
            "source",
            "referrer",
            "referrer_url",
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_content",
            "utm_term",
            "country",
            "region",
            "city",
            "browser",
            "os",
            "device",
            "event");

    private static final int MAX_FILTERS = 10;
    private static final int MAX_FILTER_VALUE_LENGTH = 500;

    /**
     * A filter, written {@code dimension:value} in a request. A {@code *} in the value is a wildcard and an empty value
     * means "none".
     */
    public record Filter(String dimension, String value) {

        static Filter parse(String raw) {
            var separator = raw.indexOf(':');
            if (separator <= 0) {
                throw new ServerWebInputException("筛选条件的格式应为“维度:值”。");
            }
            var dimension = raw.substring(0, separator);
            var value = raw.substring(separator + 1);
            // "hostname" can be filtered on, but tinytraffic offers no breakdown for it.
            if (!DIMENSIONS.contains(dimension) && !dimension.equals("hostname")) {
                throw new ServerWebInputException("不支持的筛选维度：" + StringUtils.abbreviate(dimension, 40));
            }
            if (value.length() > MAX_FILTER_VALUE_LENGTH) {
                throw new ServerWebInputException("筛选值过长。");
            }
            return new Filter(dimension, value);
        }
    }

    /** Reads {@code period}, {@code from}, {@code to}, {@code interval} and repeated {@code filter} parameters. */
    public static StatsQuery from(MultiValueMap<String, String> params) {
        var period = StringUtils.defaultIfBlank(params.getFirst("period"), DEFAULT_PERIOD);
        if (!PERIODS.contains(period)) {
            throw new ServerWebInputException("不支持的时间范围：" + StringUtils.abbreviate(period, 40));
        }

        String from = null;
        String to = null;
        if (period.equals("custom")) {
            var first = parseDate(params.getFirst("from"));
            var last = parseDate(params.getFirst("to"));
            if (first == null || last == null || last.isBefore(first)) {
                throw new ServerWebInputException("自定义时间范围需要 YYYY-MM-DD 格式的开始和结束日期，且结束日期不能早于开始日期。");
            }
            from = first.toString();
            to = last.toString();
        }

        var interval = StringUtils.trimToNull(params.getFirst("interval"));
        if (interval != null && !INTERVALS.contains(interval)) {
            throw new ServerWebInputException("不支持的时间粒度：" + StringUtils.abbreviate(interval, 40));
        }

        var rawFilters = params.getOrDefault("filter", List.of());
        if (rawFilters.size() > MAX_FILTERS) {
            throw new ServerWebInputException("筛选条件过多。");
        }
        var filters = rawFilters.stream().map(Filter::parse).toList();
        return new StatsQuery(period, from, to, interval, filters);
    }

    /** @return the parameters as a tinytraffic query string, e.g. {@code period=7d&f=page%3A%2Fblog%2F*} */
    public String toQueryString() {
        var parts = new ArrayList<String>();
        parts.add("period=" + encode(period));
        if (from != null && to != null) {
            parts.add("from=" + encode(from));
            parts.add("to=" + encode(to));
        }
        if (interval != null) {
            parts.add("interval=" + encode(interval));
        }
        for (var filter : filters) {
            parts.add("f=" + encode(filter.dimension() + ":" + filter.value()));
        }
        return String.join("&", parts);
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
