package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** Application-registered asset identifier; never a caller-selected filesystem path. */
public record AssetKey(String value) {
    private static final int MAX_LENGTH = 256;

    public AssetKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > MAX_LENGTH
                || value.startsWith("/") || value.contains("..")
                || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("invalid registered asset key");
        }
    }
}
