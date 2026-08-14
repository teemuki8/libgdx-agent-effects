package io.github.teemuki8.libgdx.agent.effects.fixtures;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderImportRequest;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparer;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonResult;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.libgdx.CompiledEffect;
import io.github.teemuki8.libgdx.agent.effects.libgdx.EffectCompiler;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewPngWriter;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewRenderer;
import io.github.teemuki8.libgdx.agent.effects.importer.godot.GodotCanvasImporter;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsImportBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Requests;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Real-GL {@link EffectsBackend} over the libgdx layer, wired into an
 * {@link EffectsProtocolService}. Compile/render operations invoked on the owner thread complete
 * inline; off-owner calls are posted through the application-owned render queue. Queued work is
 * bounded and canceled work is skipped before it touches GL. Close this backend on its owner thread.
 */
public final class EffectsFixtureBackend implements EffectsBackend, EffectsImportBackend,
        AutoCloseable {
    private static final int MAX_PENDING_RENDER_OPERATIONS = 64;

    private final EffectsProtocolService protocol;
    private final EffectsLimits limits;
    private final BoundedRenderDispatcher dispatcher;
    private final EffectCompiler compiler;
    private final PreviewRenderer renderer;
    private final PixelComparer comparer = new PixelComparer();
    private final PreviewPngWriter pngWriter = new PreviewPngWriter();
    private final GodotCanvasImporter importer =
            new GodotCanvasImporter(ImportLimits.developmentDefaults());

    public EffectsFixtureBackend(EffectsProtocolService protocol, EffectsLimits limits) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.dispatcher = new BoundedRenderDispatcher(Thread.currentThread(),
            com.badlogic.gdx.Gdx.app::postRunnable, MAX_PENDING_RENDER_OPERATIONS);
        this.compiler = new EffectCompiler(limits);
        this.renderer = new PreviewRenderer(limits);
    }

    @Override public CompletionStage<Results.CompileResult> compile(String effectName) {
        return dispatcher.submit(() -> compileOnRenderThread(effectName));
    }

    private Results.CompileResult compileOnRenderThread(String effectName) {
        CompiledEffect compiled = compiler.compile(effectOf(effectName));
        try {
            return new Results.CompileResult(effectName, compiled.diagnostic());
        } finally {
            compiled.close();
        }
    }

    @Override public CompletionStage<Results.PreviewResult> preview(String effectName) {
        return dispatcher.submit(() -> previewOnRenderThread(effectName));
    }

    private Results.PreviewResult previewOnRenderThread(String effectName) {
        RgbaImage image = renderer.render(effectOf(effectName));
        byte[] png = pngWriter.write(image);
        return new Results.PreviewResult(effectName, sha256Hex(png),
            image.width(), image.height());
    }

    @Override public CompletionStage<Results.CompareResult> compare(
            String referenceName, String actualName,
            PixelComparisonSpec spec) {
        return dispatcher.submit(() -> compareOnRenderThread(referenceName, actualName, spec));
    }

    @Override public CompletionStage<Results.ImportShaderResult> importGodotCanvas(
            Requests.ImportGodotCanvasRequest request) {
        ShaderImportRequest coreRequest = new ShaderImportRequest(
                request.name(), request.source(), request.targetProfiles());
        return CompletableFuture.completedFuture(
                new Results.ImportShaderResult(importer.importShader(coreRequest)));
    }

    private Results.CompareResult compareOnRenderThread(String referenceName, String actualName,
            PixelComparisonSpec spec) {
        RgbaImage reference = renderer.render(effectOf(referenceName));
        RgbaImage actual = renderer.render(effectOf(actualName));
        PixelComparisonResult result = comparer.compare(reference, actual, spec, limits);
        return new Results.CompareResult(result);
    }

    @Override public void close() {
        if (dispatcher.close()) {
            renderer.close();
        }
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
