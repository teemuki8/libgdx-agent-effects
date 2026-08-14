package io.github.teemuki8.libgdx.agent.effects.runtime;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDefinition;
import io.github.teemuki8.libgdx.agent.effects.core.EffectSnapshot;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RuntimeLimits;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Factory and capacity owner for explicitly stepped visual-effect instances. */
public final class EffectRuntime implements AutoCloseable {
    private final RuntimeLimits limits;
    private final Set<BasicEffectInstance> instances = new HashSet<>();
    private boolean closed;

    /** Creates an application-owned runtime with finite state and catch-up limits. */
    public EffectRuntime(RuntimeLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Creates one seeded instance with a closed ordered set of accepted anchor names. */
    public synchronized EffectInstance create(
            EffectDefinition definition, long seed, List<String> anchorNames) {
        requireOpen();
        Objects.requireNonNull(definition, "definition")
                .validate(EffectsLimits.developmentDefaults());
        Objects.requireNonNull(anchorNames, "anchorNames");
        LinkedHashSet<String> acceptedAnchors = new LinkedHashSet<>();
        for (String anchorName : anchorNames) {
            validateName(anchorName);
            if (!acceptedAnchors.add(anchorName)) {
                throw new IllegalArgumentException("anchor names must be unique");
            }
        }
        if (acceptedAnchors.size() > limits.maxAnchorsPerInstance()
                || instances.size() >= limits.maxInstances()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "effect runtime capacity exceeded");
        }
        BasicEffectInstance instance = new BasicEffectInstance(
                definition.name(), seed, List.copyOf(acceptedAnchors), limits, this::release);
        instances.add(instance);
        return instance;
    }

    /** Closes all remaining instances and rejects future creation. */
    @Override public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (BasicEffectInstance instance : List.copyOf(instances)) {
            instance.close();
        }
    }

    private synchronized void release(BasicEffectInstance instance) {
        instances.remove(instance);
    }

    private void requireOpen() {
        if (closed) {
            throw lifecycleFailure();
        }
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "anchorName");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("invalid anchor name");
        }
    }

    private static EffectsException lifecycleFailure() {
        return new EffectsException(EffectsException.Kind.INVALID_LIFECYCLE,
                "effect runtime or instance is closed");
    }

    private static final class BasicEffectInstance implements EffectInstance {
        private final String definitionName;
        private final long seed;
        private final List<String> acceptedAnchorNames;
        private final RuntimeLimits limits;
        private final java.util.function.Consumer<BasicEffectInstance> closeAction;
        private final Map<String, EffectAnchor> anchors = new LinkedHashMap<>();
        private final ArrayDeque<EffectEvent> pendingEvents = new ArrayDeque<>();
        private List<EffectEvent> latestEvents = List.of();
        private double accumulatedSeconds;
        private long stepIndex;
        private boolean closed;

        BasicEffectInstance(String definitionName, long seed, List<String> acceptedAnchorNames,
                RuntimeLimits limits,
                java.util.function.Consumer<BasicEffectInstance> closeAction) {
            this.definitionName = definitionName;
            this.seed = seed;
            this.acceptedAnchorNames = List.copyOf(acceptedAnchorNames);
            this.limits = limits;
            this.closeAction = closeAction;
        }

        @Override public synchronized void setAnchor(EffectAnchor anchor) {
            requireOpen();
            Objects.requireNonNull(anchor, "anchor");
            if (!acceptedAnchorNames.contains(anchor.name())) {
                throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                        "anchor name is not declared for this instance");
            }
            anchors.put(anchor.name(), anchor);
        }

        @Override public synchronized void submit(EffectEvent event) {
            requireOpen();
            Objects.requireNonNull(event, "event");
            if (pendingEvents.size() >= limits.maxQueuedEvents()
                    || pendingEvents.size() >= limits.maxEventsPerSnapshot()) {
                throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                        "effect event capacity exceeded");
            }
            pendingEvents.addLast(event);
        }

        @Override public synchronized void advance(float deltaSeconds) {
            requireOpen();
            if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
                throw new EffectsException(EffectsException.Kind.INVALID_EFFECT,
                        "deltaSeconds must be finite and non-negative");
            }
            double prospective = accumulatedSeconds + deltaSeconds;
            int steps = (int) Math.floor(
                    prospective / limits.fixedStepSeconds() + 1.0e-7);
            if (steps > limits.maxCatchUpSteps()) {
                throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                        "fixed-step catch-up exceeds runtime limits");
            }
            accumulatedSeconds = prospective;
            for (int index = 0; index < steps; index++) {
                latestEvents = index == 0 ? drainEvents() : List.of();
                accumulatedSeconds -= limits.fixedStepSeconds();
                stepIndex++;
            }
        }

        @Override public synchronized EffectSnapshot snapshot() {
            requireOpen();
            List<EffectSnapshot.Anchor> anchorSnapshots = new ArrayList<>();
            for (String name : acceptedAnchorNames) {
                EffectAnchor anchor = anchors.get(name);
                if (anchor != null) {
                    anchorSnapshots.add(new EffectSnapshot.Anchor(
                            anchor.name(), anchor.x(), anchor.y(), anchor.z()));
                }
            }
            List<EffectSnapshot.Event> eventSnapshots = latestEvents.stream()
                    .map(event -> new EffectSnapshot.Event(
                            event.name(), event.sequence(), event.value()))
                    .toList();
            return new EffectSnapshot(definitionName, seed, stepIndex,
                    stepIndex * limits.fixedStepSeconds(), anchorSnapshots, eventSnapshots);
        }

        @Override public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            pendingEvents.clear();
            anchors.clear();
            latestEvents = List.of();
            closeAction.accept(this);
        }

        private List<EffectEvent> drainEvents() {
            List<EffectEvent> drained = List.copyOf(pendingEvents);
            pendingEvents.clear();
            return drained;
        }

        private void requireOpen() {
            if (closed) {
                throw lifecycleFailure();
            }
        }
    }
}
