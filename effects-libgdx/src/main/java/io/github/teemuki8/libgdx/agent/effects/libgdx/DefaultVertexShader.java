package io.github.teemuki8.libgdx.agent.effects.libgdx;

/** Passthrough fullscreen-quad vertex shader (positions a {@code vec2 a_position} in clip space). */
public final class DefaultVertexShader {

    public static final String SOURCE =
        "attribute vec2 a_position;\n"
        + "void main() {\n"
        + "    gl_Position = vec4(a_position, 0.0, 1.0);\n"
        + "}\n";

    private DefaultVertexShader() {}
}
