package io.github.teemuki8.libgdx.agent.effects.mcp;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.PixelComparisonSpec;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderDiagnostic;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsBackend;
import io.github.teemuki8.libgdx.agent.effects.protocol.EffectsProtocolService;
import io.github.teemuki8.libgdx.agent.effects.protocol.Results;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EffectsToolHandlerTest {
    @Test
    void invokesWiredBackendOnItsOwnerThread() {
        Thread owner = Thread.currentThread();
        AtomicReference<Thread> invoked = new AtomicReference<>();
        EffectsBackend backend = new EffectsBackend() {
            @Override public Results.CompileResult compile(String effectName) {
                invoked.set(Thread.currentThread());
                return new Results.CompileResult(effectName,
                    new ShaderDiagnostic(true, List.of(), List.of(), List.of(), ""));
            }

            @Override public Results.PreviewResult preview(String effectName) {
                throw new UnsupportedOperationException();
            }

            @Override public Results.CompareResult compare(String referenceName,
                    String actualName, PixelComparisonSpec spec) {
                throw new UnsupportedOperationException();
            }
        };
        EffectDescription effect = new EffectDescription("red",
            new ShaderSource("void main(){}", "void main(){}"), List.of(), 1, 1, 0f);
        EffectsProtocolService protocol = new EffectsProtocolService()
            .declare(effect).backend(backend);
        try (EffectsToolHandler handler = new EffectsToolHandler(protocol)) {
            handler.handle(McpSchema.CallToolRequest.builder()
                .name("effect_compile").arguments(Map.of("effectName", "red")).build()).block();
        }
        assertSame(owner, invoked.get(),
            "render backend must run on the thread that owns its resources");
    }
}
