package io.github.teemuki8.libgdx.agent.effects.fixtures;

import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded handoff from request workers to one application-owned render thread. */
final class BoundedRenderDispatcher {
    private final Thread ownerThread;
    private final Consumer<Runnable> postRunnable;
    private final int maxPending;
    private final AtomicInteger pending = new AtomicInteger();

    BoundedRenderDispatcher(Thread ownerThread, Consumer<Runnable> postRunnable, int maxPending) {
        this.ownerThread = Objects.requireNonNull(ownerThread, "ownerThread");
        this.postRunnable = Objects.requireNonNull(postRunnable, "postRunnable");
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive");
        }
        this.maxPending = maxPending;
    }

    <T> CompletionStage<T> submit(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (Thread.currentThread() == ownerThread) {
            return completed(operation);
        }
        if (pending.incrementAndGet() > maxPending) {
            pending.decrementAndGet();
            return CompletableFuture.failedFuture(new EffectsException(
                EffectsException.Kind.LIMIT_EXCEEDED, "render operation queue is full"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            postRunnable.accept(() -> runQueued(operation, result));
        } catch (RuntimeException failure) {
            pending.decrementAndGet();
            result.completeExceptionally(failure);
        }
        return result;
    }

    private <T> void runQueued(Supplier<T> operation, CompletableFuture<T> result) {
        try {
            if (!result.isCancelled()) {
                result.complete(operation.get());
            }
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        } finally {
            pending.decrementAndGet();
        }
    }

    private static <T> CompletionStage<T> completed(Supplier<T> operation) {
        try {
            return CompletableFuture.completedFuture(operation.get());
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
