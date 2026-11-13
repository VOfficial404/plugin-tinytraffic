package io.github.vofficial404.tinytraffic;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TinytrafficUrlsTest {

    @Test
    void baseUriDropsTrailingSlashesAndWhitespace() {
        assertThat(TinytrafficUrls.baseUri("  https://stats.example.com//  "))
                .contains(URI.create("https://stats.example.com"));
        assertThat(TinytrafficUrls.baseUri("http://10.0.0.5:8080")).contains(URI.create("http://10.0.0.5:8080"));
        assertThat(TinytrafficUrls.baseUri("https://example.com/stats/"))
                .contains(URI.create("https://example.com/stats"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "stats.example.com",
                "//stats.example.com",
                "ftp://stats.example.com",
                "javascript:alert(1)",
                "https://",
                "https://user:pass@stats.example.com",
                "https://stats.example.com?x=1",
                "https://stats.example.com#x",
                "https://stats.example.com/a b"
            })
    void baseUriRejectsAnythingButAPlainHttpAddress(String raw) {
        assertThat(TinytrafficUrls.baseUri(raw)).isEmpty();
    }

    @Test
    void baseUriRejectsNull() {
        assertThat(TinytrafficUrls.baseUri(null)).isEmpty();
    }

    @Test
    void scriptReferenceAcceptsAbsoluteUrlsAndSitePaths() {
        assertThat(TinytrafficUrls.scriptReference(" /stats/js/script.js ")).contains("/stats/js/script.js");
        assertThat(TinytrafficUrls.scriptReference("https://cdn.example.com/s.js"))
                .contains("https://cdn.example.com/s.js");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "//evil.example/s.js",
                "/\\evil.example/s.js",
                "s.js",
                "javascript:alert(1)",
                "data:text/javascript,alert(1)",
                "https://user@cdn.example.com/s.js"
            })
    void scriptReferenceRejectsOtherForms(String raw) {
        assertThat(TinytrafficUrls.scriptReference(raw)).isEmpty();
    }

    @Test
    void normalizeDomainMirrorsTinytraffic() {
        assertThat(TinytrafficUrls.normalizeDomain(" HTTPS://www.Example.com/path?q#h "))
                .isEqualTo("example.com");
        assertThat(TinytrafficUrls.normalizeDomain("blog.example.com")).isEqualTo("blog.example.com");
        assertThat(TinytrafficUrls.normalizeDomain("localhost:8090")).isEqualTo("localhost:8090");
        assertThat(TinytrafficUrls.normalizeDomain("  ")).isNull();
        assertThat(TinytrafficUrls.normalizeDomain(null)).isNull();
    }
}
