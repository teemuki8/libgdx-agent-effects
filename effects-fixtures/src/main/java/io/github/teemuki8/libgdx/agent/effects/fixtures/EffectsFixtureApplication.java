package io.github.teemuki8.libgdx.agent.effects.fixtures;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparer;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonResult;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.libgdx.CompiledEffect;
import io.github.teemuki8.libgdx.agent.effects.libgdx.DefaultVertexShader;
import io.github.teemuki8.libgdx.agent.effects.libgdx.EffectCompiler;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewPngWriter;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewRenderer;
import java.util.List;

/** Deterministic LWJGL3 qualification scenario; never published. */
public final class EffectsFixtureApplication {

    private EffectsFixtureApplication() {}

    /** Runs the full compile -> preview -> compare -> PNG path; true iff every assertion holds. */
    public static boolean runScenario() {
        EffectsLimits limits = EffectsLimits.developmentDefaults();
        EffectCompiler compiler = new EffectCompiler(limits);
        PreviewRenderer renderer = new PreviewRenderer(limits);

        ShaderSource good = new ShaderSource(DefaultVertexShader.SOURCE,
            "void main(){gl_FragColor=vec4(1.0,0.0,0.0,1.0);}");
        EffectDescription red = new EffectDescription("red", good, List.of(), 64, 64, 0f);

        ShaderSource bad = new ShaderSource(DefaultVertexShader.SOURCE,
            "void main(){ this is not valid glsl }");
        EffectDescription broken = new EffectDescription("broken", bad, List.of(), 64, 64, 0f);

        CompiledEffect redCompiled = compiler.compile(red);
        if (!redCompiled.compiled()) {
            return false;
        }
        redCompiled.close();

        CompiledEffect brokenCompiled = compiler.compile(broken);
        if (brokenCompiled.compiled() || brokenCompiled.program() != null) {
            return false;
        }
        if (brokenCompiled.diagnostic().messages().isEmpty()) {
            return false;
        }

        RgbaImage a = renderer.render(red);
        RgbaImage b = renderer.render(red);
        PixelComparer comparer = new PixelComparer();
        PixelComparisonResult self = comparer.compare(a, b,
            new PixelComparisonSpec(0, List.of(), List.of()), limits);
        if (!self.pass()) {
            return false;
        }

        RgbaImage blue = RgbaImage.solid(64, 64, 0xff0000ff);
        PixelComparisonResult diverged = comparer.compare(a, blue,
            new PixelComparisonSpec(0, List.of(), List.of()), limits);
        if (diverged.pass()) {
            return false;
        }

        byte[] png = new PreviewPngWriter().write(a);
        return png.length >= 8;
    }
}
