package io.github.vofficial404.tinytraffic.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vofficial404.tinytraffic.setting.BasicSetting;
import io.github.vofficial404.tinytraffic.setting.TrackerSetting;
import org.junit.jupiter.api.Test;

class TrackerScriptTest {

    private static final TrackerSetting DEFAULTS = new TrackerSetting(null, null, null, null, null, null, null);

    @Test
    void rendersTheSnippetFromTheTinytrafficReadme() {
        var basic = new BasicSetting("https://stats.example.com", "example.com");

        assertThat(TrackerScript.render(basic, DEFAULTS))
                .contains(
                        "<script defer data-domain=\"example.com\" src=\"https://stats.example.com/js/script.js\"></script>\n");
    }

    @Test
    void leavesOutTheDomainWhenNoneIsConfigured() {
        var basic = new BasicSetting(" https://stats.example.com/ ", " ");

        assertThat(TrackerScript.render(basic, DEFAULTS))
                .contains("<script defer src=\"https://stats.example.com/js/script.js\"></script>\n");
    }

    @Test
    void normalizesTheDomainLikeTinytrafficDoes() {
        var basic = new BasicSetting("https://stats.example.com", "https://WWW.Example.com/blog");

        assertThat(TrackerScript.render(basic, DEFAULTS).orElseThrow()).contains(" data-domain=\"example.com\" ");
    }

    @Test
    void addsTheOptionalAttributes() {
        var basic = new BasicSetting("https://stats.example.com", "example.com");
        var tracker = new TrackerSetting(true, false, true, false, "/tt/js/script.js", "/tt/api/event", null);

        assertThat(TrackerScript.render(basic, tracker))
                .contains("<script defer data-domain=\"example.com\" data-api=\"/tt/api/event\" data-hash"
                        + " data-outbound=\"false\" src=\"/tt/js/script.js\"></script>\n");
    }

    @Test
    void rendersNothingWhenSwitchedOff() {
        var basic = new BasicSetting("https://stats.example.com", "example.com");
        var tracker = new TrackerSetting(false, null, null, null, null, null, null);

        assertThat(TrackerScript.render(basic, tracker)).isEmpty();
    }

    @Test
    void rendersNothingWithoutAUsableAddress() {
        assertThat(TrackerScript.render(new BasicSetting(null, "example.com"), DEFAULTS))
                .isEmpty();
        assertThat(TrackerScript.render(new BasicSetting("", null), DEFAULTS)).isEmpty();
        assertThat(TrackerScript.render(new BasicSetting("stats.example.com", null), DEFAULTS))
                .isEmpty();
        assertThat(TrackerScript.render(new BasicSetting("javascript:alert(1)", null), DEFAULTS))
                .isEmpty();
        assertThat(TrackerScript.render(new BasicSetting("ftp://stats.example.com", null), DEFAULTS))
                .isEmpty();
    }

    @Test
    void ignoresAnInvalidScriptAddressAndFallsBackToTheServer() {
        var basic = new BasicSetting("https://stats.example.com", null);
        var tracker = new TrackerSetting(null, null, null, null, "//evil.example/x.js", "javascript:alert(1)", null);

        assertThat(TrackerScript.render(basic, tracker))
                .contains("<script defer src=\"https://stats.example.com/js/script.js\"></script>\n");
    }

    @Test
    void escapesAttributeValues() {
        var basic = new BasicSetting("https://stats.example.com", "a\"><script>alert(1)</script>");
        var tracker = new TrackerSetting(null, null, null, null, "/s.js?a=1&b=\"2\"", null, null);

        var script = TrackerScript.render(basic, tracker).orElseThrow();

        assertThat(script)
                .startsWith("<script defer data-domain=\"a&quot;&gt;&lt;script&gt;alert(1)&lt;\" ")
                .contains(" src=\"/s.js?a=1&amp;b=&quot;2&quot;\"")
                .endsWith("></script>\n");
        // Exactly one opening and one closing tag: nothing broke out of an attribute.
        assertThat(script.split("<script", -1)).hasSize(2);
        assertThat(script.split("</script>", -1)).hasSize(2);
    }
}
