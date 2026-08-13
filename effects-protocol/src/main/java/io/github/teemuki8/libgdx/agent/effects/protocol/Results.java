package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import java.util.List;
import java.util.Objects;

/** Closed result records. */
public final class Results {

    private Results() {}

    public record CompileResult(String effectName, ShaderDiagnostic diagnostic) {
        public CompileResult {
            Objects.requireNonNull(effectName, "effectName");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    public record PreviewResult(String effectName, String artifactRef, int width, int height) {
        public PreviewResult {
            Objects.requireNonNull(effectName, "effectName");
            Objects.requireNonNull(artifactRef, "artifactRef");
        }
    }

    public record CompareResult(PixelComparisonResult result) {
        public CompareResult {
            Objects.requireNonNull(result, "result");
        }
    }

    public record ListResult(List<String> effectNames) {
        public ListResult {
            effectNames = List.copyOf(effectNames);
        }
    }

    public record CapabilitiesResult(List<String> tools) {
        public CapabilitiesResult {
            tools = List.copyOf(tools);
        }
    }
}
