package io.github.teemuki8.libgdx.agent.effects.protocol;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Closed, bounded Jackson mapper for all protocol JSON. */
public final class EffectsJson {

    private static final ObjectMapper MAPPER = build();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper build() {
        ObjectMapper m = new ObjectMapper();
        m.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        m.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxNestingDepth(32)
                .maxStringLength(16_384)
                .maxNumberLength(128)
                .build());
        return m;
    }

    private EffectsJson() {}
}
