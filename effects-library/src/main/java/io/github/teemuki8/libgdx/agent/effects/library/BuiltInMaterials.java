package io.github.teemuki8.libgdx.agent.effects.library;

import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.BlendMode;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.util.List;

/** Original reusable material definitions shipped with the effect library. */
public final class BuiltInMaterials {
    private static final String FULLSCREEN_VERTEX = """
            attribute vec2 a_position;
            attribute vec2 a_texCoord0;
            varying vec2 v_uv;
            void main(){v_uv=a_texCoord0;gl_Position=vec4(a_position,0.0,1.0);}
            """;
    private static final String COLOR_VERTEX = """
            attribute vec2 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            varying vec4 v_color;
            void main(){v_color=a_color;gl_Position=vec4(a_position,0.0,1.0);}
            """;
    private static final String COLOR_FRAGMENT =
            "varying vec4 v_color;void main(){gl_FragColor=v_color;}";
    private static final String HEADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform sampler2D u_source;
            uniform vec2 u_resolution;
            uniform float u_time;
            uniform float u_intensity;
            """;
    private static final String DAMAGE_PULSE_FRAGMENT = HEADER + "void main(){"
            + "vec2 uv=gl_FragCoord.xy/u_resolution; vec4 c=texture2D(u_source,uv);"
            + "float d=distance(uv,vec2(0.5)); float pulse=0.65+0.35*sin(u_time*5.0);"
            + "float edge=smoothstep(0.18,0.72,d);"
            + "vec3 hurt=vec3(c.r+0.65*edge*pulse,c.g*(1.0-0.45*edge),"
            + "c.b*(1.0-0.55*edge)); gl_FragColor=vec4(mix(c.rgb,hurt,u_intensity),c.a);}\n";
    private static final String NEON_EDGES_FRAGMENT = HEADER + "void main(){"
            + "vec2 uv=gl_FragCoord.xy/u_resolution; vec2 px=1.0/u_resolution;"
            + "vec3 c=texture2D(u_source,uv).rgb;"
            + "vec3 x=texture2D(u_source,uv+vec2(px.x,0.0)).rgb;"
            + "vec3 y=texture2D(u_source,uv+vec2(0.0,px.y)).rgb;"
            + "float e=length(c-x)+length(c-y);"
            + "vec3 neon=vec3(e*0.5,e*1.8,e*2.4)+c*0.25;"
            + "gl_FragColor=vec4(mix(c,neon,u_intensity),1.0);}\n";

    /** Animated damage-edge pulse with ready-to-use default uniforms. */
    public static Material2dDefinition damagePulse() {
        return fullscreen("damage-pulse", DAMAGE_PULSE_FRAGMENT, 0.72f, 0.5f);
    }

    /** Aqua neon edge extraction with ready-to-use default uniforms. */
    public static Material2dDefinition neonEdges() {
        return fullscreen("neon-edges", NEON_EDGES_FRAGMENT, 0.78f, 0f);
    }

    /** Vertex-colored additive material shared by bundled geometry effects. */
    public static Material2dDefinition coloredGeometry(String name) {
        return new Material2dDefinition(name,
                new ShaderSource(COLOR_VERTEX, COLOR_FRAGMENT),
                BlendMode.ADDITIVE, List.of(), List.of());
    }

    private static Material2dDefinition fullscreen(String name, String fragment,
            float intensity, float timeSeconds) {
        return new Material2dDefinition(name,
                new ShaderSource(FULLSCREEN_VERTEX, fragment), BlendMode.NORMAL,
                List.of(
                        new UniformBinding("u_resolution", new UniformValue.Vec2(32f, 32f)),
                        new UniformBinding("u_time", new UniformValue.Float(timeSeconds)),
                        new UniformBinding("u_intensity", new UniformValue.Float(intensity))),
                List.of(new AssetKey("source")));
    }

    private BuiltInMaterials() {}
}
