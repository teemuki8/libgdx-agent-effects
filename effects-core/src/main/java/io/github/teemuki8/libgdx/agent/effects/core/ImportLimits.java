package io.github.teemuki8.libgdx.agent.effects.core;

/** Configured bounds for parsing, translating, and reporting imported shaders. */
public record ImportLimits(
        int maxSourceChars,
        int maxGeneratedChars,
        int maxTokens,
        int maxAstDepth,
        int maxDeclarations,
        int maxFunctions,
        int maxParameters,
        int maxStatements,
        int maxExpressionNodes,
        int maxDiagnostics,
        int maxFeatureMappings) {

    private static final int HARD_TEXT_CAP = 1024 * 1024;
    private static final int HARD_COUNT_CAP = 1024 * 1024;

    public ImportLimits {
        requireRange(maxSourceChars, HARD_TEXT_CAP, "maxSourceChars");
        requireRange(maxGeneratedChars, HARD_TEXT_CAP, "maxGeneratedChars");
        requireRange(maxTokens, HARD_COUNT_CAP, "maxTokens");
        requireRange(maxAstDepth, 1024, "maxAstDepth");
        requireRange(maxDeclarations, HARD_COUNT_CAP, "maxDeclarations");
        requireRange(maxFunctions, HARD_COUNT_CAP, "maxFunctions");
        requireRange(maxParameters, HARD_COUNT_CAP, "maxParameters");
        requireRange(maxStatements, HARD_COUNT_CAP, "maxStatements");
        requireRange(maxExpressionNodes, HARD_COUNT_CAP, "maxExpressionNodes");
        requireRange(maxDiagnostics, HARD_COUNT_CAP, "maxDiagnostics");
        requireRange(maxFeatureMappings, HARD_COUNT_CAP, "maxFeatureMappings");
    }

    /** Conservative defaults for local development and agent-facing imports. */
    public static ImportLimits developmentDefaults() {
        return new ImportLimits(
                64 * 1024, 128 * 1024, 32 * 1024, 64, 1024, 256,
                128, 8192, 32 * 1024, 256, 1024);
    }

    private static void requireRange(int value, int maximum, String name) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be within hard bounds");
        }
    }
}
