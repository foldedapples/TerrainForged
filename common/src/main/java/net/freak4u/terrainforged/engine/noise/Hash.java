package net.freak4u.terrainforged.engine.noise;

/**
 * Deterministic, allocation-free integer hashing used by every noise and placement routine.
 * All outputs depend only on the inputs, never on call order or thread.
 */
public final class Hash {
    private Hash() {}

    /** 64-bit finaliser (SplitMix64). */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long seed(long base, long salt) {
        return mix(base ^ mix(salt + 0x9E3779B97F4A7C15L));
    }

    public static int hash2(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        return (int) (mix(h) >>> 32);
    }

    /** Uniform double in [0, 1). */
    public static double unit(long seed, int x, int z) {
        return (hash2(seed, x, z) >>> 8) * 0x1.0p-24;
    }

    /** Uniform double in [-1, 1). */
    public static double signed(long seed, int x, int z) {
        return unit(seed, x, z) * 2.0 - 1.0;
    }
}
