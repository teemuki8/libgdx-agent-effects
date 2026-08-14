package io.github.teemuki8.libgdx.agent.effects.protocol;

import java.util.concurrent.CompletionStage;

/** Separately wired JDK-only shader import backend. */
public interface EffectsImportBackend {

    /** Imports bounded Godot canvas source without registering or persisting it. */
    CompletionStage<Results.ImportShaderResult> importGodotCanvas(
            Requests.ImportGodotCanvasRequest request);
}
