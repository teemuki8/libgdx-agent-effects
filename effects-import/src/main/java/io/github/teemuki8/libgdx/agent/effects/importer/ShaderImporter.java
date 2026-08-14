package io.github.teemuki8.libgdx.agent.effects.importer;

import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportRequest;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;

/** Translates explicitly supplied bounded shader source without resolving external resources. */
@FunctionalInterface
public interface ShaderImporter {

    /** Parses and translates one source request into immutable shader-import evidence. */
    ShaderImportResult importShader(ShaderImportRequest request);
}
