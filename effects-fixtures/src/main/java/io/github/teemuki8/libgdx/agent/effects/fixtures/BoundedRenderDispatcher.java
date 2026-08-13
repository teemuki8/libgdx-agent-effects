package io.github.teemuki8.libgdx.agent.effects.fixtures;

import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded handoff from request workers to one application-owned render thread. */
final class BoundedRenderDispatcher {
    private final Thread ownerThread;
    private final Consumer<Runnable> postRunnable;
    private final int maxPending;
    private final Set<DispatchFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

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
        if (closed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("render dispatcher is closed"));
        }
        if (Thread.currentThread() == ownerThread) {
            return completed(operation);
        }
        DispatchFuture<T> result = new DispatchFuture<>();
        synchronized (pending) {
            if (closed) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("render dispatcher is closed"));
            }
            if (pending.size() >= maxPending) {
                return CompletableFuture.failedFuture(new EffectsException(
                    EffectsException.Kind.LIMIT_EXCEEDED, "render operation queue is full"));
            }
            pending.add(result);
        }
        try {
            postRunnable.accept(() -> runQueued(operation, result));
        } catch (RuntimeException failure) {
            pending.remove(result);
            result.completeExceptionally(failure);
        }
        return result;
    }

    boolean close() {
        if (Thread.currentThread() != ownerThread) {
            throw new EffectsException(EffectsException.Kind.WRONG_THREAD,
                "render dispatcher must be closed on its owner thread");
        }
        synchronized (pending) {
            if (closed) {
                return false;
            }
            closed = true;
            pending.forEach(future -> future.cancel(false));
            return true;
        }
    }

    private <T> void runQueued(Supplier<T> operation, DispatchFuture<T> result) {
        try {
            if (result.begin()) {
                result.complete(operation.get());
            }
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        } finally {
            result.finish();
            pending.remove(result);
        }
    }

    private static <T> CompletionStage<T> completed(Supplier<T> operation) {
        try {
            return CompletableFuture.completedFuture(operation.get());
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private enum DispatchState { QUEUED, RUNNING, CANCELED, DONE }

    private static final class DispatchFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<DispatchState> state =
            new AtomicReference<>(DispatchState.QUEUED);

        boolean begin() {
            return state.compareAndSet(DispatchState.QUEUED, DispatchState.RUNNING);
        }

        void finish() {
            state.compareAndSet(DispatchState.RUNNING, DispatchState.DONE);
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            return state.compareAndSet(DispatchState.QUEUED, DispatchState.CANCELED)
                && super.cancel(false);
        }
    }
}
