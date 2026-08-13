package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import java.util.Objects;

/** Render-thread-only handle to a compiled shader plus its structured diagnostic. */
public final class CompiledEffect implements AutoCloseable {

    private final ShaderDiagnostic diagnostic;
    private final ShaderProgram program; // null when the shader failed to compile

    CompiledEffect(ShaderDiagnostic diagnostic, ShaderProgram program) {
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        this.program = program;
    }

    public ShaderDiagnostic diagnostic() {
        return diagnostic;
    }

    /** The compiled program, or {@code null} when {@link #compiled()} is false. */
    public ShaderProgram program() {
        return program;
    }

    public boolean compiled() {
        return diagnostic.compiled();
    }

    @Override public void close() {
        if (program != null) {
            program.dispose();
        }
    }
}
