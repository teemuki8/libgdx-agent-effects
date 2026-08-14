package io.github.teemuki8.libgdx.agent.effects.core;

/** Immutable application-resolved decal transform, normal, order, and tint. */
public record DecalPlacement(long order, float x, float y, float z,
        float normalX, float normalY, float normalZ, float rotationDegrees,
        float r, float g, float b, float a) {
    public DecalPlacement {
        if (!finite(x, y, z, normalX, normalY, normalZ, rotationDegrees, r, g, b, a)) {
            throw new IllegalArgumentException("decal placement values must be finite");
        }
        float normalLengthSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
        if (normalLengthSquared < 0.000001f) {
            throw new IllegalArgumentException("decal normal must be nonzero");
        }
    }

    private static boolean finite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
