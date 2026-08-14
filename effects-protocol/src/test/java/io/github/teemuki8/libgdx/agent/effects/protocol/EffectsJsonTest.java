package io.github.teemuki8.libgdx.agent.effects.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveAttribute;
import io.github.teemuki8.libgdx.agent.effects.core.ActiveUniform;
import io.github.teemuki8.libgdx.agent.effects.core.DiagnosticMessage;
import io.github.teemuki8.libgdx.agent.effects.core.DiagnosticSeverity;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectFamily;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import java.util.List;
import java.util.Map;
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

    @Test
    void roundTripsClosedGodotImportRequestAndRejectsUnknownFields() throws Exception {
        ObjectMapper mapper = EffectsJson.mapper();
        Requests.ImportGodotCanvasRequest request = new Requests.ImportGodotCanvasRequest(
                "glow", "shader_type canvas_item; void fragment(){}",
                List.of(ShaderTargetProfile.GLSL_ES_100));

        String json = mapper.writeValueAsString(request);
        Requests.ImportGodotCanvasRequest decoded = mapper.readValue(
                json, Requests.ImportGodotCanvasRequest.class);

        assertEquals(request, decoded);
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"name\":\"x\",\"source\":\"s\",\"targetProfiles\":[\"GLSL_ES_100\"],"
                        + "\"path\":\"/tmp/x\"}",
                Requests.ImportGodotCanvasRequest.class));
    }

    @Test
    void roundTripsEveryClosedEffectFamilySummary() throws Exception {
        ObjectMapper mapper = EffectsJson.mapper();
        for (EffectFamily family : EffectFamily.values()) {
            Results.EffectSummaryResult summary = new Results.EffectSummaryResult(
                    "effect", family, 8, List.of("bounded"));
            String json = mapper.writeValueAsString(summary);
            assertEquals(summary, mapper.readValue(json, Results.EffectSummaryResult.class));
        }
    }

    @Test
    void particleImportRequestIsVersionedClosedAndContainsMappingsNotPaths() throws Exception {
        ObjectMapper mapper = EffectsJson.mapper();
        Requests.ImportParticleRequest request = new Requests.ImportParticleRequest("1",
                ParticleSourceFormat.LIBGDX_2D, "sparks", "source", "emitter",
                "particle-material", Map.of("spark.png", "spark_region"));
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request),
                Requests.ImportParticleRequest.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"schemaVersion\":\"2\",\"format\":\"LIBGDX_2D\","
                        + "\"name\":\"x\",\"source\":\"s\",\"anchorName\":\"a\","
                        + "\"materialName\":\"m\",\"assetMappings\":{}}",
                Requests.ImportParticleRequest.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"schemaVersion\":\"1\",\"format\":\"LIBGDX_2D\","
                        + "\"name\":\"x\",\"source\":\"s\",\"anchorName\":\"a\","
                        + "\"materialName\":\"m\",\"assetMappings\":{},"
                        + "\"path\":\"/tmp/x\"}", Requests.ImportParticleRequest.class));
    }
    @Test
    void catalogSearchRequestIsClosedBoundedAndNormalizesTags() throws Exception {
        ObjectMapper mapper = EffectsJson.mapper();
        EffectCapabilities target = new EffectCapabilities(
                4, 6, 16384, true, EffectCapabilities.Profile.DESKTOP_OPENGL);
        Requests.CatalogSearchRequest request = new Requests.CatalogSearchRequest(
                target, EffectFamily.TRAIL, List.of("space", "glow"), 12);

        Requests.CatalogSearchRequest decoded = mapper.readValue(
                mapper.writeValueAsString(request), Requests.CatalogSearchRequest.class);

        assertEquals(List.of("glow", "space"), decoded.tags());
        assertThrows(IllegalArgumentException.class, () -> new Requests.CatalogSearchRequest(
                target, null, List.of("glow", "glow"), 12));
        assertThrows(IllegalArgumentException.class, () -> new Requests.CatalogSearchRequest(
                target, null, List.of(), EffectsProtocol.MAX_CATALOG_RESULTS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Requests.CatalogSearchRequest(
                target, null, List.of("x".repeat(EffectsProtocol.MAX_IDENTIFIER_CHARS + 1)), 1));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"target\":{\"glMajor\":4,\"glMinor\":6,\"maxTextureSize\":16384,"
                        + "\"floatTextures\":true,\"profile\":\"DESKTOP_OPENGL\"},"
                        + "\"family\":\"TRAIL\",\"tags\":[],\"limit\":12,\"path\":\"x\"}",
                Requests.CatalogSearchRequest.class));
    }

    @Test
    void catalogLookupRequestIsClosedAndRequiresQualifiedTarget() throws Exception {
        ObjectMapper mapper = EffectsJson.mapper();
        EffectCapabilities target = new EffectCapabilities(
                3, 2, 8192, false, EffectCapabilities.Profile.DESKTOP_OPENGL);
        Requests.CatalogLookupRequest request = new Requests.CatalogLookupRequest(
                "ship-trail", target);

        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request),
                Requests.CatalogLookupRequest.class));
        assertThrows(IllegalArgumentException.class, () -> new Requests.CatalogLookupRequest(
                "ship-trail", new EffectCapabilities(3, 2, 8192, false)));
        assertThrows(IllegalArgumentException.class, () -> new Requests.CatalogLookupRequest(
                "x".repeat(EffectsProtocol.MAX_IDENTIFIER_CHARS + 1), target));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"id\":\"ship-trail\",\"target\":{\"glMajor\":3,\"glMinor\":2,"
                        + "\"maxTextureSize\":8192,\"floatTextures\":false,"
                        + "\"profile\":\"DESKTOP_OPENGL\"},\"extra\":true}",
                Requests.CatalogLookupRequest.class));
    }
}
