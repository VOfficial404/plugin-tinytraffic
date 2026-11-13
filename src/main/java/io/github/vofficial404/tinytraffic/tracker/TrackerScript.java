package io.github.vofficial404.tinytraffic.tracker;

import io.github.vofficial404.tinytraffic.TinytrafficUrls;
import io.github.vofficial404.tinytraffic.setting.BasicSetting;
import io.github.vofficial404.tinytraffic.setting.TrackerSetting;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the tinytraffic tracker tag:
 *
 * <pre>{@code
 * <script defer data-domain="example.com" src="https://stats.example.com/js/script.js"></script>
 * }</pre>
 */
public final class TrackerScript {

    static final String SCRIPT_PATH = "/js/script.js";

    private TrackerScript() {}

    /** @return the tag, or empty when the tracker is switched off or there is no valid script address */
    public static Optional<String> render(BasicSetting basic, TrackerSetting tracker) {
        if (!tracker.isEnabled()) {
            return Optional.empty();
        }
        var src = TinytrafficUrls.scriptReference(tracker.scriptUrl())
                .or(() -> TinytrafficUrls.baseUri(basic.endpoint()).map(base -> base + SCRIPT_PATH));
        if (src.isEmpty()) {
            return Optional.empty();
        }

        var tag = new StringBuilder("<script defer");
        var domain = TinytrafficUrls.normalizeDomain(basic.domain());
        if (domain != null) {
            attribute(tag, "data-domain", domain);
        }
        TinytrafficUrls.scriptReference(tracker.apiEndpoint()).ifPresent(api -> attribute(tag, "data-api", api));
        if (tracker.isHashMode()) {
            tag.append(" data-hash");
        }
        if (!tracker.isOutbound()) {
            attribute(tag, "data-outbound", "false");
        }
        attribute(tag, "src", src.get());
        return Optional.of(tag.append("></script>\n").toString());
    }

    private static void attribute(StringBuilder tag, String name, String value) {
        tag.append(' ')
                .append(name)
                .append("=\"")
                .append(HtmlUtils.htmlEscape(value))
                .append('"');
    }
}
