package io.github.vofficial404.tinytraffic.client;

import lombok.Getter;

/**
 * A request to tinytraffic that could not be made or did not succeed. The message is shown to the user in the Console,
 * so it must never contain the API key.
 */
@Getter
public class TinytrafficException extends RuntimeException {

    public enum Kind {
        /** The plugin settings are incomplete. */
        NOT_CONFIGURED,
        /** The server address points at a private or local network, which is not allowed by default. */
        BLOCKED_ADDRESS,
        /** tinytraffic rejected the request parameters. */
        INVALID_REQUEST,
        /** tinytraffic rejected the API key. */
        UNAUTHORIZED,
        /** tinytraffic has no site for the configured domain, or the address is not a tinytraffic server. */
        NOT_FOUND,
        /** The API key has used up its requests for this minute. */
        RATE_LIMITED,
        /** The server did not answer in time. */
        TIMEOUT,
        /** The server could not be reached. */
        UNREACHABLE,
        /** The server answered with something this plugin does not understand. */
        BAD_RESPONSE
    }

    private final Kind kind;

    public TinytrafficException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public TinytrafficException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }
}
