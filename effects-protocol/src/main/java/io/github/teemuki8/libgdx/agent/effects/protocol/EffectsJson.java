package io.github.teemuki8.libgdx.agent.effects.protocol;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Closed, bounded Jackson mapper for all protocol JSON. */
public final class EffectsJson {

    private static final ObjectMapper MAPPER = build();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper build() {
        JsonFactory factory = JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder()
                .maxNestingDepth(32)
                .maxStringLength(EffectsProtocol.MAX_SHADER_IMPORT_SOURCE_CHARS)
                .maxNumberLength(128)
                .build())
            .build();
        return JsonMapper.builder(factory)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();
    }

    private EffectsJson() {}
}
