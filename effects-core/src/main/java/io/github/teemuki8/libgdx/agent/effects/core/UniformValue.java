package io.github.teemuki8.libgdx.agent.effects.core;

/** Closed union of effect uniform values. */
public sealed interface UniformValue permits UniformValue.Float, UniformValue.Int,
        UniformValue.Vec2, UniformValue.Vec3, UniformValue.Vec4, UniformValue.Mat4,
        UniformValue.Sampler2d {

    record Float(float value) implements UniformValue {}
    record Int(int value) implements UniformValue {}
    record Vec2(float x, float y) implements UniformValue {}
    record Vec3(float x, float y, float z) implements UniformValue {}
    record Vec4(float x, float y, float z, float w) implements UniformValue {}

    record Sampler2d(RgbaImage image) implements UniformValue {
        public Sampler2d {
            java.util.Objects.requireNonNull(image, "image");
        }
    }

    record Mat4(float[] values) implements UniformValue {
        public Mat4 {
            java.util.Objects.requireNonNull(values, "values");
            if (values.length != 16) {
                throw new IllegalArgumentException("Mat4 needs 16 floats");
            }
            values = values.clone();
        }

        @Override public float[] values() {
            return values.clone();
        }
    }
}
