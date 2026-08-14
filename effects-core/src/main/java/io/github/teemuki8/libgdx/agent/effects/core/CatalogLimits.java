package io.github.teemuki8.libgdx.agent.effects.core;

/** Configured bounds for catalog metadata, entries, variants, and results. */
public record CatalogLimits(int maxEntries, int maxVariantsPerEntry,
        int maxTagsPerEntry, int maxResults, int maxTextChars) {
    private static final int HARD_CAP = 1024 * 1024;

    public CatalogLimits {
        requireBounded(maxEntries, "maxEntries");
        requireBounded(maxVariantsPerEntry, "maxVariantsPerEntry");
        requireBounded(maxTagsPerEntry, "maxTagsPerEntry");
        requireBounded(maxResults, "maxResults");
        requireBounded(maxTextChars, "maxTextChars");
    }

    /** Conservative defaults for local development catalogs. */
    public static CatalogLimits developmentDefaults() {
        return new CatalogLimits(1024, 16, 32, 256, 1024);
    }

    private static void requireBounded(int value, String name) {
        if (value <= 0 || value > HARD_CAP) {
            throw new IllegalArgumentException(name + " must be within hard bounds");
        }
    }
}
