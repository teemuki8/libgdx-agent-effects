package io.github.teemuki8.libgdx.agent.effects.showcase;

import java.util.List;

/** Stable catalog of practical and artistic shaders rendered by the showcase. */
public final class ShowcasePresets {

    private static final String HEADER = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
        + "uniform sampler2D u_source;\nuniform vec2 u_resolution;\n"
        + "uniform float u_time;\nuniform float u_intensity;\n";

    private static final List<ShowcasePreset> ALL = List.of(
        preset("Damage Pulse", "damage-pulse", ShowcasePreset.Group.PRACTICAL, 0.72f, true,
            "vec2 uv=gl_FragCoord.xy/u_resolution; vec4 c=texture2D(u_source,uv);"
            + "float d=distance(uv,vec2(0.5)); float pulse=0.65+0.35*sin(u_time*5.0);"
            + "float edge=smoothstep(0.18,0.72,d);"
            + "vec3 hurt=vec3(c.r+0.65*edge*pulse,c.g*(1.0-0.45*edge),"
            + "c.b*(1.0-0.55*edge)); gl_FragColor=vec4(mix(c.rgb,hurt,u_intensity),c.a);"),
        preset("Underwater Distortion", "underwater", ShowcasePreset.Group.PRACTICAL, 0.65f, true,
            "vec2 uv=gl_FragCoord.xy/u_resolution;"
            + "uv.x+=sin(uv.y*34.0+u_time*2.4)*0.012*u_intensity;"
            + "uv.y+=cos(uv.x*22.0-u_time*1.7)*0.007*u_intensity;"
            + "vec4 c=texture2D(u_source,uv); vec3 aqua=vec3(c.r*0.58,c.g*0.9,c.b*1.25);"
            + "gl_FragColor=vec4(mix(c.rgb,aqua,u_intensity*0.72),c.a);"),
        preset("Pixelation", "pixelation", ShowcasePreset.Group.PRACTICAL, 0.68f, false,
            "vec2 uv=gl_FragCoord.xy/u_resolution; float cells=mix(160.0,24.0,u_intensity);"
            + "vec2 p=floor(uv*cells)/cells+vec2(0.5/cells);"
            + "gl_FragColor=texture2D(u_source,p);"),
        preset("CRT Display", "crt-display", ShowcasePreset.Group.PRACTICAL, 0.58f, true,
            "vec2 uv=gl_FragCoord.xy/u_resolution; vec2 p=uv*2.0-1.0;"
            + "uv=0.5+p*(1.0+dot(p,p)*0.055*u_intensity)*0.5;"
            + "vec4 c=texture2D(u_source,uv); float scan=0.78+0.22*sin(gl_FragCoord.y*3.14159);"
            + "float flicker=0.97+0.03*sin(u_time*19.0);"
            + "gl_FragColor=vec4(c.rgb*mix(1.0,scan*flicker,u_intensity),c.a);"),
        preset("Neon Edges", "neon-edges", ShowcasePreset.Group.FLASHY, 0.78f, false,
            "vec2 uv=gl_FragCoord.xy/u_resolution; vec2 px=1.0/u_resolution;"
            + "vec3 c=texture2D(u_source,uv).rgb; vec3 x=texture2D(u_source,uv+vec2(px.x,0.0)).rgb;"
            + "vec3 y=texture2D(u_source,uv+vec2(0.0,px.y)).rgb;"
            + "float e=length(c-x)+length(c-y); vec3 neon=vec3(e*0.5,e*1.8,e*2.4)+c*0.25;"
            + "gl_FragColor=vec4(mix(c,neon,u_intensity),1.0);"),
        preset("Chromatic Shockwave", "chromatic-shockwave", ShowcasePreset.Group.FLASHY,
            0.82f, true,
            "vec2 uv=gl_FragCoord.xy/u_resolution; vec2 delta=uv-vec2(0.5); float d=length(delta);"
            + "float radius=0.12+mod(u_time*0.16,0.58);"
            + "float ring=exp(-abs(d-radius)*95.0)*u_intensity;"
            + "vec2 dir=delta/max(d,0.001); vec2 off=dir*ring*0.025;"
            + "float r=texture2D(u_source,uv+off).r; float g=texture2D(u_source,uv).g;"
            + "float b=texture2D(u_source,uv-off).b;"
            + "gl_FragColor=vec4(vec3(r,g,b)+ring*vec3(0.32,0.08,0.4),1.0);"));

    /** Returns all presets in stable UI order. */
    public static List<ShowcasePreset> all() {
        return ALL;
    }

    private static ShowcasePreset preset(String name, String slug, ShowcasePreset.Group group,
            float intensity, boolean animated, String body) {
        return new ShowcasePreset(name, slug, group, HEADER + "void main(){" + body + "}\n",
            intensity, animated);
    }

    private ShowcasePresets() {}
}
