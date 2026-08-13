package io.github.teemuki8.libgdx.agent.effects.mcp;

import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;

/** Stdio-only production entry point. */
public final class Main {
    private Main() {}

    /** Starts one MCP connection and exits when stdin closes. */
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("effects MCP accepts no command-line arguments");
        }
        try (EffectsMcpServer server = EffectsMcpServer.open(
                new EffectsProtocolService(), System.in, System.out)) {
            server.awaitTermination();
        }
    }
}
