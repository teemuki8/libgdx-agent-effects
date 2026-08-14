package io.github.teemuki8.libgdx.agent.effects.libgdx;

import io.github.teemuki8.libgdx.agent.effects.core.DistortionFieldDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import java.util.Map;
import java.util.Objects;

/** One-pass distortion composition adapter over application-owned scene and vector captures. */
public final class DistortionRenderer implements AutoCloseable {
    private final DistortionFieldDefinition definition;
    private final PostProcessGraphRenderer graphRenderer;

    /** Creates the bounded one-pass graph on the current render thread. */
    public DistortionRenderer(DistortionFieldDefinition definition, EffectsLimits limits) {
        this.definition = Objects.requireNonNull(definition, "definition");
        definition.validate(Objects.requireNonNull(limits, "limits"));
        graphRenderer = new PostProcessGraphRenderer(definition.asGraph(), limits);
    }

    /** Composes borrowed scene and distortion-vector inputs into a borrowed output texture. */
    public PostProcessGraphResult render(SceneCapture scene, SceneCapture vectors,
            int width, int height) {
        return graphRenderer.render(Map.of(definition.sceneInput(), scene,
                definition.vectorInput(), vectors), width, height);
    }

    @Override public void close() {
        graphRenderer.close();
    }
}
