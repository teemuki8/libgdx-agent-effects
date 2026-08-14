package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.EffectCapabilities;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.FidelityClassification;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparer;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonResult;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderQualificationResult;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderTargetProfile;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Objects;

/** Real-GL compilation, preview, and optional reference qualification for imported shaders. */
public final class ShaderImportQualifier {
    private final EffectsLimits limits;
    private final Thread ownerThread = Thread.currentThread();
    private final ImportedMaterialAdapter adapter;
    private final EffectCompiler compiler;
    private final PixelComparer comparer = new PixelComparer();

    /** Creates a render-thread-confined qualifier with finite effect limits. */
    public ShaderImportQualifier(EffectsLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        adapter = new ImportedMaterialAdapter(limits);
        compiler = new EffectCompiler(limits);
    }

    /**
     * Compiles and renders one target, promoting fidelity only when the supplied reference passes.
     */
    public ShaderQualificationResult qualify(
            ShaderImportResult imported,
            ShaderTargetProfile target,
            List<UniformBinding> bindings,
            int width,
            int height,
            RgbaImage reference,
            PixelComparisonSpec comparisonSpec) {
        requireOwnerThread();
        if ((reference == null) != (comparisonSpec == null)) {
            throw new IllegalArgumentException(
                    "reference and comparisonSpec must either both be present or both be absent");
        }
        EffectDescription effect = adapter.adapt(imported, target, bindings, width, height);
        EffectCapabilities capabilities = observedCapabilities();
        try (CompiledEffect compiled = compiler.compile(effect)) {
            if (!compiled.compiled()) {
                return new ShaderQualificationResult(target, effect.shader(), capabilities,
                        compiled.diagnostic(), null, null, FidelityClassification.UNSUPPORTED);
            }
            RgbaImage preview;
            try (PreviewRenderer renderer = new PreviewRenderer(limits)) {
                preview = renderer.render(effect);
            }
            PixelComparisonResult comparison = reference == null ? null
                    : comparer.compare(reference, preview, comparisonSpec, limits);
            FidelityClassification fidelity = fidelity(imported.fidelity(), comparison);
            return new ShaderQualificationResult(target, effect.shader(), capabilities,
                    compiled.diagnostic(), preview, comparison, fidelity);
        }
    }

    private static EffectCapabilities observedCapabilities() {
        GLVersion version = Gdx.graphics.getGLVersion();
        EffectCapabilities.Profile profile = switch (version.getType()) {
            case OpenGL -> EffectCapabilities.Profile.DESKTOP_OPENGL;
            case GLES -> EffectCapabilities.Profile.OPENGL_ES;
            case WebGL -> EffectCapabilities.Profile.WEBGL;
            case NONE -> EffectCapabilities.Profile.UNKNOWN;
        };
        IntBuffer maximum = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, maximum);
        return new EffectCapabilities(version.getMajorVersion(), version.getMinorVersion(),
                Math.max(1, maximum.get(0)), Gdx.gl30 != null, profile);
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                    "ShaderImportQualifier must be used on its owning render thread");
        }
    }

    private static FidelityClassification fidelity(
            FidelityClassification imported, PixelComparisonResult comparison) {
        if (imported == FidelityClassification.APPROXIMATED) {
            return FidelityClassification.APPROXIMATED;
        }
        if (comparison != null && comparison.pass()) {
            return FidelityClassification.VISUALLY_QUALIFIED;
        }
        return FidelityClassification.UNQUALIFIED;
    }
}
