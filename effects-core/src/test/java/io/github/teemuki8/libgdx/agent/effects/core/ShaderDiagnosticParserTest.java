package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderDiagnosticParserTest {
    @Test
    void parsesErrorLineAndUniforms() {
        String log = "0:12: 'undeclared': syntax error\n"
            + "ERROR: 0:12: 'foo' : undeclared identifier\n";
        ShaderDiagnostic d = new ShaderDiagnosticParser().parse(false, log,
            List.of(new ActiveUniform("u_time", "float", 1)),
            List.of(new ActiveAttribute("a_position", "vec4")),
            EffectsLimits.developmentDefaults());
        assertFalse(d.compiled());
        assertEquals(2, d.messages().size());
        DiagnosticMessage m = d.messages().get(1);
        assertEquals(DiagnosticSeverity.ERROR, m.severity());
        assertEquals(12, m.line());
        assertEquals("u_time", d.uniforms().get(0).name());
    }

    @Test
    void truncatesLongInfoLog() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= EffectsLimits.developmentDefaults().maxDiagnosticChars(); i++) {
            sb.append('x');
        }
        ShaderDiagnostic d = new ShaderDiagnosticParser().parse(true, sb.toString(),
            List.of(), List.of(), EffectsLimits.developmentDefaults());
        assertEquals(EffectsLimits.developmentDefaults().maxDiagnosticChars(), d.infoLog().length());
    }
}
