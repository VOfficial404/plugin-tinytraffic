package io.github.vofficial404.tinytraffic.client;

import io.github.vofficial404.tinytraffic.client.TinytrafficException.Kind;
import io.netty.util.NetUtil;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.apache.commons.lang3.StringUtils;

/**
 * Checks the target of an outbound request before any credentials are attached to it.
 *
 * <p>Host names are checked when they are resolved, by the HTTP client of
 * {@link run.halo.app.infra.utils.HttpSecurityUtils#secureHttpClient()}. An address typed as an IP literal never goes
 * through a resolver, so it is checked here.
 */
final class OutboundTargets {

    static final String BLOCKED_MESSAGE =
            "tinytraffic 地址指向内网或本机，默认不允许访问。如果 tinytraffic 确实部署在内网，请在插件设置的「数据面板」中开启「允许访问内网地址」。";

    private OutboundTargets() {}

    /** @throws TinytrafficException when {@code uri} is an IP literal in a private, local or special-use range */
    static void check(URI uri, boolean allowPrivateNetwork) {
        if (allowPrivateNetwork) {
            return;
        }
        var host = StringUtils.strip(uri.getHost(), "[]");
        if (host == null || !(NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host))) {
            return;
        }
        InetAddress address;
        try {
            // Never queries DNS: the host is a literal.
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new TinytrafficException(Kind.BLOCKED_ADDRESS, BLOCKED_MESSAGE, e);
        }
        if (isPrivate(address)) {
            throw new TinytrafficException(Kind.BLOCKED_ADDRESS, BLOCKED_MESSAGE);
        }
    }

    /** Mirrors the ranges {@code HttpSecurityUtils} refuses to connect to. */
    static boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        var bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 // "this network"
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
                    || (first == 198 && (second == 18 || second == 19)) // benchmarking
                    || first >= 240; // reserved and broadcast
        }
        // Unique local addresses, fc00::/7.
        return address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc;
    }
}
