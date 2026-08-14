package io.github.teemuki8.libgdx.agent.effects.importer.godot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportRequest;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSemantic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class GodotCanvasImporterTest {

    private static final String DIRECT = """
            shader_type canvas_item;
            uniform float intensity : hint_range(0.0, 1.0) = 0.5;
            void fragment() {
                vec4 sampled = texture(TEXTURE, UV);
                COLOR = vec4(sampled.rgb * intensity, sampled.a);
            }
            """;

    @Test
    void translatesDirectCanvasMaterialForBothProfiles() {
        ShaderImportResult result = importer().importShader(new ShaderImportRequest(
                "direct", DIRECT,
                List.of(ShaderTargetProfile.GLSL_ES_100, ShaderTargetProfile.GLSL_ES_300)));

        assertEquals(FidelityClassification.STRUCTURALLY_EQUIVALENT, result.fidelity());
        assertEquals(BlendMode.NORMAL, result.material().blendMode());
        assertEquals(2, result.generatedShaders().size());
        assertEquals(List.of(ShaderSemantic.UV, ShaderSemantic.VERTEX_COLOR,
                ShaderSemantic.SOURCE_TEXTURE), result.requiredSemantics());
        assertEquals(List.of("TEXTURE", "UV", "COLOR"), result.featureMappings().stream()
                .map(mapping -> mapping.sourceFeature()).toList());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals("intensity", result.material().uniforms().getFirst().name());

        String es100 = result.generatedShaders().get(0).shader().fragment();
        assertTrue(es100.contains("uniform sampler2D u_source;"));
        assertTrue(es100.contains("texture2D(u_source, v_uv)"));
        assertTrue(es100.contains("gl_FragColor ="));
        String es300 = result.generatedShaders().get(1).shader().fragment();
        assertTrue(es300.startsWith("#version 300 es"));
        assertTrue(es300.contains("out vec4 godot_fragColor;"));
        assertTrue(es300.contains("godot_fragColor ="));
        assertFalse(result.generatedShaders().get(0).sourceMappings().isEmpty());
    }

    @Test
    void mapsBuiltinsAndReportsPremultipliedBlendApproximation() {
        String source = """
                shader_type canvas_item;
                render_mode blend_premul_alpha, unshaded;
                uniform sampler2D screen_texture : hint_screen_texture;
                void vertex() { VERTEX.x += sin(TIME); }
                void fragment() {
                    COLOR = texture(screen_texture, SCREEN_UV)
                            + vec4(TEXTURE_PIXEL_SIZE, 0.0, 0.0);
                }
                """;
        ShaderImportResult result = importer().importShader(request("approx", source));

        assertEquals(FidelityClassification.APPROXIMATED, result.fidelity());
        assertEquals(BlendMode.NORMAL, result.material().blendMode());
        assertTrue(result.requiredSemantics().containsAll(List.of(
                ShaderSemantic.POSITION, ShaderSemantic.TIME,
                ShaderSemantic.SOURCE_TEXEL_SIZE, ShaderSemantic.SCREEN_UV,
                ShaderSemantic.SCREEN_TEXTURE)));
        assertEquals("GODOT_BLEND_PREMULTIPLIED_APPROXIMATION",
                result.diagnostics().getFirst().code());
        String vertex = result.generatedShaders().getFirst().shader().vertex();
        assertTrue(vertex.contains("godot_vertex.x += sin(u_time)"));
        String fragment = result.generatedShaders().getFirst().shader().fragment();
        assertTrue(fragment.contains("texture2D(u_screenTexture, godot_screenUv)"));
    }

    @Test
    void returnsStructuredUnsupportedResultForUnsafeOrUnavailableFeatures() {
        for (String source : List.of(
                "shader_type canvas_item; void light() {}",
                "shader_type canvas_item; void fragment(){ COLOR=texture_sdf(UV); }",
                "shader_type canvas_item; void fragment(){ for(;;){ COLOR=vec4(1.0); } }",
                "shader_type spatial; void fragment() {}")) {
            ShaderImportResult result = importer().importShader(request("unsupported", source));
            assertEquals(FidelityClassification.UNSUPPORTED, result.fidelity());
            assertNull(result.material());
            assertTrue(result.generatedShaders().isEmpty());
            assertEquals(1, result.diagnostics().size());
        }
    }

    @Test
    void enforcesGeneratedOutputLimit() {
        ImportLimits limits = new ImportLimits(
                4096, 32, 4096, 32, 128, 64, 32, 512, 2048, 32, 64);
        ShaderImportResult result = new GodotCanvasImporter(limits)
                .importShader(request("bounded", DIRECT));

        assertEquals(FidelityClassification.UNSUPPORTED, result.fidelity());
        assertEquals("GENERATED_SOURCE_LIMIT_EXCEEDED", result.diagnostics().getFirst().code());
    }

    private static GodotCanvasImporter importer() {
        return new GodotCanvasImporter(ImportLimits.developmentDefaults());
    }

    private static ShaderImportRequest request(String name, String source) {
        return new ShaderImportRequest(name, source, List.of(ShaderTargetProfile.GLSL_ES_100));
    }
}
