package io.github.teemuki8.libgdx.agent.effects.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveAttribute;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveUniform;
import io.github.teemuki8.libgdx.agent.effects.core.DiagnosticMessage;
import io.github.teemuki8.libgdx.agent.effects.core.DiagnosticSeverity;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectsJsonTest {
    @Test
    void roundTripsCompileResult() throws Exception {
        ObjectMapper mapper = EffectsJson.mapper();
        ShaderDiagnostic d = new ShaderDiagnostic(true,
            List.of(new DiagnosticMessage(DiagnosticSeverity.ERROR, 3, "bad")),
            List.of(new ActiveUniform("u_time", "float", 1)),
            List.of(new ActiveAttribute("a_position", "vec4")), "ok");
        Results.CompileResult result = new Results.CompileResult("red", d);
        String json = mapper.writeValueAsString(result);
        Results.CompileResult back = mapper.readValue(json, Results.CompileResult.class);
        assertEquals("red", back.effectName());
        assertEquals(3, back.diagnostic().messages().get(0).line());
    }

    @Test
    void rejectsUnknownFields() {
        ObjectMapper mapper = EffectsJson.mapper();
        String json = "{\"effectName\":\"red\",\"diagnostic\":null,\"nope\":1}";
        assertThrows(Exception.class, () ->
            mapper.readValue(json, Results.CompileResult.class));
    }
}
