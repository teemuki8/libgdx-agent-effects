package io.github.teemuki8.libgdx.agent.effects.protocol;

import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import java.util.HashSet;
import java.util.List;
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

    public record ImportGodotCanvasRequest(
            String name, String source, List<ShaderTargetProfile> targetProfiles) {
        public ImportGodotCanvasRequest {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(source, "source");
            targetProfiles = List.copyOf(targetProfiles);
            if (name.isBlank() || name.length() > EffectsProtocol.MAX_IDENTIFIER_CHARS
                    || source.isBlank()
                    || source.length() > EffectsProtocol.MAX_SHADER_IMPORT_SOURCE_CHARS
                    || targetProfiles.isEmpty() || targetProfiles.size() > 2
                    || new HashSet<>(targetProfiles).size() != targetProfiles.size()) {
                throw new IllegalArgumentException("invalid bounded Godot import request");
            }
        }
    }
}
