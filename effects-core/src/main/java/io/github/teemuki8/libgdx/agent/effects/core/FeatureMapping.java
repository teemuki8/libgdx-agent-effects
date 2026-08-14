package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.Objects;

/** One explicit source-feature to target-feature mapping decision. */
public record FeatureMapping(
        String sourceFeature,
        String targetFeature,
        boolean direct,
        SourceSpan sourceSpan) {

    public FeatureMapping {
        Objects.requireNonNull(sourceFeature, "sourceFeature");
        Objects.requireNonNull(targetFeature, "targetFeature");
        Objects.requireNonNull(sourceSpan, "sourceSpan");
        if (sourceFeature.isBlank() || targetFeature.isBlank()) {
            throw new IllegalArgumentException("feature names must not be blank");
        }
    }
}
