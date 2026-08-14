package io.github.teemuki8.libgdx.agent.effects.protocol;

/** Closed protocol constants. */
public final class EffectsProtocol {
    public static final String SCHEMA_VERSION = "1";
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
    public static final int MAX_IDENTIFIER_CHARS = 256;
    public static final int MAX_SHADER_IMPORT_SOURCE_CHARS = 64 * 1024;

    private EffectsProtocol() {}
}
