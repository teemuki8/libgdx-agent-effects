package io.github.teemuki8.libgdx.agent.effects.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable bounded acyclic post-process pass graph. */
public record PostProcessGraphDefinition(String name, List<String> externalInputs,
        List<RenderPassDefinition> passes, String output, int framebufferPoolLimit) {
    private static final int HARD_PASS_CAP = 4096;

    public PostProcessGraphDefinition {
        Objects.requireNonNull(name, "name");
        externalInputs = List.copyOf(Objects.requireNonNull(externalInputs, "externalInputs"));
        passes = List.copyOf(Objects.requireNonNull(passes, "passes"));
        Objects.requireNonNull(output, "output");
        if (name.isBlank() || output.isBlank() || passes.isEmpty()
                || passes.size() > HARD_PASS_CAP) {
            throw new IllegalArgumentException("post-process graph identity or size is invalid");
        }
        requireUniqueNames(externalInputs, "external inputs");
        if (framebufferPoolLimit <= 0 || framebufferPoolLimit > HARD_PASS_CAP
                || framebufferPoolLimit < passes.size()) {
            throw new IllegalArgumentException("framebuffer pool cannot hold declared passes");
        }
        validateGraph(externalInputs, passes, output);
    }

    /** Validates the graph and every material against configured limits. */
    public PostProcessGraphDefinition validate(EffectsLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (passes.size() > limits.maxPassCount()) {
            throw new EffectsException(EffectsException.Kind.LIMIT_EXCEEDED,
                    "post-process pass count exceeds configured limit");
        }
        for (RenderPassDefinition pass : passes) {
            pass.material().validate(limits);
        }
        return this;
    }

    /** Returns passes in stable declared-order topological order. */
    public List<RenderPassDefinition> executionOrder() {
        Map<String, RenderPassDefinition> producers = producers(passes);
        List<RenderPassDefinition> remaining = new ArrayList<>(passes);
        List<RenderPassDefinition> result = new ArrayList<>(passes.size());
        Set<String> available = new HashSet<>(externalInputs);
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (int index = 0; index < remaining.size();) {
                RenderPassDefinition pass = remaining.get(index);
                if (available.containsAll(pass.inputs())) {
                    result.add(pass);
                    available.add(pass.output());
                    remaining.remove(index);
                    progressed = true;
                } else {
                    index++;
                }
            }
            if (!progressed) {
                throw new IllegalArgumentException("post-process graph contains a cycle");
            }
        }
        if (!producers.containsKey(output)) {
            throw new IllegalArgumentException("final output must be produced by a pass");
        }
        return List.copyOf(result);
    }

    private static void validateGraph(List<String> externalInputs,
            List<RenderPassDefinition> passes, String output) {
        Map<String, RenderPassDefinition> producers = producers(passes);
        Set<String> known = new HashSet<>(externalInputs);
        known.addAll(producers.keySet());
        Set<String> passNames = new HashSet<>();
        for (RenderPassDefinition pass : passes) {
            if (!passNames.add(pass.name())) {
                throw new IllegalArgumentException("render pass names must be unique");
            }
            for (String input : pass.inputs()) {
                if (!known.contains(input)) {
                    throw new IllegalArgumentException("unknown render pass input: " + input);
                }
            }
        }
        if (!producers.containsKey(output)) {
            throw new IllegalArgumentException("final output must be produced by a pass");
        }
        new PostProcessGraphDefinitionOrder(externalInputs, passes).verify();
    }

    private static Map<String, RenderPassDefinition> producers(List<RenderPassDefinition> passes) {
        Map<String, RenderPassDefinition> result = new HashMap<>();
        for (RenderPassDefinition pass : passes) {
            if (result.put(pass.output(), pass) != null) {
                throw new IllegalArgumentException("render pass outputs must be unique");
            }
        }
        return result;
    }

    private static void requireUniqueNames(List<String> names, String description) {
        Set<String> unique = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank() || !unique.add(name)) {
                throw new IllegalArgumentException(description + " must be unique names");
            }
        }
    }

    private record PostProcessGraphDefinitionOrder(List<String> externalInputs,
            List<RenderPassDefinition> passes) {
        void verify() {
            List<RenderPassDefinition> remaining = new ArrayList<>(passes);
            Set<String> available = new HashSet<>(externalInputs);
            while (!remaining.isEmpty()) {
                int before = remaining.size();
                remaining.removeIf(pass -> {
                    if (available.containsAll(pass.inputs())) {
                        available.add(pass.output());
                        return true;
                    }
                    return false;
                });
                if (remaining.size() == before) {
                    throw new IllegalArgumentException("post-process graph contains a cycle");
                }
            }
        }
    }
}
