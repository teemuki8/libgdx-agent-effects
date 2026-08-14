package io.github.teemuki8.libgdx.agent.effects.runtime;

/** Repository-owned 64-bit linear-congruential generator with a stable sequence contract. */
final class StableRandom {
    private long state;

    StableRandom(long seed) {
        state = seed;
    }

    float nextFloat() {
        state = state * 6364136223846793005L + 1442695040888963407L;
        return (state >>> 40) * (1f / (1 << 24));
    }
}
