package io.github.teemuki8.libgdx.agent.effects.fixtures;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.ImportLimits;
import io.github.teemuki8.libgdx.agent.effects.core.Material2dDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.ParticleImportResult;
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
import io.github.teemuki8.libgdx.agent.effects.importer.libgdx.FlameParticleImporter;
import io.github.teemuki8.libgdx.agent.effects.importer.libgdx.LibgdxParticleImporter;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsImportBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Requests;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final LibgdxParticleImporter libgdxParticleImporter =
            new LibgdxParticleImporter(ImportLimits.developmentDefaults());
    private final FlameParticleImporter flameParticleImporter =
            new FlameParticleImporter(ImportLimits.developmentDefaults());
    private final Map<String, Results.SnapshotSummaryResult> snapshotSummaries =
            new LinkedHashMap<>();

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

    @Override public CompletionStage<Results.ImportParticleResult> importParticle(
            Requests.ImportParticleRequest request) {
        EffectDefinition declared = protocol.definition(request.materialName());
        if (!(declared instanceof Material2dDefinition material)) {
            return CompletableFuture.failedFuture(new EffectsException(
                    EffectsException.Kind.INVALID_IMPORT,
                    "particle material is not an application-declared 2D material"));
        }
        Map<String, AssetKey> mappings = request.assetMappings().entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> new AssetKey(entry.getValue())));
        ParticleImportResult imported = switch (request.format()) {
            case LIBGDX_2D -> libgdxParticleImporter.importParticle(request.source(),
                    request.name(), request.anchorName(), material, mappings);
            case FLAME -> flameParticleImporter.importParticle(request.source(),
                    request.anchorName(), material, mappings);
        };
        return CompletableFuture.completedFuture(new Results.ImportParticleResult(
                imported.definition().name(), imported.fidelity(),
                imported.definition().capacity(), imported.diagnostics()));
    }

    /** Registers an immutable application snapshot for the closed summary tool. */
    public synchronized EffectsFixtureBackend registerSnapshot(EffectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Results.EffectSummaryResult declared = protocol.effectSummary(snapshot.definitionName());
        if (declared == null) {
            throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                    "snapshot definition is not declared");
        }
        int elements = Math.addExact(snapshot.anchors().size(), snapshot.events().size());
        if (elements > limits.maxDefinitionNodes()
                || (!snapshotSummaries.containsKey(snapshot.definitionName())
                        && snapshotSummaries.size() >= limits.maxRuntimeInstances())) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "snapshot summary exceeds configured limits");
        }
        snapshotSummaries.put(snapshot.definitionName(), new Results.SnapshotSummaryResult(
                snapshot.definitionName(), declared.family(), snapshot.stepIndex(), elements, 0L,
                List.of("anchors=" + snapshot.anchors().size(),
                        "events=" + snapshot.events().size())));
        return this;
    }

    @Override public synchronized CompletionStage<Results.SnapshotSummaryResult> snapshotSummary(
            String effectName) {
        Results.SnapshotSummaryResult summary = snapshotSummaries.get(effectName);
        if (summary == null) {
            return CompletableFuture.failedFuture(new EffectsException(
                    EffectsException.Kind.UNSUPPORTED_FEATURE,
                    "no immutable snapshot is registered for effect: " + effectName));
        }
        return CompletableFuture.completedFuture(summary);
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
