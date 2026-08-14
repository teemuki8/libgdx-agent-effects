package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** One normalized effect definition qualified for explicit target capabilities. */
public record EffectCatalogVariant(String id, int preference,
        EffectDefinition definition, List<EffectCapabilities> qualifiedTargets) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int HARD_TARGET_CAP = 1024;
    private static final int HARD_PREFERENCE_CAP = 1024 * 1024;

    public EffectCatalogVariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        qualifiedTargets = List.copyOf(Objects.requireNonNull(
                qualifiedTargets, "qualifiedTargets"));
        if (!ID.matcher(id).matches() || preference < 0
                || preference > HARD_PREFERENCE_CAP) {
            throw new IllegalArgumentException("invalid catalog variant identity or preference");
        }
        if (qualifiedTargets.isEmpty() || qualifiedTargets.size() > HARD_TARGET_CAP) {
            throw new IllegalArgumentException("qualified targets are outside hard bounds");
        }
        Set<EffectCapabilities> unique = new HashSet<>();
        for (EffectCapabilities target : qualifiedTargets) {
            if (target.profile() == EffectCapabilities.Profile.UNKNOWN || !unique.add(target)) {
                throw new IllegalArgumentException(
                        "qualified targets must be unique and explicit");
            }
        }
    }

    /** Whether the supplied actual target satisfies any qualification minimum. */
    public boolean supports(EffectCapabilities actual) {
        Objects.requireNonNull(actual, "actual");
        return qualifiedTargets.stream().anyMatch(actual::satisfies);
    }
}
