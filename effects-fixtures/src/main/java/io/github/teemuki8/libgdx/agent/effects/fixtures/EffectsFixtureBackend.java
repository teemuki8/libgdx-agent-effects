package io.github.teemuki8.libgdx.agent.effects.fixtures;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparer;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonResult;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.libgdx.CompiledEffect;
import io.github.teemuki8.libgdx.agent.effects.libgdx.EffectCompiler;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewPngWriter;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewRenderer;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Real-GL {@link EffectsBackend} over the libgdx layer, wired into an
 * {@link EffectsProtocolService}. Render-thread confined: compile/render run on the owning GL
 * thread and fail fast with {@code WRONG_THREAD} otherwise.
 */
public final class EffectsFixtureBackend implements EffectsBackend, AutoCloseable {

    private final EffectsProtocolService protocol;
    private final EffectsLimits limits;
    private final EffectCompiler compiler;
    private final PreviewRenderer renderer;
    private final PixelComparer comparer = new PixelComparer();
    private final PreviewPngWriter pngWriter = new PreviewPngWriter();

    public EffectsFixtureBackend(EffectsProtocolService protocol, EffectsLimits limits) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.compiler = new EffectCompiler(limits);
        this.renderer = new PreviewRenderer(limits);
    }

    @Override public Results.CompileResult compile(String effectName) {
        CompiledEffect compiled = compiler.compile(effectOf(effectName));
        try {
            return new Results.CompileResult(effectName, compiled.diagnostic());
        } finally {
            compiled.close();
        }
    }

    @Override public Results.PreviewResult preview(String effectName) {
        RgbaImage image = renderer.render(effectOf(effectName));
        byte[] png = pngWriter.write(image);
        return new Results.PreviewResult(effectName, sha256Hex(png),
            image.width(), image.height());
    }

    @Override public Results.CompareResult compare(String referenceName, String actualName,
            PixelComparisonSpec spec) {
        RgbaImage reference = renderer.render(effectOf(referenceName));
        RgbaImage actual = renderer.render(effectOf(actualName));
        PixelComparisonResult result = comparer.compare(reference, actual, spec, limits);
        return new Results.CompareResult(result);
    }

    @Override public void close() {
        renderer.close();
    }

    private EffectDescription effectOf(String name) {
        EffectDescription effect = protocol.effect(name);
        if (effect == null) {
            throw new IllegalArgumentException("effect is not declared: " + name);
        }
        return effect;
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
