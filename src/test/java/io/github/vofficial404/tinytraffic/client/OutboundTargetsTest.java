package io.github.vofficial404.tinytraffic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutboundTargetsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "127.8.9.10",
                "10.1.2.3",
                "172.16.0.1",
                "172.31.255.255",
                "192.168.1.5",
                "169.254.169.254",
                "100.64.0.1",
                "100.127.255.255",
                "198.18.0.1",
                "0.0.0.0",
                "0.1.2.3",
                "224.0.0.1",
                "240.0.0.1",
                "255.255.255.255",
                "::1",
                "::",
                "fe80::1",
                "fc00::1",
                "fd12:3456:789a::1",
                "ff02::1",
                "::ffff:127.0.0.1",
                "::ffff:192.168.1.5"
            })
    void treatsLocalAndPrivateAddressesAsPrivate(String literal) throws Exception {
        assertThat(OutboundTargets.isPrivate(InetAddress.getByName(literal))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1.1.1.1",
                "8.8.8.8",
                "100.63.255.255",
                "100.128.0.1",
                "172.32.0.1",
                "198.17.255.255",
                "198.20.0.1",
                "2606:4700:4700::1111",
                "2001:4860:4860::8888"
            })
    void treatsPublicAddressesAsPublic(String literal) throws Exception {
        assertThat(OutboundTargets.isPrivate(InetAddress.getByName(literal))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://127.0.0.1:8080/api/v1/sites",
                "http://192.168.1.5/api/v1/sites",
                "https://10.0.0.2/api/v1/sites",
                "http://169.254.169.254/latest/meta-data",
                "http://[::1]:8080/api/v1/sites",
                "http://[fd00::1]/api/v1/sites",
                "http://[::ffff:127.0.0.1]/api/v1/sites"
            })
    void refusesPrivateIpLiterals(String uri) {
        assertThatThrownBy(() -> OutboundTargets.check(URI.create(uri), false))
                .isInstanceOfSatisfying(
                        TinytrafficException.class, e -> assertThat(e.getKind()).isEqualTo(Kind.BLOCKED_ADDRESS));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://1.1.1.1/api/v1/sites",
                "https://[2606:4700:4700::1111]/api/v1/sites",
                // Host names are checked by the HTTP client when they are resolved.
                "https://stats.example.com/api/v1/sites",
                "http://localhost:8080/api/v1/sites"
            })
    void letsPublicLiteralsAndHostNamesThrough(String uri) {
        assertThatCode(() -> OutboundTargets.check(URI.create(uri), false)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8080/api/v1/sites", "http://[::1]/api/v1/sites"})
    void allowsPrivateLiteralsWhenTheAdministratorSaysSo(String uri) {
        assertThatCode(() -> OutboundTargets.check(URI.create(uri), true)).doesNotThrowAnyException();
    }
}
