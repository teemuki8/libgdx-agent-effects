package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/**
 * Render backend seam for the compile/preview/compare tools.
 *
 * <p>The MCP server ({@code effects-mcp}) has no render layer; whoever wires a server into a
 * process with a real GL context supplies an {@code EffectsBackend} (e.g. the LWJGL3 fixture)
 * through {@link EffectsProtocolService#backend(EffectsBackend)}. Without one the tools answer
 * a typed {@code NOT_AVAILABLE} error.
 *
 * <p>Returned stages may complete on the backend's render thread. Callers must move result
 * encoding, transport, and other non-GL continuation work off that thread. Implementations should
 * skip queued work when its stage is canceled before execution and reject work beyond their
 * configured pending-operation bound.
 */
public interface EffectsBackend {

    /** Compiles the named declared effect into structured diagnostics asynchronously. */
    CompletionStage<Results.CompileResult> compile(String effectName);

    /** Renders the named declared effect and returns a bounded artifact receipt asynchronously. */
    CompletionStage<Results.PreviewResult> preview(String effectName);

    /** Renders two declared effects and compares their pixels asynchronously. */
    CompletionStage<Results.CompareResult> compare(String referenceName, String actualName,
            PixelComparisonSpec spec);

    /** Summarizes a named live effect snapshot without exposing mutable runtime objects. */
    default CompletionStage<Results.SnapshotSummaryResult> snapshotSummary(String effectName) {
        return CompletableFuture.failedFuture(new EffectsException(
                EffectsException.Kind.UNSUPPORTED_FEATURE,
                "snapshot summaries are not implemented by this backend"));
    }
}
