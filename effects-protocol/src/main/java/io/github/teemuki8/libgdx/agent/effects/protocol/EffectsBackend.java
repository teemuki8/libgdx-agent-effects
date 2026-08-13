package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import java.util.concurrent.CompletionStage;

/**
 * Render backend seam for the compile/preview/compare tools.
 *
 * <p>The MCP server ({@code effects-mcp}) has no render layer; whoever wires a server into a
 * process with a real GL context supplies an {@code EffectsBackend} (e.g. the LWJGL3 fixture)
 * through {@link EffectsProtocolService#backend(EffectsBackend)}. Without one the tools answer
 * a typed {@code NOT_AVAILABLE} error.
 */
public interface EffectsBackend {

    /** Compiles the named declared effect into structured diagnostics. */
    CompletionStage<Results.CompileResult> compile(String effectName);

    /** Renders the named declared effect and returns a bounded artifact receipt. */
    CompletionStage<Results.PreviewResult> preview(String effectName);

    /** Renders two declared effects and compares their pixels under the given spec. */
    CompletionStage<Results.CompareResult> compare(String referenceName, String actualName,
            PixelComparisonSpec spec);
}
