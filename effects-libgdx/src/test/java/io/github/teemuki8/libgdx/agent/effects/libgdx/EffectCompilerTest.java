package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectCompilerTest {

    private static final String VERTEX =
        "attribute vec4 a_position;\nvoid main(){gl_Position=a_position;}";

    @Test
    void compilesValidShaderIntoStructuredDiagnostic() throws Exception {
        GdxTestHost.run(() -> {
            EffectCompiler compiler = new EffectCompiler(EffectsLimits.developmentDefaults());
            ShaderSource src = new ShaderSource(VERTEX,
                "uniform float u_time;\nvoid main(){gl_FragColor=vec4(u_time,0.0,0.0,1.0);}");
            EffectDescription e = new EffectDescription("red", src, List.of(), 128, 128, 0f);
            CompiledEffect compiled = compiler.compile(e);
            assertTrue(compiled.compiled(), compiled.diagnostic().infoLog());
            assertTrue(compiled.diagnostic().uniforms().stream()
                .anyMatch(u -> u.name().equals("u_time")), "uniform u_time must be reported");
            compiled.close();
        });
    }

    @Test
    void reportsCompileFailureStructured() throws Exception {
        GdxTestHost.run(() -> {
            EffectCompiler compiler = new EffectCompiler(EffectsLimits.developmentDefaults());
            ShaderSource src = new ShaderSource(VERTEX, "void main(){ this is not valid glsl }");
            EffectDescription e = new EffectDescription("bad", src, List.of(), 128, 128, 0f);
            CompiledEffect compiled = compiler.compile(e);
            assertFalse(compiled.compiled());
            assertFalse(compiled.diagnostic().messages().isEmpty());
            assertNull(compiled.program(), "failed compile must not retain a program");
            compiled.close();
        });
    }
}
