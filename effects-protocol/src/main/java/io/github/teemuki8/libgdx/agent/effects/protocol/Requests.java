package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import java.util.Objects;

/** Closed request records. */
public final class Requests {

    private Requests() {}

    public record CompileRequest(String effectName) {
        public CompileRequest {
            Objects.requireNonNull(effectName, "effectName");
        }
    }

    public record PreviewRequest(String effectName) {
        public PreviewRequest {
            Objects.requireNonNull(effectName, "effectName");
        }
    }

    public record CompareRequest(String referenceName, String actualName,
            PixelComparisonSpec spec) {
        public CompareRequest {
            Objects.requireNonNull(referenceName, "referenceName");
            Objects.requireNonNull(actualName, "actualName");
            Objects.requireNonNull(spec, "spec");
        }
    }
}
