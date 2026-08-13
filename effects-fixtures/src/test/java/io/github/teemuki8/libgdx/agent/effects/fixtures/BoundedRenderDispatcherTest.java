package io.github.teemuki8.libgdx.agent.effects.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BoundedRenderDispatcherTest {
    @Test
    void canceledQueuedOperationDoesNotRun() {
        Queue<Runnable> queued = new ArrayDeque<>();
        BoundedRenderDispatcher dispatcher =
            new BoundedRenderDispatcher(Thread.currentThread(), queued::add, 1);
        AtomicBoolean invoked = new AtomicBoolean();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            CompletionStage<Boolean> result = CompletableFuture.supplyAsync(
                () -> dispatcher.submit(() -> invoked.compareAndSet(false, true)), worker).join();
            assertTrue(result.toCompletableFuture().cancel(false));
        }
        queued.remove().run();
        assertFalse(invoked.get(), "canceled GL work must not execute later");
    }

    @Test
    void rejectsWorkBeyondThePendingLimit() {
        Queue<Runnable> queued = new ArrayDeque<>();
        BoundedRenderDispatcher dispatcher =
            new BoundedRenderDispatcher(Thread.currentThread(), queued::add, 1);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            CompletionStage<Boolean> first = CompletableFuture.supplyAsync(
                () -> dispatcher.submit(() -> true), worker).join();
            CompletionStage<Boolean> rejected = CompletableFuture.supplyAsync(
                () -> dispatcher.submit(() -> true), worker).join();
            CompletionException failure = assertThrows(CompletionException.class,
                () -> rejected.toCompletableFuture().join());
            EffectsException effectsFailure = (EffectsException) failure.getCause();
            assertEquals(EffectsException.Kind.LIMIT_EXCEEDED, effectsFailure.kind());
            first.toCompletableFuture().cancel(false);
        }
        queued.remove().run();
    }

    @Test
    void successfulCancellationAlwaysWinsBeforeOperationStart() throws Exception {
        for (int attempt = 0; attempt < 1_000; attempt++) {
            Queue<Runnable> queued = new ArrayDeque<>();
            BoundedRenderDispatcher dispatcher =
                new BoundedRenderDispatcher(Thread.currentThread(), queued::add, 1);
            AtomicBoolean invoked = new AtomicBoolean();
            AtomicReference<CompletionStage<Boolean>> submitted = new AtomicReference<>();
            try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
                submitted.set(CompletableFuture.supplyAsync(
                    () -> dispatcher.submit(() -> invoked.compareAndSet(false, true)), worker).join());
                Thread runner = Thread.ofVirtual().start(queued.remove());
                boolean canceled = submitted.get().toCompletableFuture().cancel(false);
                runner.join();
                if (canceled) {
                    assertFalse(invoked.get(),
                        "a successfully canceled operation must not begin GL work");
                }
            }
        }
    }

    @Test
    void closeCancelsQueuedWorkAndRejectsLaterSubmissions() {
        Queue<Runnable> queued = new ArrayDeque<>();
        BoundedRenderDispatcher dispatcher =
            new BoundedRenderDispatcher(Thread.currentThread(), queued::add, 1);
        CompletionStage<Boolean> submitted;
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            submitted = CompletableFuture.supplyAsync(
                () -> dispatcher.submit(() -> true), worker).join();
        }

        dispatcher.close();

        assertTrue(submitted.toCompletableFuture().isCancelled());
        CompletionException failure = assertThrows(CompletionException.class,
            () -> dispatcher.submit(() -> true).toCompletableFuture().join());
        assertTrue(failure.getCause() instanceof IllegalStateException);
        queued.remove().run();
    }

    @Test
    void closeRejectsTheWrongThread() {
        BoundedRenderDispatcher dispatcher =
            new BoundedRenderDispatcher(Thread.currentThread(), ignored -> { }, 1);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            CompletionException failure = assertThrows(CompletionException.class,
                () -> CompletableFuture.runAsync(dispatcher::close, worker).join());
            EffectsException effectsFailure = (EffectsException) failure.getCause();
            assertEquals(EffectsException.Kind.WRONG_THREAD, effectsFailure.kind());
        }
    }
}
