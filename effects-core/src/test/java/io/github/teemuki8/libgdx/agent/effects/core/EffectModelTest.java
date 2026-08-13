package io.github.teemuki8.libgdx.agent.effects.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EffectModelTest {
    @Test
    void validDescriptionRoundTrips() {
        EffectsLimits limits = EffectsLimits.developmentDefaults();
        ShaderSource src = new ShaderSource("void main(){gl_Position=vec4(0.);}",
            "void main(){gl_FragColor=vec4(1.,0.,0.,1.);}");
        EffectDescription e = new EffectDescription("red", src,
            List.of(new UniformBinding("u_scale", new UniformValue.Float(2f))),
            256, 256, 1.5f);
        assertEquals("red", e.name());
        assertEquals(1, e.uniforms().size());
        assertEquals(1.5f, e.timeSeconds());
        assertDoesNotThrow(() -> e.validate(limits));
    }

    @Test
    void rejectsBlankFragment() {
        assertThrows(IllegalArgumentException.class, () ->
            new ShaderSource("void main(){gl_Position=vec4(0.);}", ""));
    }

    @Test
    void defensiveCopyOfUniformList() {
        ShaderSource src = new ShaderSource("v", "f");
        List<UniformBinding> list = new java.util.ArrayList<>();
        list.add(new UniformBinding("u", new UniformValue.Int(1)));
        EffectDescription e = new EffectDescription("n", src, list, 256, 256, 0f);
        list.clear();
        assertEquals(1, e.uniforms().size());
    }
}
