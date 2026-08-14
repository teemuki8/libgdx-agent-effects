package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportRequest;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderQualificationResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotCanvasImporter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ShaderImportQualifierTest {
    private static final String DIRECT = """
            shader_type canvas_item;
            uniform float intensity = 0.5;
            void fragment() {
                vec4 sampled = texture(TEXTURE, UV);
                COLOR = vec4(sampled.rgb * intensity, sampled.a);
            }
            """;

    @Test
    void compilesRendersAndQualifiesGeneratedImporterOutputUnderRealGl() throws Exception {
        GdxTestHost.run(() -> {
            ShaderImportResult imported = imported();
            ShaderImportQualifier qualifier = new ShaderImportQualifier(
                    EffectsLimits.developmentDefaults());
            RgbaImage source = RgbaImage.solid(8, 8, 0xff00ff00);
            RgbaImage reference = RgbaImage.solid(16, 16, 0xff008000);

            ShaderQualificationResult result = qualifier.qualify(imported,
                    ShaderTargetProfile.GLSL_ES_100,
                    List.of(new UniformBinding("u_source", new UniformValue.Sampler2d(source))),
                    16, 16, reference, new PixelComparisonSpec(1, List.of(), List.of()));

            assertTrue(result.diagnostic().compiled());
            assertNotNull(result.preview());
            assertTrue(result.comparison().pass());
            assertEquals(FidelityClassification.VISUALLY_QUALIFIED, result.fidelity());
            assertEquals(imported.generatedShaders().getFirst().shader(), result.shader());
            assertEquals(EffectCapabilities.Profile.DESKTOP_OPENGL,
                    result.capabilities().profile());
            com.badlogic.gdx.graphics.glutils.GLVersion version =
                    com.badlogic.gdx.Gdx.graphics.getGLVersion();
            assertEquals(version.getMajorVersion(),
                    result.capabilities().glMajor());
            assertEquals(version.getMinorVersion(), result.capabilities().glMinor());
            assertTrue(result.capabilities().maxTextureSize() > 0);
            assertEquals(com.badlogic.gdx.Gdx.gl30 != null,
                    result.capabilities().floatTextures());
        });
    }

    @Test
    void compileWithoutReferenceIsUnqualifiedAndMissingSamplerIsRejected() throws Exception {
        GdxTestHost.run(() -> {
            ShaderImportQualifier qualifier = new ShaderImportQualifier(
                    EffectsLimits.developmentDefaults());
            ShaderQualificationResult result = qualifier.qualify(imported(),
                    ShaderTargetProfile.GLSL_ES_100,
                    List.of(new UniformBinding("u_source", new UniformValue.Sampler2d(
                            RgbaImage.solid(1, 1, 0xffffffff)))),
                    4, 4, null, null);
            assertNull(result.comparison());
            assertEquals(FidelityClassification.UNQUALIFIED, result.fidelity());

            EffectsException failure = assertThrows(EffectsException.class,
                    () -> qualifier.qualify(imported(), ShaderTargetProfile.GLSL_ES_100,
                            List.of(), 4, 4, null, null));
            assertEquals(EffectsException.Kind.INVALID_EFFECT, failure.kind());
        });
    }

    @Test
    void qualifierIsConfinedToItsOwningRenderThread() throws Exception {
        GdxTestHost.run(() -> {
            ShaderImportQualifier qualifier = new ShaderImportQualifier(
                    EffectsLimits.developmentDefaults());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread other = new Thread(() -> {
                try {
                    qualifier.qualify(imported(), ShaderTargetProfile.GLSL_ES_100,
                            List.of(), 4, 4, null, null);
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
            other.start();
            other.join();
            EffectsException wrongThread = (EffectsException) failure.get();
            assertEquals(EffectsException.Kind.WRONG_THREAD, wrongThread.kind());
        });
    }

    private static ShaderImportResult imported() {
        return new GodotCanvasImporter(ImportLimits.developmentDefaults()).importShader(
                new ShaderImportRequest("qualified", DIRECT,
                        List.of(ShaderTargetProfile.GLSL_ES_100)));
    }
}
